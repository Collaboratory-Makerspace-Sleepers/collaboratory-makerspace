"""Durable telemetry only. Nothing in this queue can request access or actuate GPIO."""
import hashlib
import json
import os
import sqlite3
import threading
import uuid
from datetime import datetime,timezone
from pathlib import Path
from api import call

def binding(cfg,key):
    if not key.get('id'):return 'unpaired'
    identity=cfg.get('server_id') if cfg.get('connection_mode')!='hosted' else cfg.get('origin')
    return hashlib.sha256((str(identity)+'|'+key['id']).encode()).hexdigest()

class EventQueue:
    def __init__(self,path,limit=10000):
        path=Path(path);path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        self.db=sqlite3.connect(path,check_same_thread=False);path.chmod(0o600)
        self.lock=threading.RLock();self.limit=limit
        self.db.executescript('''PRAGMA journal_mode=DELETE;
          CREATE TABLE IF NOT EXISTS events(seq INTEGER PRIMARY KEY AUTOINCREMENT,scope TEXT NOT NULL,id TEXT UNIQUE NOT NULL,payload TEXT NOT NULL);
          CREATE INDEX IF NOT EXISTS scope_events ON events(scope,seq);
          CREATE TABLE IF NOT EXISTS losses(scope TEXT PRIMARY KEY,count INTEGER NOT NULL);''')
    def add(self,scope,kind,detail='',uid=None,scan_id=None,error=False):
        item={'eventId':str(uuid.uuid4()),'occurredAt':datetime.now(timezone.utc).isoformat(),
              'type':kind,'detail':str(detail)[:400],'outcome':'ERROR' if error else 'INFO',
              'cardUid':uid,'scanId':scan_id}
        with self.lock,self.db:
            count=self.db.execute('SELECT count(*) FROM events').fetchone()[0]
            if count>=self.limit:
                old=self.db.execute('SELECT seq,scope FROM events ORDER BY seq LIMIT 1').fetchone()
                self.db.execute('DELETE FROM events WHERE seq=?',(old[0],))
                self.db.execute('INSERT INTO losses VALUES(?,1) ON CONFLICT(scope) DO UPDATE SET count=count+1',(old[1],))
            self.db.execute('INSERT INTO events(scope,id,payload) VALUES(?,?,?)',(scope,item['eventId'],json.dumps(item)))
        return item['eventId']
    def pending(self,scope):
        with self.lock:return self.db.execute('SELECT count(*) FROM events WHERE scope=?',(scope,)).fetchone()[0]
    def flush(self,cfg,key):
        scope=binding(cfg,key)
        if scope=='unpaired':return 0
        with self.lock,self.db:
            loss=self.db.execute('SELECT count FROM losses WHERE scope=?',(scope,)).fetchone()
            # Reserve capacity before writing the explicit gap record.
            if loss and self.db.execute('SELECT count(*) FROM events').fetchone()[0]<self.limit:
                self.add(scope,'QUEUE_GAP',f'{loss[0]} oldest events were discarded because the local queue filled.',error=True)
                self.db.execute('DELETE FROM losses WHERE scope=?',(scope,))
            batch=[json.loads(row[0]) for row in self.db.execute('SELECT payload FROM events WHERE scope=? ORDER BY seq LIMIT 20',(scope,))]
        if not batch:return 0
        response=call(cfg['origin'],'/api/v1/hardware/device/events',{'events':batch},key,cfg.get('tls_fingerprint') or None)
        accepted=response.get('accepted')
        if not isinstance(accepted,list) or any(not isinstance(x,str) for x in accepted):raise ValueError('Invalid event acknowledgement')
        sent={e['eventId'] for e in batch};acked=sent.intersection(accepted)
        with self.lock,self.db:
            self.db.executemany('DELETE FROM events WHERE scope=? AND id=?',[(scope,x) for x in acked])
        return len(acked)
    def close(self):
        with self.lock:self.db.close()
