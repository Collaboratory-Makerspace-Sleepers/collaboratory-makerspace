#!/usr/bin/env python3
"""Boot service: sole reader/GPIO owner. Setup/status UI is an optional client."""
import fcntl
import json
import os
import queue
import signal
import socket
import threading
import time
import uuid
from api import heartbeat, ApiAuthError
from connection import credentials_match
from discovery import discover, remembered_match
from door import Door
from engine import Worker
from event_queue import EventQueue, binding
from state import DATA, CONFIG, KEY, CONTROL, STATUS, DEFAULTS, read, write_private


def notify(text):
    address=os.environ.get('NOTIFY_SOCKET')
    if not address:return
    if address.startswith('@'):address='\0'+address[1:]
    with socket.socket(socket.AF_UNIX,socket.SOCK_DGRAM) as sock:
        sock.connect(address);sock.sendall(text.encode())


class ScannerService:
    def __init__(self,data=DATA,status=STATUS,worker_factory=Worker,door_factory=Door):
        self.data=data;self.status_path=status
        self.config_path=data/'config.json';self.key_path=data/'credentials.json';self.control_path=data/'control.json'
        self.cfg=dict(DEFAULTS);self.key={};self.epoch=0;self.mode='scan';self.uid_until=None
        self.stop=threading.Event();self.events=queue.Queue();self.outbox=EventQueue(data/'events.sqlite3')
        self.error='';self.reader_status='Waiting for configuration';self.connection='UNPAIRED';self.remote={}
        self.last_notice='';self.next_heartbeat=0;self.next_upload=0;self.last_status=0;self.sequence=0
        self.recent=[];self.last_message=None;self.known_control=read(self.control_path).get('id')
        self.door=door_factory(notify=self.audit)
        self.worker=worker_factory(self.events,self.door,self.audit)
        self.signature=None;self.valid=False
    def audit(self,kind,detail='',scope=None,**fields):
        try:self.outbox.add(scope or binding(self.cfg,self.key),kind,detail,**fields)
        except Exception:
            self.error='Local event logging failed. Check free space and permissions.'
            print(self.error,flush=True)
    def configure(self):
        cfg={**DEFAULTS,**read(self.config_path)};key=read(self.key_path)
        if key and not credentials_match(cfg,key):key={}
        signature=json.dumps([cfg,key],sort_keys=True)
        if signature!=self.signature:
            # Invalidate prior in-flight decisions before changing saved identity/settings.
            self.epoch+=1;self.worker.configure(self.epoch,'paused',cfg,{})
            self.door.configure({},{});self.cfg=cfg;self.key=key;self.signature=signature
            self.remote={};self.next_heartbeat=0;self.next_upload=0
            self.connection='CONNECTING' if key else 'UNPAIRED'
            self.audit('CONFIG_CHANGED','Saved connection or settings loaded')
            self.worker.configure(self.epoch,self.mode if key else 'paused',cfg,key)
        self.valid=True
        command=read(self.control_path)
        if command.get('id') and command.get('id')!=self.known_control:
            self.known_control=command['id'];mode=command.get('mode')
            if mode not in ('scan','uid','paused'):return
            self.mode=mode;self.uid_until=time.monotonic()+120 if mode=='uid' else None
            self.epoch+=1;self.worker.configure(self.epoch,mode if self.key or mode=='uid' else 'paused',self.cfg,self.key)
            self.audit('SCAN_MODE_CHANGED',mode)
        if self.uid_until and time.monotonic()>=self.uid_until:
            self.mode='scan';self.uid_until=None;self.epoch+=1
            self.worker.configure(self.epoch,'scan' if self.key else 'paused',self.cfg,self.key)
            self.audit('SCAN_MODE_CHANGED','UID capture ended; normal scanning restored')
    def network_tick(self):
        now=time.monotonic()
        if not self.valid or not self.key or now<self.next_heartbeat:return
        try:
            remote=heartbeat(self.cfg['origin'],self.key,socket.gethostname(),self.reader_status,self.cfg.get('tls_fingerprint') or None)
            if self.connection!='CONNECTED':self.audit('CONNECTION_RESTORED','Backend connection available')
            if self.remote.get('enabled')!=remote.get('enabled'):self.audit('DEVICE_STATE','Enabled' if remote.get('enabled') else 'Disabled by admin')
            self.connection='CONNECTED';self.remote=remote;self.next_heartbeat=now+15
            try:self.door.configure(self.cfg,remote)
            except Exception:
                self.error='Door driver setup failed; leave output off and inspect hardware.'
                self.audit('DOOR_ERROR',self.error,error=True)
        except ApiAuthError:
            if self.connection!='PAIRING_REQUIRED':self.audit('AUTH_REJECTED','Saved registration rejected; pair this Pi again',error=True)
            self.connection='PAIRING_REQUIRED';self.next_heartbeat=now+15
        except Exception:
            if self.connection!='OFFLINE':self.audit('CONNECTION_LOST','Backend unavailable; no offline approval',error=True)
            self.connection='OFFLINE';self.next_heartbeat=now+5
            if self.cfg.get('connection_mode')!='hosted' and self.cfg.get('server_id'):
                previous=dict(self.cfg)
                try:
                    target=remembered_match(discover(),previous)
                    if target and target['origin']!=previous['origin']:
                        heartbeat(target['origin'],self.key,socket.gethostname(),self.reader_status,target['fingerprint'])
                        # Do not overwrite a simultaneous setup-window edit.
                        if {**DEFAULTS,**read(self.config_path)}==previous:
                            write_private(self.config_path,json.dumps({**previous,'origin':target['origin']}))
                except Exception:pass
    def upload_tick(self):
        if not self.valid or not self.key or time.monotonic()<self.next_upload:return
        try:
            sent=self.outbox.flush(self.cfg,self.key)
            self.next_upload=time.monotonic()+(1 if sent else 5)
        except Exception:
            self.next_upload=time.monotonic()+10 # Logging cannot submit/replay a scan.
    def drain(self):
        while True:
            try:epoch,kind,value=self.events.get_nowait()
            except queue.Empty:return
            if epoch!=self.epoch:continue
            self.sequence+=1
            self.last_message={'sequence':self.sequence,'kind':kind,'value':value,'at':time.time()}
            if kind=='notice':
                self.reader_status=str(value)[:160]
                if value!=self.last_notice:
                    self.audit('READER_STATUS',value,error='error' in str(value).lower() or 'failed' in str(value).lower())
                    self.last_notice=value
            elif kind in ('reading','uid'):
                if self.reader_status!='Reader connected':self.audit('READER_READY','Reader detected a card')
                self.reader_status='Reader connected';self.last_notice=''
            elif kind=='decision':
                self.recent.append(self.last_message);self.recent=self.recent[-20:]
    def write_status(self):
        if time.monotonic()-self.last_status<.5:return
        self.last_status=time.monotonic()
        # RuntimeDirectory is private and volatile; member display is not a local member database.
        write_private(self.status_path,json.dumps({'updatedAt':time.time(),'pid':os.getpid(),
            'connection':self.connection,'mode':self.worker.snapshot()[1],'commandId':self.known_control,
            'reader':self.reader_status,'device':{k:self.remote.get(k) for k in ('name','area','enabled','doorEnabled')},
            'pendingEvents':self.outbox.pending(binding(self.cfg,self.key)),
            'error':self.error,'lastMessage':self.last_message,'recent':self.recent}))
    def run(self):
        self.configure();self.audit('SERVICE_STARTED','Headless scanner service started');self.worker.start()
        notify('READY=1')
        try:
            while not self.stop.is_set():
                try:self.configure()
                except (ValueError,OSError):
                    # A broken config never keeps scanning using a stale identity.
                    self.epoch+=1;self.worker.configure(self.epoch,'paused',self.cfg,{})
                    self.signature=None;self.valid=False;self.error='Cannot read configuration; fix it in setup.'
                self.drain();self.write_status()
                if not self.worker.is_alive() or time.monotonic()-self.worker.progress>35:
                    raise RuntimeError('Reader worker stalled; service restart required')
                notify('WATCHDOG=1')
                self.network_tick();self.upload_tick();self.stop.wait(.2)
        finally:
            self.epoch+=1;self.worker.configure(self.epoch,'paused',self.cfg,{})
            self.worker.stop_event.set();self.door.close()
            self.audit('SERVICE_STOPPED','Service stopped; no new scans')
            self.worker.join(2);self.status_path.unlink(missing_ok=True);self.outbox.close()


def main():
    os.umask(0o077);DATA.mkdir(parents=True,exist_ok=True,mode=0o700)
    with open(DATA/'scanner.lock','w') as lock:
        try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise SystemExit('Another scanner owns the reader. Close the old scanner program.')
        service=ScannerService()
        for sig in (signal.SIGINT,signal.SIGTERM):signal.signal(sig,lambda *_:service.stop.set())
        service.run()
if __name__=='__main__':main()
