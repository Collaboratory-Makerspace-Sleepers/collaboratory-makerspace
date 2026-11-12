#!/usr/bin/env python3
"""Live building check-in on the Pi. Backend owns every access decision."""
import json
import os
import queue
import threading
import time
import socket
import subprocess
from pathlib import Path
import tkinter as tk
from tkinter import ttk, messagebox
from tkinter.scrolledtext import ScrolledText
from api import DEFAULT_ORIGIN, checkin, origin, pair, heartbeat, ApiError, ApiAuthError
from discovery import discover, probe, trust_code, remembered_match
from reader import Hardware
from door import Door, ALLOWED_PINS
from connection import hosted_config, credentials_match
from api import protocol

DATA = Path.home()/'.config'/'collaboratory-scanner-v2'
CONFIG = DATA/'config.json'
KEY = DATA/'credentials.json'
DEFAULTS = dict(origin=DEFAULT_ORIGIN, connection_mode='local', reader_match='ACR122', led_enabled=True,
                buzzer_enabled=False, denial_beep_enabled=True, auto_start=True, tls_fingerprint="", server_id="", laptop_name="", door_gpio_enabled=False, door_gpio_pin=17, door_active_high=True)

# The background service owns reader and door access, even with this window closed.
from service_client import ServiceWorker, NoHardware, messages_since, status as service_status
from state import write_private

