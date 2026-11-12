"""Paired-device client. All access decisions are made by the backend."""
import json
import hashlib, http.client, hmac, ssl, re
from urllib import request, error, parse
from reader import uid_text, safe
DEFAULT_ORIGIN='http://127.0.0.1:18080'
class ApiError(Exception): pass
class ApiAuthError(ApiError): pass
class ApiCompatibilityError(ApiError): pass
class ApiHttpError(ApiError):
    def __init__(self,status):
        self.status=status
        super().__init__(f'Backend returned HTTP {status}. Check the computer app.')
class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self,*args,**kwargs): return None

def origin(value):
    p=parse.urlsplit(value.strip())
    local=p.scheme=='http' and p.hostname in ('127.0.0.1','localhost','::1')
    if not p.hostname or not (p.scheme=='https' or local) or p.username or p.password or p.query or p.fragment or p.path not in ('','/'):
        raise ValueError('Use http://127.0.0.1:18080 for the computer tunnel, or an HTTPS origin.')
    return value.strip().rstrip('/')

def pinned_request(base,path,payload,headers,fingerprint,method="POST"):
    p=parse.urlsplit(origin(base))
    if p.scheme!='https' or not re.fullmatch('[0-9a-f]{64}',fingerprint):
        raise ApiError('Invalid saved computer identity. Use Find computer to connect again.')
    context=ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    context.check_hostname=False;context.verify_mode=ssl.CERT_NONE
    conn=http.client.HTTPSConnection(p.hostname,p.port or 443,context=context,timeout=5)
    try:
        conn.connect()
        actual=hashlib.sha256(conn.sock.getpeercert(binary_form=True)).hexdigest()
        if not hmac.compare_digest(actual,fingerprint):
            raise ApiError('Computer identity changed. No card or credential was sent. Verify the computer using Find computer.')
        # Credentials and card data are sent only AFTER the exact certificate matches.
        conn.request(method,path,body=json.dumps(payload).encode() if payload is not None else None,headers=headers)
        response=conn.getresponse();raw=response.read(65537)
        return response.status,raw
    finally:conn.close()

def decode(status,raw):
    if status==401:raise ApiAuthError('Pi registration was deleted/replaced, or pairing code expired. Ask an admin to add or pair this Pi again.')
    if status==403:raise ApiError('Backend rejected this Pi. Ask an admin to check it.')
    if status==429:raise ApiError('Too many pairing attempts. Wait one minute, then try again.')
    if status!=200:raise ApiHttpError(status)
    if len(raw)>65536:raise ApiError('Backend response too large.')
    data=json.loads(raw)
    if not isinstance(data,dict):raise ApiError('Unexpected backend response.')
    return data

def call(base,path,payload,credentials=None,fingerprint=None,method="POST"):
    headers={'Content-Type':'application/json','Accept':'application/json'}
    if credentials:
        headers.update({'X-Device-Id':credentials['id'],'X-Device-Key':credentials['deviceKey']})
    req=request.Request(origin(base)+path,data=json.dumps(payload).encode() if payload is not None else None,headers=headers,method=method)
    try:
        if fingerprint:
            return decode(*pinned_request(base,path,payload,headers,fingerprint,method))
        with request.build_opener(request.ProxyHandler({}),NoRedirect()).open(req,timeout=5) as r:
            raw=r.read(65537)
            if r.status!=200 or len(raw)>65536: raise ApiError('Unexpected backend response.')
            data=json.loads(raw)
            if not isinstance(data,dict):raise ApiError('Unexpected backend response.')
            return data
    except error.HTTPError as e:
        e.close()
        if e.code==401:raise ApiAuthError('Pi registration was deleted/replaced, or pairing code expired. Pair this Pi again.') from None
        if e.code==429:raise ApiError('Too many pairing attempts. Wait one minute.') from None
        if e.code==403:raise ApiError('Backend rejected this Pi. Ask an admin to check it.') from None
        raise ApiHttpError(e.code) from None
    except (error.URLError,OSError,ValueError,http.client.HTTPException) as e:
        raise ApiError('Cannot reach the backend. Retrying automatically; use manual check-in. For local testing, keep the computer awake and its launcher running.') from None

def protocol(base,fingerprint=None):
    try:
        data=call(base,'/api/v1/hardware/device/protocol',None,fingerprint=fingerprint,method='GET')
    except ApiHttpError as exc:
        if exc.status==404:raise ApiCompatibilityError('Pairing support is missing from the computer app. Start the matching hardware release; saved pairing is unchanged.') from None
        raise
    except ApiAuthError:
        raise ApiCompatibilityError('Computer app does not support this scanner connection. Start the matching hardware app and launcher; saved pairing is unchanged.') from None
    if data.get('protocol')!='makerspace-device-v1' or data.get('version')!=1:
        raise ApiCompatibilityError('Incompatible computer app. Use the matching hardware release; saved pairing is unchanged.')
    return data

def pair(base,code,hostname,fingerprint=None):
    protocol(base,fingerprint)
    data=call(base,'/api/v1/hardware/device/pair',{'code':code.strip(),'hostname':hostname[:100]},fingerprint=fingerprint)
    import re,uuid
    try:
        uuid.UUID(data['id'])
        if not re.fullmatch('[0-9a-f]{64}',data['deviceKey']):raise ValueError()
    except (KeyError,TypeError,ValueError):raise ApiError('Invalid pairing response. Generate a new code and try again.')
    return data

def heartbeat(base,credentials,hostname,reader_status,fingerprint=None):
    protocol(base,fingerprint)
    return call(base,'/api/v1/hardware/device/heartbeat',{'hostname':hostname[:100],'readerStatus':reader_status[:160]},credentials,fingerprint)

def checkin(uid,base,credentials,fingerprint=None):
    result={'granted':False,'reason':'Connection error. Ask staff for manual check-in.','userName':'','error':True}
    try:
        protocol(base,fingerprint)
        data=call(base,'/api/v1/hardware/card-scan',{'cardUid':uid_text(uid)},credentials,fingerprint)
        if type(data.get('approved')) is not bool or not isinstance(data.get('message'),str) or not data['message'].strip():
            raise ApiError('Invalid access response. Ask staff for manual check-in.')
        if data.get('memberName') is not None and not isinstance(data['memberName'],str):raise ApiError('Invalid member response.')
        door=data.get('door',{})
        if not isinstance(door,dict): raise ApiError('Invalid door response.')
        enrollment=data.get('enrollment') is True
        if enrollment and data['approved']:raise ApiError('Invalid enrollment response: no access is granted during assignment.')
        result['door']={'enabled':False} if enrollment else door
        result['scanId']=data.get('scanId') if type(data.get('scanId')) is int else None
        result['enrollment']=enrollment
        result['assigned']=enrollment and data.get('status')=='CARD_ASSIGNED'
        result.update(granted=data['approved'],reason=safe(data['message']),userName=safe(data.get('memberName') or ''),error=False)
    except (ApiError,ValueError,KeyError) as e:result['reason']=safe(str(e))
    return result
