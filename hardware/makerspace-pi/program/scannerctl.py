#!/usr/bin/env python3
"""Optional terminal setup/status for Pi OS Lite or SSH. No reader/GPIO ownership."""
import argparse,getpass,json,time,uuid
from api import pair,protocol
from connection import hosted_config
from discovery import discover,probe,trust_code
from service_client import status
from state import DATA,CONFIG,KEY,CONTROL,DEFAULTS,read,write_private

def main():
    parser=argparse.ArgumentParser();parser.add_argument('command',choices=['status','pause','resume','pair','events'])
    parser.add_argument('--url',help='Hosted HTTPS origin');parser.add_argument('--host',help='Local computer IPv4 address')
    args=parser.parse_args()
    if args.command=='status':print(json.dumps(status(),indent=2));return
    if args.command in ('pause','resume'):
        write_private(CONTROL,json.dumps({'id':str(uuid.uuid4()),'mode':'paused' if args.command=='pause' else 'scan','at':time.time()}));print('Request sent; check status for acknowledgement.');return
    if args.command=='events':
        import sqlite3
        with sqlite3.connect(DATA/'events.sqlite3') as db:
            print('Queued events:',db.execute('SELECT count(*) FROM events').fetchone()[0]);print('Queue overflow counts:',db.execute('SELECT SUM(count) FROM losses').fetchone()[0] or 0)
        return
    cfg={**DEFAULTS,**read(CONFIG)}
    if args.url:
        cfg=hosted_config(cfg,args.url);protocol(cfg['origin'])
        if input('Backend '+cfg['origin']+' is the address supplied by your team? Type yes: ').strip().lower()!='yes':return
    else:
        found=[probe(args.host)] if args.host else discover()
        if not found:raise SystemExit('No computer found. Start the launcher, check Wi-Fi, or use --host COMPUTER_IP.')
        for i,d in enumerate(found,1):print(i,d['name'],d['origin'],trust_code(d['fingerprint']))
        index=int(input('Computer number: '))-1
        if index<0 or index>=len(found):raise SystemExit('Choose a listed computer')
        target=found[index]
        if input('Verification code matches Admin → Pis & areas? Type yes: ').strip().lower()!='yes':return
        cfg.update(origin=target['origin'],connection_mode='local',server_id=target['serverId'],tls_fingerprint=target['fingerprint'],laptop_name=target['name'])
    code=getpass.getpass('Eight-digit Pi pairing code (hidden): ').strip()
    result=pair(cfg['origin'],code,__import__('socket').gethostname(),cfg.get('tls_fingerprint') or None)
    # Pause while replacing configuration, so an old credential is never used at a new host.
    write_private(CONTROL,json.dumps({'id':str(uuid.uuid4()),'mode':'paused','at':time.time()}))
    write_private(CONFIG,json.dumps(cfg,indent=2))
    write_private(KEY,json.dumps({'id':result['id'],'deviceKey':result['deviceKey'],'serverId':cfg.get('server_id',''),'fingerprint':cfg.get('tls_fingerprint',''),'backendOrigin':cfg['origin']}))
    write_private(CONTROL,json.dumps({'id':str(uuid.uuid4()),'mode':'scan','at':time.time()}))
    print('Paired. The background service loads the connection automatically.')
if __name__=='__main__':main()
