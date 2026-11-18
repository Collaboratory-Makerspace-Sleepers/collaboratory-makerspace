"""Find the computer on a local IPv4 network. Discovery never establishes trust."""
import hashlib
import http.client
import ipaddress
import json
import re
import secrets
import socket
import ssl
import time
import uuid

PORT=9444

def validate(record, address, nonce=None):
    if not isinstance(record,dict) or record.get('protocol')!='makerspace-lan-v1': raise ValueError('Not a Makerspace computer')
    if nonce is not None and record.get('nonce')!=nonce: raise ValueError('Unsolicited discovery response')
    ip=ipaddress.IPv4Address(address)
    if not ip.is_private or ip.is_unspecified or ip.is_multicast: raise ValueError('Use a local network address')
    uuid.UUID(record['serverId'])
    if not re.fullmatch('[0-9a-f]{64}',record.get('fingerprint','')) or record.get('port')!=9443: raise ValueError('Invalid identity')
    return {'serverId':record['serverId'],'name':''.join(c for c in str(record.get('name','Computer')) if c.isprintable())[:80],
            'fingerprint':record['fingerprint'],'origin':'https://'+str(ip)+':9443'}

def broadcasts():
    addresses={'255.255.255.255'}
    # Directed broadcasts help Raspberry Pi OS when several interfaces are present.
    try:
        import fcntl,struct
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as s:
            for _,name in socket.if_nameindex():
                try:
                    data=fcntl.ioctl(s.fileno(),0x8919,struct.pack('256s',name[:15].encode()))
                    addr=socket.inet_ntoa(data[20:24])
                    if addr!='0.0.0.0': addresses.add(addr)
                except OSError: pass
    except ImportError: pass
    return addresses

def discover(seconds=3):
    nonce=secrets.token_hex(16); payload=('MAKERSPACE_DISCOVER_V1 '+nonce).encode()
    found={}
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as s:
        s.setsockopt(socket.SOL_SOCKET,socket.SO_BROADCAST,1);s.bind(('',0));s.settimeout(.25)
        for address in broadcasts():
            try:s.sendto(payload,(address,PORT))
            except OSError:pass
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            try:
                raw,peer=s.recvfrom(2049)
                if len(raw)>2048:continue
                item=validate(json.loads(raw),peer[0],nonce)
                found[(item['serverId'],item['fingerprint'],item['origin'])]=item
            except socket.timeout:continue
            except (OSError,ValueError,KeyError,TypeError):continue
    return sorted(found.values(),key=lambda d:(d['name'],d['origin']))

def trust_code(fingerprint):
    short=fingerprint[:12].upper()
    return '-'.join(short[i:i+4] for i in range(0,len(short),4))

def probe(address):
    """Manual fallback: obtain PUBLIC identity only; user must still compare trust code."""
    ip=ipaddress.IPv4Address(address.strip())
    if not ip.is_private or ip.is_unspecified or ip.is_multicast:raise ValueError('Enter the computer local IPv4 address, for example 192.168.1.20.')
    context=ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT);context.check_hostname=False;context.verify_mode=ssl.CERT_NONE
    conn=http.client.HTTPSConnection(str(ip),9443,context=context,timeout=4)
    try:
        conn.connect();actual=hashlib.sha256(conn.sock.getpeercert(binary_form=True)).hexdigest()
        conn.request('GET','/scanner-info');r=conn.getresponse();raw=r.read(2049)
        if r.status!=200 or len(raw)>2048:raise ValueError('Not a Makerspace computer')
        item=validate(json.loads(raw),str(ip))
        if item['fingerprint']!=actual:raise ValueError('Computer identity did not match its certificate')
        return item
    finally:conn.close()

def remembered_match(items, config):
    # Address can change; trust cannot. Never silently pair with a different computer.
    return next((d for d in items if d['serverId']==config.get('server_id') and d['fingerprint']==config.get('tls_fingerprint')),None)
