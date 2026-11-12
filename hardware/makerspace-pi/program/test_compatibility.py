import json,tempfile,threading,unittest
from http.server import BaseHTTPRequestHandler,HTTPServer
from pathlib import Path
from unittest.mock import Mock,patch
import api
class CompatibilityTests(unittest.TestCase):
    def test_old_backend_never_receives_device_credentials(self):
        calls=[]
        class Old(BaseHTTPRequestHandler):
            def do_GET(self):
                calls.append(dict(self.headers));self.send_response(401);self.end_headers()
            def do_POST(self):raise AssertionError('Must not post to incompatible backend')
            def log_message(self,*a):pass
        with HTTPServer(('127.0.0.1',0),Old) as server:
            thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
            try:
                with self.assertRaises(api.ApiCompatibilityError):api.heartbeat(f'http://127.0.0.1:{server.server_port}',{'id':'x','deviceKey':'secret'},'pi','ready')
                self.assertEqual(len(calls),1);self.assertNotIn('X-Device-Key',calls[0])
            finally:server.shutdown();thread.join()
    def test_auth_failure_keeps_pairing_file_and_credentials(self):
        from scanner_gui import App
        app=object.__new__(App);app.pause=Mock();app.device_status=Mock();app.reason=Mock();app.key={'id':'x','deviceKey':'secret'}
        with tempfile.TemporaryDirectory() as d:
            key=Path(d)/'credentials.json';key.write_text(json.dumps(app.key))
            with patch('scanner_gui.KEY',key):App.registration_lost(app)
            self.assertTrue(key.exists());self.assertEqual(app.key['deviceKey'],'secret');self.assertTrue(app.recover_after_auth)
