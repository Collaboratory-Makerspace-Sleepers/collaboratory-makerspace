import json,threading,unittest
from http.server import HTTPServer,BaseHTTPRequestHandler
from unittest.mock import patch
import api
from reader import uid_text,result_led_command
class Handler(BaseHTTPRequestHandler):
    response={'approved':True,'message':'Access granted','memberName':'Test Member'}
    status=200; received=[]
    def do_GET(self):
        data=json.dumps({'protocol':'makerspace-device-v1','version':1}).encode()
        self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def do_POST(self):
        Handler.received.append((self.path,dict(self.headers),json.loads(self.rfile.read(int(self.headers['Content-Length'])))))
        self.send_response(Handler.status);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(json.dumps(Handler.response).encode())
    def log_message(self,*args):pass
class Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server=HTTPServer(('127.0.0.1',0),Handler);threading.Thread(target=cls.server.serve_forever,daemon=True).start()
        cls.base=f'http://127.0.0.1:{cls.server.server_port}'
        cls.cred={'id':'11111111-1111-4111-8111-111111111111','deviceKey':'a'*64}
    @classmethod
    def tearDownClass(cls):cls.server.shutdown();cls.server.server_close()
    def setUp(self):Handler.status=200;Handler.response={'approved':True,'message':'Access granted','memberName':'Test Member'};Handler.received=[]
    def test_scan_contract_and_leading_zero(self):
        self.assertTrue(api.checkin('00112233',self.base,self.cred)['granted'])
        path,headers,body=Handler.received[-1];self.assertEqual(path,'/api/v1/hardware/card-scan');self.assertEqual(body,{'cardUid':'00112233'})
        self.assertEqual(headers['X-Device-Id'],self.cred['id']);self.assertEqual(headers['X-Device-Key'],self.cred['deviceKey'])
    def test_denied(self):
        Handler.response={'approved':False,'message':'Card not registered','memberName':None}
        r=api.checkin('00112233',self.base,self.cred);self.assertFalse(r['granted']);self.assertFalse(r['error'])
    def test_invalid_bool_fails_closed(self):
        Handler.response={'approved':'false','message':'oops'};self.assertTrue(api.checkin('00112233',self.base,self.cred)['error'])
    def test_old_api_fails_closed(self):
        Handler.response={'granted':True,'reason':'ok'};self.assertFalse(api.checkin('00112233',self.base,self.cred)['granted'])
    def test_auth_failure_not_retried(self):
        Handler.status=401;r=api.checkin('00112233',self.base,self.cred);self.assertFalse(r['granted']);self.assertEqual(len(Handler.received),1)
    def test_server_failure_not_retried(self):
        Handler.status=500;self.assertTrue(api.checkin('00112233',self.base,self.cred)['error']);self.assertEqual(len(Handler.received),1)
    def test_redirect_not_followed(self):
        Handler.status=302;self.assertTrue(api.checkin('00112233',self.base,self.cred)['error']);self.assertEqual(len(Handler.received),1)
    def test_nonlocal_plain_http_blocked(self):
        with self.assertRaises(ValueError):api.origin('http://192.168.1.3:8080')
    def test_pair_response(self):
        Handler.response={**self.cred,'name':'Pi','area':'Entrance'}
        self.assertEqual(api.pair(self.base,'b'*32,'testpi')['id'],self.cred['id'])
        self.assertNotIn('X-Device-Key',Handler.received[-1][1])
    def test_heartbeat_never_scans(self):
        Handler.response={'enabled':True,'area':'Entrance'}
        api.heartbeat(self.base,self.cred,'pi','Paused');self.assertEqual(Handler.received[-1][0],'/api/v1/hardware/device/heartbeat')
    def test_uid_invalid(self):
        with self.assertRaises(ValueError):uid_text('abc')
    def test_led_green_returns_red(self):self.assertEqual(result_led_command(True),[255,0,64,237,4,8,0,1,0])
    def test_led_denied_amber_returns_red(self):self.assertEqual(result_led_command(False,True),[255,0,64,253,4,3,2,3,1])
import reader
class HardwareTests(unittest.TestCase):
    def test_led_sequences(self):
        self.assertEqual(reader.led_command('red'),[255,0,64,13,4,0,0,0,0])
        self.assertEqual(reader.result_led_command(True),[255,0,64,237,4,8,0,1,0])
        self.assertEqual(reader.result_led_command(False,True),[255,0,64,253,4,3,2,3,1])
    def test_card_changed_cannot_flash_green(self):
        from unittest.mock import Mock
        hw=reader.Hardware.__new__(reader.Hardware)
        hw.cfg={'led_enabled':True,'buzzer_enabled':False,'denial_beep_enabled':True}
        hw.conn=Mock();hw.conn.transmit.return_value=([1,2,3,4],144,0)
        hw.last_message='';hw.notify=None
        self.assertFalse(hw.result_feedback(True,'95E263D4'))
        commands=[c.args[0] for c in hw.conn.transmit.call_args_list]
        self.assertNotIn(reader.result_led_command(True),commands)
    def test_held_card_submitted_once(self):
        from unittest.mock import Mock
        hw=reader.Hardware.__new__(reader.Hardware);hw.conn=Mock()
        hw.conn.transmit.return_value=([149,226,99,212],144,0)
        hw.last_uid=None;hw.last_message='';hw.no_card=LookupError
        self.assertEqual(hw.poll(),'95E263D4');self.assertIsNone(hw.poll())

class WorkerTests(unittest.TestCase):
    def test_pause_discards_inflight_approval(self):
        import queue
        from engine import Worker
        from unittest.mock import Mock
        started=threading.Event();release=threading.Event();events=queue.Queue()
        hw=Mock();hw.poll.side_effect=['00112233',None,None]
        def slow(*args,**kwargs):
            started.set();release.wait(2)
            return {'granted':True,'reason':'Access granted','userName':'Member','error':False}
        worker=Worker(events)
        with patch('engine.Hardware',return_value=hw),patch('engine.checkin',side_effect=slow):
            worker.configure(1,'scan',{'origin':'http://127.0.0.1:18080'}, {'id':'x','deviceKey':'a'})
            worker.start();self.assertTrue(started.wait(2))
            worker.configure(2,'paused',{},'');release.set();worker.stop_event.set();worker.join(2)
        hw.result_feedback.assert_not_called()
        self.assertFalse(any(e[1]=='decision' for e in list(events.queue)))

if __name__=='__main__':unittest.main()
