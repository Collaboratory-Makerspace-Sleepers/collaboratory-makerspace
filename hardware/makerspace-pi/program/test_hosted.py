import unittest
from unittest.mock import patch
from connection import hosted_config, credentials_match
import api
class HostedTests(unittest.TestCase):
    def test_hosted_requires_https_and_clears_local_pin(self):
        config=hosted_config({'server_id':'old','tls_fingerprint':'old'},'https://makerspace.example.edu/')
        self.assertEqual(config['origin'],'https://makerspace.example.edu');self.assertEqual(config['tls_fingerprint'],'')
        for url in ['http://127.0.0.1:8080','http://example.com','https://user:pass@example.com','https://example.com/api']:
            with self.subTest(url=url), self.assertRaises(ValueError):hosted_config({},url)
    def test_hosted_credential_cannot_follow_origin_change(self):
        config=hosted_config({},'https://one.example.edu');key={'backendOrigin':config['origin'],'deviceKey':'secret'}
        self.assertTrue(credentials_match(config,key))
        self.assertFalse(credentials_match(hosted_config(config,'https://two.example.edu'),key))
        self.assertFalse(credentials_match(config,{'serverId':'old','fingerprint':'old','deviceKey':'secret'}))
    def test_assignment_response_never_becomes_access_or_door_approval(self):
        response={'approved':False,'message':'Card assigned','status':'CARD_ASSIGNED','enrollment':True,'door':{'enabled':True,'unlockSeconds':5}}
        with patch('api.protocol'),patch('api.call',return_value=response):
            result=api.checkin('00112233','https://example.edu',{})
        self.assertTrue(result['assigned']);self.assertFalse(result['granted']);self.assertFalse(result['door']['enabled']);self.assertFalse(result['error'])
        with patch('api.protocol'),patch('api.call',return_value={**response,'approved':True}):
            self.assertTrue(api.checkin('00112233','https://example.edu',{})['error'])
if __name__=='__main__':unittest.main()

class HostedTlsTests(unittest.TestCase):
    def test_ca_verified_hosted_pairing_and_unknown_ca_rejection(self):
        import tempfile,subprocess,ssl,threading,json,os
        from pathlib import Path
        from http.server import HTTPServer,BaseHTTPRequestHandler
        calls=[]
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*args):pass
            def respond(self,data):
                body=json.dumps(data).encode();self.send_response(200);self.send_header('Content-Length',str(len(body)));self.end_headers();self.wfile.write(body)
            def do_GET(self):calls.append('GET');self.respond({'protocol':'makerspace-device-v1','version':1})
            def do_POST(self):
                calls.append('POST');self.rfile.read(int(self.headers['Content-Length']));self.respond({'id':'11111111-1111-4111-8111-111111111111','deviceKey':'a'*64})
        with tempfile.TemporaryDirectory() as tmp:
            cert=Path(tmp)/'cert.pem';key=Path(tmp)/'key.pem'
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-keyout',str(key),'-out',str(cert),'-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
            context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cert,key)
            with HTTPServer(('127.0.0.1',0),Handler) as server:
                server.socket=context.wrap_socket(server.socket,server_side=True)
                thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
                try:
                    base=f'https://localhost:{server.server_port}'
                    with self.assertRaises(api.ApiError):api.pair(base,'12345678','pi')
                    self.assertEqual(calls,[])
                    with patch.dict(os.environ,{'SSL_CERT_FILE':str(cert)}):
                        self.assertEqual(api.pair(base,'12345678','pi')['deviceKey'],'a'*64)
                    self.assertEqual(calls,['GET','POST'])
                finally:server.shutdown();thread.join()