class App:
    def __init__(self, root):
        self.root, self.epoch, self.uid = root, 0, ''
        self.result_seq=0
        self.cfg = dict(DEFAULTS)
        if CONFIG.exists(): self.cfg.update(json.loads(CONFIG.read_text()))
        self.key = json.loads(KEY.read_text()) if KEY.exists() else {}
        if self.key and not credentials_match(self.cfg,self.key):
            self.key = {}
        self.door = NoHardware()
        self.reader_status = 'Scanning paused'
        self.recover_after_auth = False
        self.hb_busy = False
        self.reconnect_busy = False
        self.connection_was_offline = False
        root.title('Collaboratory • Scanner setup and status')
        root.geometry('900x680'); root.minsize(720, 600)
        ttk.Style().configure('.', font=('DejaVu Sans', 11))
        frame = ttk.Frame(root, padding=20); frame.pack(fill='both', expand=True)
        ttk.Label(frame, text='THE COLLABORATORY', font=('DejaVu Sans', 19, 'bold')).pack(anchor='w')
        ttk.Label(frame, text='Background scanner service • closing this window does not stop scanning').pack(anchor='w', pady=(0,15))
        self.banner = tk.Label(frame, text='SCANNING PAUSED', bg='#742d36', fg='white', font=('DejaVu Sans', 25, 'bold'), pady=20)
        self.banner.pack(fill='x')
        self.name = tk.StringVar(); self.reason = tk.StringVar(value='Choose Connect to get started. After pairing, the scanner reconnects automatically.')
        self.uid_label = tk.StringVar(value='Card UID: —'); self.notice = tk.StringVar()
        self.device_status = tk.StringVar(value='Not paired. Choose Connect.')
        ttk.Label(frame,textvariable=self.device_status,wraplength=800).pack(pady=6)
        ttk.Label(frame, textvariable=self.name, font=('DejaVu Sans', 20, 'bold')).pack(pady=(15,5))
        ttk.Label(frame, textvariable=self.reason, wraplength=700, justify='center').pack()
        ttk.Label(frame, textvariable=self.uid_label).pack(pady=10)
        buttons = ttk.Frame(frame); buttons.pack(pady=8)
        for title, action in [('Connect', self.connect_dialog), ('Resume scans', lambda:self.start('scan')), ('Pause', self.pause), ('Help & settings', self.advanced)]:
            ttk.Button(buttons, text=title, command=action).pack(side='left', padx=4)
        ttk.Label(frame, textvariable=self.notice, wraplength=800).pack(pady=8)
        ttk.Label(frame, text='Recent scans (service memory; full history is in admin)').pack(anchor='w')
        self.log = ScrolledText(frame, height=6, state='disabled', font=('DejaVu Sans Mono', 10)); self.log.pack(fill='both', expand=True)
        self.events = queue.Queue(); self.worker = ServiceWorker(); self.service_sequence=0; self.service_pid=None
        root.protocol('WM_DELETE_WINDOW', self.close); root.after(100, self.drain)

        if not self.key: root.after(1000,self.connect_dialog)


    def start(self, mode):
        try:
            self.cfg['origin'] = origin(self.cfg['origin'])
            # Reload credentials provisioned from the computer while this UI was open.
            if KEY.exists(): self.key = json.loads(KEY.read_text())
            if self.key and not credentials_match(self.cfg,self.key):
                self.key={}
            if mode == 'scan' and not self.key.get('deviceKey'): raise ValueError('Pair this Pi with makerspace first.')
        except ValueError as exc:
            messagebox.showerror('Connection setup needed', str(exc)); return
        self.epoch += 1; self.name.set(''); self.notice.set('')
        self.reader_status='Waiting for reader/card'
        self.worker.configure(self.epoch, mode, self.cfg, self.key)
        self.banner.config(text='WAITING FOR CARD', bg='#742d36')
        self.reason.set('Tap one card. Remove it before tapping again.' if mode == 'scan' else 'UID capture only. No check-in request is sent.')

    def pause(self):
        self.recover_after_auth=False
        self.epoch += 1; self.worker.configure(self.epoch, 'paused', self.cfg, '')
        self.banner.config(text='PAUSE REQUESTED', bg='#742d36'); self.name.set(''); self.reason.set('Select a scan mode to continue.')
        self.reader_status='Scanning paused'

    def copy_uid(self):
        if self.uid:
            self.root.clipboard_clear(); self.root.clipboard_append(self.uid)
            self.notice.set('UID copied. Link it to an account in the backend; this does not write data to the card.')

    def advanced(self):
        win=tk.Toplevel(self.root);win.title('Help & settings');frame=ttk.Frame(win,padding=20);frame.pack()
        def check():
            from diagnostics import report
            self.background(report,lambda ok,value:messagebox.showinfo('Scanner check',str(value),parent=win) if win.winfo_exists() else None)
        for title,action in [('Check reader and connection',check),('Reader and LED settings',self.settings),('Optional door output',self.door_settings),('Read UID only (2 minutes)',lambda:self.start('uid')),('Copy last UID',self.copy_uid)]:
            ttk.Button(frame,text=title,command=action).pack(fill='x',pady=5)
        ttk.Label(frame,text='For repair: open the downloaded scanner folder in a terminal and run\nbash Start-Scanner.sh --repair\n\nCreate users and assign cards from the Makerspace admin page.',wraplength=520).pack(pady=12)
        ttk.Button(frame,text='Close',command=win.destroy).pack()

    def background(self,action,done):
        results=queue.Queue()
        def run():
            try: results.put((True,action()))
            except Exception as exc: results.put((False,exc))
        threading.Thread(target=run,daemon=True).start()
        def poll():
            try: ok,value=results.get_nowait()
            except queue.Empty: self.root.after(100,poll); return
            done(ok,value)
        self.root.after(100,poll)

    def send_heartbeat(self):
        if self.key and not self.hb_busy:
            self.hb_busy=True
            credentials=dict(self.key); connection=dict(self.cfg)
            def done(ok,value):
                self.hb_busy=False
                if credentials!=self.key or connection['origin']!=self.cfg['origin']: return
                if ok:
                    was_offline=self.connection_was_offline;self.connection_was_offline=False
                    if was_offline and self.worker.snapshot()[1]=='scan':
                        self.banner.config(text='WAITING FOR CARD',bg='#742d36');self.reason.set('Connection restored. Remove the card and tap again.')
                    if self.recover_after_auth and self.cfg.get('auto_start'):
                        self.recover_after_auth=False;self.start('scan')
                    try: self.door.configure(self.cfg,value)
                    except Exception as exc: self.notice.set('Door setup error: '+str(exc))
                    state='ENABLED' if value.get('enabled') else 'DISABLED BY ADMIN'
                    self.device_status.set(f"CONNECTED • {value.get('name','Pi')} • {value.get('area','')} • {value.get('equipmentName') or 'General entry'} • {state}")
                elif isinstance(value,ApiAuthError):
                    self.registration_lost()
                else:
                    self.connection_was_offline=True
                    self.device_status.set('OFFLINE — reconnecting automatically. Use manual check-in.')
                    self.reconnect()
            self.background(lambda:heartbeat(connection['origin'],credentials,socket.gethostname(),self.reader_status,connection.get('tls_fingerprint') or None),done)
        self.root.after(5000 if self.connection_was_offline else 15000,self.send_heartbeat)

    def registration_lost(self):
        self.pause(); self.recover_after_auth=True
        self.device_status.set('PAIRING NOT ACCEPTED. Saved credentials kept. Check the computer app or ask an admin for a new pairing code.')
        self.reason.set('This Pi cannot grant access. Use manual check-in until paired again.')

    def reconnect(self):
        if self.cfg.get('connection_mode')=='hosted': return
        if self.reconnect_busy or not self.cfg.get('server_id') or not self.cfg.get('tls_fingerprint'): return
        self.reconnect_busy=True
        previous=dict(self.cfg); credentials=dict(self.key)
        def attempt():
            target=remembered_match(discover(),previous)
            if not target: raise ApiError('Saved computer not found on this network.')
            try: heartbeat(target['origin'],credentials,socket.gethostname(),self.reader_status,target['fingerprint'])
            except ApiAuthError: target['registration_removed']=True
            return target
        def done(ok,value):
            self.reconnect_busy=False
            if not ok or self.cfg!=previous or self.key!=credentials: return
            updated={**self.cfg,'origin':value['origin']}
            try: write_private(CONFIG,json.dumps(updated,indent=2)+'\n')
            except OSError:
                self.notice.set('Could not save the new computer address. Check storage space.');return
            self.cfg=updated
            if value.get('registration_removed'):
                self.registration_lost();return
            mode=self.worker.snapshot()[1]
            self.epoch+=1;self.worker.configure(self.epoch,mode,self.cfg,self.key)
            self.device_status.set('Reconnected to '+self.cfg.get('laptop_name','saved computer'))
            self.notice.set('Computer address updated automatically. Pairing is unchanged.')
        self.background(attempt,done)

    def connect_dialog(self):
        win=tk.Toplevel(self.root);win.title('Connect scanner');win.transient(self.root);win.grab_set()
        frame=ttk.Frame(win,padding=20);frame.pack(fill='both',expand=True)
        ttk.Label(frame,text='Where is Makerspace running?',font=('DejaVu Sans',16,'bold')).pack(pady=10)
        def local():win.destroy();self.network()
        ttk.Button(frame,text='Find computer — local testing',command=local).pack(fill='x',pady=8)
        ttk.Label(frame,text='Hosted backend — use the address provided by your team',wraplength=560).pack(pady=(16,4))
        address=tk.StringVar(value=self.cfg['origin'] if self.cfg.get('connection_mode')=='hosted' else '')
        ttk.Entry(frame,textvariable=address,width=56).pack(pady=4)
        status=tk.StringVar();ttk.Label(frame,textvariable=status,wraplength=560).pack(pady=8)
        def hosted():
            try: config=hosted_config(self.cfg,address.get())
            except ValueError as exc:status.set(str(exc));return
            button.config(state='disabled');status.set('Checking secure backend connection…')
            def done(ok,value):
                if not win.winfo_exists():return
                button.config(state='normal')
                if not ok:status.set(str(value));return
                if not messagebox.askyesno('Confirm backend',f"Use {config['origin']}?\nCheck that this is the address from your makerspace team.",parent=win):return
                self.pause();same=credentials_match(config,self.key)
                try:
                    write_private(CONFIG,json.dumps(config,indent=2)+'\n')
                    if not same:KEY.unlink(missing_ok=True);self.key={}
                except OSError as exc:status.set('Could not save connection: '+str(exc));return
                self.cfg=config;win.destroy()
                if self.key:self.start('scan')
                else:self.pairing()
            # Standard CA/hostname verification; no pinning to short-lived AWS certificates.
            self.background(lambda:protocol(config['origin']),done)
        button=ttk.Button(frame,text='Connect to hosted backend',command=hosted);button.pack(fill='x',pady=8)
        if self.key:
            def replace():win.destroy();self.pairing()
            ttk.Button(frame,text='Enter a new pairing code for the saved connection',command=replace).pack(pady=8)
        ttk.Button(frame,text='Close',command=win.destroy).pack(pady=8)

    def pairing(self):
        previous_mode=self.worker.snapshot()[1]
        self.pause()
        win=tk.Toplevel(self.root);win.title('Pair this Pi');win.transient(self.root);win.grab_set()
        def dismiss():
            win.destroy()
            if previous_mode!='paused' and self.key:self.start(previous_mode)
        win.protocol('WM_DELETE_WINDOW',dismiss)
        frame=ttk.Frame(win,padding=20);frame.pack()
        ttk.Label(frame,text='On the computer: Admin → Hardware Access → Pis & areas → Add a Pi.\nEnter its eight-digit pairing code below (not the website login code).',wraplength=560).pack(pady=10)
        code=tk.StringVar();entry=ttk.Entry(frame,textvariable=code,width=40);entry.pack(pady=8);entry.focus_set()
        status=tk.StringVar();ttk.Label(frame,textvariable=status,wraplength=560).pack(pady=8)
        def submit():
            import re
            value=re.sub(r'[\s-]','',code.get())
            if not re.fullmatch('(?:[0-9]{8}|[0-9a-fA-F]{32})',value):status.set('Enter the eight-digit pairing code from Pis & areas.');return
            button.config(state='disabled');status.set('Pairing…')
            def done(ok,data):
                if not win.winfo_exists():return
                button.config(state='normal')
                if not ok:status.set(data);return
                credentials={'id':data['id'],'deviceKey':data['deviceKey'],'serverId':self.cfg.get('server_id',''),'fingerprint':self.cfg.get('tls_fingerprint',''),'backendOrigin':self.cfg['origin']}
                try:write_private(KEY,json.dumps(credentials)+'\n')
                except OSError:status.set('Could not save credentials. Generate a new pairing code and try again.');return
                self.key=credentials;win.destroy()
                self.device_status.set(f"Paired: {data['name']} • {data['area']}")
                self.notice.set('Pairing saved. Reconnection is automatic. The admin controls the area and permissions.')
                self.start('scan')
            self.background(lambda:pair(self.cfg['origin'],value,socket.gethostname(),self.cfg.get('tls_fingerprint') or None),done)
        button=ttk.Button(frame,text='Pair this Pi',command=submit);button.pack(pady=8)

    def settings(self):
        win=tk.Toplevel(self.root);win.title('Connection settings');win.transient(self.root);win.grab_set()
        frame=ttk.Frame(win,padding=18);frame.pack()
        base=tk.StringVar(value=self.cfg['origin']);match=tk.StringVar(value=self.cfg['reader_match'])
        ttk.Label(frame,text='Advanced: backend origin (normally set by Find computer)').pack(anchor='w')
        ttk.Entry(frame,textvariable=base,width=55).pack(pady=6)
        ttk.Label(frame,text='Reader name contains').pack(anchor='w')
        ttk.Entry(frame,textvariable=match,width=55).pack(pady=6)
        beep=tk.BooleanVar(value=self.cfg['denial_beep_enabled'])
        ttk.Checkbutton(frame,text='Beep on denied scans',variable=beep).pack(pady=6)
        ttk.Label(frame,text='The admin assigns the Pi name, area and machine.\nYou do not enter a shared device key.',wraplength=540).pack(pady=8)
        ttk.Label(frame,text='Scanning starts at boot after pairing, without opening this window.').pack(pady=6)
        def save():
            try:
                value=origin(base.get())
                if value!=self.cfg['origin']:
                    raise ValueError('Use Connect to change the backend connection.')
                if not match.get().strip():raise ValueError('Enter a reader name, normally ACR122.')
                self.cfg.update(origin=value,reader_match=match.get().strip(),denial_beep_enabled=beep.get(),auto_start=True)
                write_private(CONFIG,json.dumps(self.cfg,indent=2)+'\n');win.destroy()
                if self.key and self.cfg.get('auto_start'):self.start('scan')
            except (ValueError,OSError) as exc:messagebox.showerror('Settings',str(exc),parent=win)
        ttk.Button(frame,text='Save',command=save).pack(pady=8)

    def network(self):
        previous_mode=self.worker.snapshot()[1]
        self.pause()
        win=tk.Toplevel(self.root);win.title('Find computer');win.transient(self.root);win.grab_set()
        def dismiss():
            win.destroy()
            if previous_mode!='paused' and self.key:self.start(previous_mode)
        win.protocol('WM_DELETE_WINDOW',dismiss)
        frame=ttk.Frame(win,padding=20);frame.pack(fill='both',expand=True)
        ttk.Label(frame,text='Connect to your makerspace computer',font=('DejaVu Sans',16,'bold')).pack(anchor='w')
        ttk.Label(frame,text='Start Makerspace on the computer. Connect both devices to the same Wi-Fi or hotspot.\nSelect the computer below; no Pi password or SSH window is needed.',wraplength=660).pack(pady=10)
        listing=tk.Listbox(frame,width=74,height=5,exportselection=False);listing.pack(fill='x')
        records=[]
        status=tk.StringVar(value='Searching…');ttk.Label(frame,textvariable=status,wraplength=660).pack(pady=10)
        verify=tk.StringVar(value='Select a computer to see its verification code.')
        ttk.Label(frame,textvariable=verify,wraplength=660,font=('DejaVu Sans',12,'bold')).pack(pady=8)
        trusted=tk.BooleanVar(value=False)
        ttk.Checkbutton(frame,text='This verification code matches the computer’s Pis & areas page',variable=trusted).pack(anchor='w')
        def selected(_=None):
            trusted.set(False)
            if listing.curselection():
                d=records[listing.curselection()[0]]
                verify.set('Computer verification code: '+trust_code(d['fingerprint']))
                if d['serverId']==self.cfg.get('server_id') and d['fingerprint']==self.cfg.get('tls_fingerprint'):
                    trusted.set(True)
        listing.bind('<<ListboxSelect>>',selected)
        def populate(ok,value):
            if not win.winfo_exists():return
            search.config(state='normal');manual.config(state='normal')
            if not ok:status.set(str(value));return
            records[:]=value;listing.delete(0,'end');trusted.set(False)
            for d in records:listing.insert('end',d['name']+' — '+d['origin'])
            status.set('Select your computer.' if records else 'No computer found. Check that the computer launcher is running and Windows marks this trusted network Private. If discovery is blocked, enter the computer IPv4 address below.')
            if len(records)==1:listing.selection_set(0);selected()
        def find():
            search.config(state='disabled');manual.config(state='disabled');status.set('Searching the local network…')
            self.background(discover,populate)
        search=ttk.Button(frame,text='Search again',command=find);search.pack(pady=6)
        ttk.Label(frame,text='If the computer is not listed: enter its IPv4 address').pack(anchor='w',pady=(12,0))
        address=tk.StringVar();ttk.Entry(frame,textvariable=address,width=30).pack(anchor='w',pady=4)
        def lookup():
            search.config(state='disabled');manual.config(state='disabled');status.set('Checking address…')
            self.background(lambda:[probe(address.get())],populate)
        manual=ttk.Button(frame,text='Find at this address',command=lookup);manual.pack(anchor='w')
        def connect():
            if not listing.curselection():status.set('Select a computer first.');return
            if not trusted.get():status.set('Compare the verification code with the computer admin page, then tick the box.');return
            target=records[listing.curselection()[0]]
            same=target['serverId']==self.cfg.get('server_id') and target['fingerprint']==self.cfg.get('tls_fingerprint')
            config={**self.cfg,'origin':target['origin'],'server_id':target['serverId'],'tls_fingerprint':target['fingerprint'],'laptop_name':target['name'],'connection_mode':'local'}
            try:
                if not same: KEY.unlink(missing_ok=True);self.key={}
                write_private(CONFIG,json.dumps(config,indent=2)+'\n')
            except OSError as exc:status.set('Could not save connection: '+str(exc));return
            self.cfg=config;win.destroy()
            if self.key:
                self.start('scan');self.notice.set('Saved computer selected. Reconnection is automatic.')
            else:self.pairing()
        ttk.Button(frame,text='Connect / pair',command=connect).pack(pady=14)
        ttk.Button(frame,text='Close',command=dismiss).pack()
        find()

    def drain(self):
        try:
            current=service_status()
            if CONFIG.exists():self.cfg.update(json.loads(CONFIG.read_text()))
            if KEY.exists():self.key=json.loads(KEY.read_text())
            device=current.get('device',{})
            self.device_status.set(f"{current['connection']} • {device.get('name') or 'Pi'} • {device.get('area') or ''} • {current['reader']} • {current['pendingEvents']} queued events")
            if current.get('error'):self.notice.set(current['error'])
            self.service_pid,self.service_sequence,messages=messages_since(current,self.service_pid,self.service_sequence)
            for m in messages:self.events.put((self.epoch,m['kind'],m['value']))
            if current['mode']=='paused':self.banner.config(text='SCANNING PAUSED',bg='#742d36')
            elif not current.get('lastMessage') or time.time()-current['lastMessage']['at']>6:
                self.name.set('');self.banner.config(text='WAITING FOR CARD',bg='#742d36')
                self.reason.set('Tap one card. Remove it before tapping again.' if current['mode']=='scan' else 'UID capture only. No access request is sent.')
            self.worker.desired=(self.epoch,current['mode'],self.cfg,self.key)
        except Exception as exc:self.device_status.set(str(exc))
        try:
            while True:
                epoch, kind, value = self.events.get_nowait()
                if epoch != self.epoch: continue
                if kind == 'notice':
                    self.notice.set(value); self.reader_status=str(value)[:160]
                elif kind in ('reading','uid'):
                    self.result_seq+=1; self.reader_status='Reader connected'; self.uid=value; self.uid_label.set('Card UID: '+value); self.name.set('')
                    self.banner.config(text='CHECKING CARD' if kind=='reading' else 'UID CAPTURED',bg='#344f6a')
                    self.reason.set('Waiting for the backend…' if kind=='reading' else 'Copy this UID to link the card to an account. No access decision made.')
                elif kind == 'decision':
                    uid, decision=value
                    self.uid=uid;self.uid_label.set('Card UID: '+uid)
                    label=('CARD ASSIGNED' if decision.get('assigned') else 'ASSIGNMENT STOPPED') if decision.get('enrollment') else 'MANUAL CHECK-IN' if decision['error'] else 'APPROVED' if decision['granted'] else 'DENIED'
                    self.banner.config(text=label,bg='#344f6a' if decision.get('enrollment') else '#17643d' if decision['granted'] else '#985600')
                    self.name.set(decision['userName']); self.reason.set(decision['reason'])
                    self.log.config(state='normal'); self.log.insert('end',f'{time.strftime("%H:%M:%S")}  {uid}  {label}  {decision["reason"]}\n')
                    # Bound the display; no member records or secrets are stored in this log.
                    if int(self.log.index('end-1c').split('.')[0])>100: self.log.delete('1.0','2.0')
                    self.log.see('end'); self.log.config(state='disabled')
                    decision_epoch=self.epoch;self.result_seq+=1;sequence=self.result_seq
                    def clear_result(e=decision_epoch,seq=sequence):
                        if e==self.epoch and seq==self.result_seq:
                            self.name.set('');self.banner.config(text='WAITING FOR CARD',bg='#742d36');self.reason.set('Remove the card before tapping again.')
                    self.root.after(6000,clear_result)
        except queue.Empty: pass
        self.root.after(100,self.drain)

    def door_settings(self):
        win=tk.Toplevel(self.root);win.title('Optional door hardware');frame=ttk.Frame(win,padding=20);frame.pack(fill='both',expand=True)
        enabled=tk.BooleanVar(value=self.cfg.get('door_gpio_enabled',False))
        pin=tk.StringVar(value=str(self.cfg.get('door_gpio_pin',17)))
        high=tk.BooleanVar(value=self.cfg.get('door_active_high',True))
        ttk.Label(frame,text='Leave off until a compatible driver/relay and fail-safe lock are installed.\nGPIO is a 3.3 V control signal, not a lock power supply.\nThe driver must request LOCKED when active and release without controller power.',wraplength=600).pack(pady=10)
        ttk.Checkbutton(frame,text='Enable GPIO hardware on this Pi',variable=enabled).pack(anchor='w')
        ttk.Label(frame,text='BCM GPIO number (not physical header position)').pack(anchor='w')
        ttk.Combobox(frame,textvariable=pin,values=sorted(ALLOWED_PINS),state='readonly').pack(anchor='w')
        ttk.Checkbutton(frame,text='Driver input is active HIGH (uncheck for active LOW)',variable=high).pack(anchor='w',pady=8)
        ttk.Button(frame,text='Run simulation (no GPIO)',command=lambda:messagebox.showinfo('Door simulation',subprocess.check_output(['/usr/bin/python3',str(Path(__file__).with_name('simulate_door.py'))],text=True),parent=win)).pack(pady=6)
        ttk.Label(frame,text='Admin → Hardware Access → Pis & areas controls door permission and unlock time.\nA Wi-Fi outage does not trigger an unlock. A successful pulse finishes on its local timer.\nActual power-loss release and emergency exit depend on the physical installation.',wraplength=600).pack(pady=10)
        def save():
            if enabled.get() and not messagebox.askyesno('Confirm hardware setup','Have the relay polarity, separate lock supply, power-loss release and independent exit been checked? This can energize the selected output after the next heartbeat.',parent=win):return
            self.door.configure({}, {})
            updated={**self.cfg,'door_gpio_enabled':enabled.get(),'door_gpio_pin':int(pin.get()),'door_active_high':high.get()}
            write_private(CONFIG,json.dumps(updated,indent=2)+'\n');self.cfg=updated
            self.notice.set('Door settings saved. Backend permission is also required.');win.destroy()
        ttk.Button(frame,text='Save',command=save).pack(pady=10)

    def connection_test(self):
        if not self.key:return 'Choose Find computer, select the computer and enter a pairing code.'
        try:
            d=heartbeat(self.cfg['origin'],self.key,socket.gethostname(),self.reader_status,self.cfg.get('tls_fingerprint') or None)
            return f"Connected and paired: {d.get('name')} / {d.get('area')}. " + ('Enabled.' if d.get('enabled') else 'Disabled by admin.')
        except (ApiError,ValueError) as exc:return str(exc)

    def close(self):
        self.root.destroy()  # The service keeps scanning after the window closes.

def main():
    os.umask(0o077)
    import fcntl
    DATA.mkdir(parents=True,exist_ok=True,mode=0o700)
    instance=open(DATA/'setup-window.lock','w')
    try: fcntl.flock(instance,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError: return
    root=tk.Tk()
    try: root.scanner_app=App(root)
    except Exception as exc:
        messagebox.showerror('Scanner startup',str(exc)); root.destroy(); return 1
    try: root.mainloop()
    finally:
        if hasattr(root,"scanner_app"): root.scanner_app.door.close()

if __name__=='__main__': raise SystemExit(main() or 0)
