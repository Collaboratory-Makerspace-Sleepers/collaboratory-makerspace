import hashlib,http.client,json,socket,ssl,subprocess,tempfile,threading,unittest,uuid
from http.server import BaseHTTPRequestHandler,ThreadingHTTPServer
from pathlib import Path
import api,discovery

class NetworkTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp=tempfile.TemporaryDirectory();d=Path(cls.temp.name)
        subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-keyout',str(d/'key.pem'),'-out',str(d/'cert.pem'),'-subj','/CN=localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
        cls.pin=hashlib.sha256(ssl.PEM_cert_to_DER_cert((d/'cert.pem').read_text())).hexdigest()
        cls.received=[];cls.response_status=200
        class Handler(BaseHTTPRequestHandler):
            def log_message(self,*a):pass
            def do_GET(self):
                data=json.dumps({'protocol':'makerspace-device-v1','version':1}).encode()
                self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
            def do_POST(self):
                cls.received.append((self.path,dict(self.headers),json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
                data=json.dumps({'approved':True,'message':'Access granted','memberName':'Test Member'}).encode()
                self.send_response(cls.response_status);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
        cls.server=ThreadingHTTPServer(('127.0.0.1',0),Handler)
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(d/'cert.pem',d/'key.pem')
        cls.server.socket=context.wrap_socket(cls.server.socket,server_side=True)
        cls.server.handle_error=lambda *_:None  # Expected peer close after a rejected certificate.
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.base='https://127.0.0.1:'+str(cls.server.server_port)
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close();cls.temp.cleanup()
    def setUp(self):self.received.clear();type(self).response_status=200
    def test_pinned_scan_transmits_uid_and_credentials(self):
        result=api.checkin('00112233',self.base,{'id':'test','deviceKey':'a'*64},self.pin)
        self.assertTrue(result['granted']);self.assertEqual(self.received[0][2],{'cardUid':'00112233'})
        self.assertEqual(self.received[0][1]['X-Device-Key'],'a'*64)
    def test_wrong_certificate_sends_no_http_or_credentials(self):
        result=api.checkin('00112233',self.base,{'id':'test','deviceKey':'a'*64},'0'*64)
        self.assertFalse(result['granted']);self.assertTrue(result['error']);self.assertEqual(self.received,[])
    def test_deleted_device_is_auth_error(self):
        type(self).response_status=401
        with self.assertRaises(api.ApiAuthError):api.heartbeat(self.base,{'id':'test','deviceKey':'a'*64},'pi','Ready',self.pin)
    def test_rate_limit_is_actionable(self):
        type(self).response_status=429
        with self.assertRaisesRegex(api.ApiError,'Wait one minute'):api.pair(self.base,'12345678','pi',self.pin)
    def test_discovery_rejects_wrong_nonce_and_bad_identity(self):
        record={'protocol':'makerspace-lan-v1','serverId':str(uuid.uuid4()),'fingerprint':self.pin,'name':'Laptop','port':9443,'nonce':'expected'}
        with self.assertRaises(ValueError):discovery.validate(record,'192.168.1.4','wrong')
        self.assertEqual(discovery.validate(record,'192.168.1.4','expected')['origin'],'https://192.168.1.4:9443')
        with self.assertRaises(ValueError):discovery.validate(record,'8.8.8.8','expected')
    def test_reconnect_requires_same_identity_and_pin(self):
        saved={'server_id':str(uuid.uuid4()),'tls_fingerprint':self.pin}
        legitimate={'serverId':saved['server_id'],'fingerprint':self.pin,'origin':'https://192.168.4.25:9443'}
        malicious={**legitimate,'fingerprint':'0'*64}
        self.assertIsNone(discovery.remembered_match([malicious],saved))
        self.assertEqual(discovery.remembered_match([malicious,legitimate],saved),legitimate)

class ReconnectTests(unittest.TestCase):
    def make_app(self,mode='scan'):
        from unittest.mock import Mock
        from scanner_gui import App
        app=object.__new__(App)
        app.cfg={'origin':'https://192.168.1.3:9443','server_id':'saved-id','tls_fingerprint':'a'*64,'laptop_name':'Laptop'}
        app.key={'id':'pi','deviceKey':'b'*64};app.reconnect_busy=False;app.reader_status='Ready';app.epoch=1
        app.worker=Mock();app.worker.snapshot.return_value=(1,mode,app.cfg,app.key)
        app.notice=Mock();app.device_status=Mock();app.registration_lost=Mock()
        def background(action,done):
            try:value=action()
            except Exception as exc:done(False,exc)
            else:done(True,value)
        app.background=background
        return app
    def test_changed_ip_reconnect_keeps_paused_mode_and_pin(self):
        from unittest.mock import patch
        from scanner_gui import App
        app=self.make_app('paused')
        target={'serverId':'saved-id','fingerprint':'a'*64,'origin':'https://192.168.4.9:9443'}
        with tempfile.TemporaryDirectory() as d,patch('scanner_gui.CONFIG',Path(d)/'config.json'),patch('scanner_gui.discover',return_value=[target]),patch('scanner_gui.heartbeat',return_value={'enabled':True}):
            App.reconnect(app)
            self.assertEqual(app.cfg['origin'],target['origin']);self.assertEqual(app.cfg['tls_fingerprint'],'a'*64)
            self.assertEqual(app.worker.configure.call_args.args[1],'paused')
            self.assertEqual(app.epoch,2)
    def test_rogue_discovery_does_not_move_saved_connection(self):
        from unittest.mock import patch
        from scanner_gui import App
        app=self.make_app();before=dict(app.cfg)
        with patch('scanner_gui.discover',return_value=[{'serverId':'saved-id','fingerprint':'0'*64}]),patch('scanner_gui.heartbeat') as hb:
            App.reconnect(app);hb.assert_not_called();self.assertEqual(app.cfg,before)
    def test_deleted_registration_is_reported_after_address_change(self):
        from unittest.mock import patch
        from scanner_gui import App
        app=self.make_app();target={'serverId':'saved-id','fingerprint':'a'*64,'origin':'https://192.168.4.9:9443'}
        with tempfile.TemporaryDirectory() as d,patch('scanner_gui.CONFIG',Path(d)/'config.json'),patch('scanner_gui.discover',return_value=[target]),patch('scanner_gui.heartbeat',side_effect=api.ApiAuthError('Deleted')):
            App.reconnect(app);app.registration_lost.assert_called_once();app.worker.configure.assert_not_called()

if __name__=='__main__':unittest.main()
