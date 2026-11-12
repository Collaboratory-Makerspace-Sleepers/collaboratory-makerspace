import importlib, json, os, queue, tempfile, time, unittest
from pathlib import Path
from unittest.mock import Mock,patch
from event_queue import EventQueue,binding
from scanner_service import ScannerService
from state import write_private
CFG={'origin':'https://example.invalid','connection_mode':'hosted'}
KEY={'id':'pi-one','deviceKey':'a'*64,'backendOrigin':CFG['origin']}
class StatusTests(unittest.TestCase):
 def test_gui_receives_scans_after_service_restart(self):
  from service_client import messages_since
  message={'sequence':2,'kind':'decision','value':['00112233',{}]}
  pid,seq,items=messages_since({'pid':22,'recent':[message],'lastMessage':message},11,100)
  self.assertEqual((pid,seq,items),(22,2,[message]))
 def test_status_poll_does_not_repeat_previously_seen_scans(self):
  from service_client import messages_since
  old={'sequence':2,'kind':'decision','value':[]};new={'sequence':3,'kind':'notice','value':'Ready'}
  self.assertEqual(messages_since({'pid':22,'recent':[old],'lastMessage':new},22,2),(22,3,[new]))
class QueueTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.path=Path(self.tmp.name)/'events.db';self.q=EventQueue(self.path)
 def tearDown(self):self.q.close();self.tmp.cleanup()
 def test_outage_retains_queue_and_restart_preserves_event_id(self):
  scope=binding(CFG,KEY);event=self.q.add(scope,'CARD_TAP',uid='00112233')
  with patch('event_queue.call',side_effect=OSError):
   with self.assertRaises(OSError):self.q.flush(CFG,KEY)
  self.q.close();self.q=EventQueue(self.path)
  with patch('event_queue.call',return_value={'accepted':[event]}) as call:
   self.assertEqual(self.q.flush(CFG,KEY),1)
   self.assertEqual(call.call_args.args[1],'/api/v1/hardware/device/events')
   self.assertEqual(call.call_args.args[2]['events'][0]['eventId'],event)
  self.assertEqual(self.q.pending(scope),0)
 def test_repeated_transmission_never_calls_card_scan(self):
  self.q.add(binding(CFG,KEY),'DOOR_UNLOCK_COMMAND','Telemetry only')
  with patch('event_queue.call',return_value={'accepted':[]}) as call:
   self.q.flush(CFG,KEY);self.q.flush(CFG,KEY)
  self.assertTrue(all(c.args[1].endswith('/device/events') for c in call.call_args_list))
 def test_ack_only_deletes_sent_ids(self):
  scope=binding(CFG,KEY);a=self.q.add(scope,'CARD_TAP');b=self.q.add(scope,'READER_ERROR')
  with patch('event_queue.call',return_value={'accepted':[a,'not-sent']}):self.q.flush(CFG,KEY)
  self.assertEqual(self.q.pending(scope),1)
 def test_other_backend_and_other_pi_never_receive_prior_events(self):
  self.q.add(binding(CFG,KEY),'CARD_TAP')
  with patch('event_queue.call') as call:
   self.q.flush({**CFG,'origin':'https://other.invalid'},KEY);self.q.flush(CFG,{**KEY,'id':'pi-two'});call.assert_not_called()
 def test_overflow_is_bounded_and_reported(self):
  self.q.limit=2;scope=binding(CFG,KEY)
  for _ in range(3):self.q.add(scope,'CARD_TAP')
  self.assertEqual(self.q.pending(scope),2)
  def accept(*args):return {'accepted':[x['eventId'] for x in args[2]['events']]}
  with patch('event_queue.call',side_effect=accept) as call:
   self.q.flush(CFG,KEY);self.q.flush(CFG,KEY)
   self.assertEqual(call.call_args.args[2]['events'][0]['type'],'QUEUE_GAP')
 def test_unpaired_events_are_not_sent_under_later_identity(self):
  self.q.add('unpaired','SERVICE_STARTED')
  with patch('event_queue.call') as call:self.q.flush(CFG,KEY);call.assert_not_called()
class ServiceTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.data=Path(self.tmp.name)
  write_private(self.data/'config.json',json.dumps(CFG));write_private(self.data/'credentials.json',json.dumps(KEY))
  self.worker=Mock();self.worker.snapshot.return_value=(1,'scan',CFG,KEY)
  self.door=Mock();self.service=ScannerService(self.data,self.data/'status.json',lambda *args:self.worker,lambda **kwargs:self.door)
 def tearDown(self):self.service.outbox.close();self.tmp.cleanup()
 def test_service_import_does_not_import_tkinter(self):
  import subprocess,sys
  result=subprocess.run([sys.executable,'-c','import scanner_service,sys;assert "tkinter" not in sys.modules'],cwd=Path(__file__).parent,capture_output=True)
  self.assertEqual(result.returncode,0,result.stderr)
 def test_boot_starts_scanning_without_desktop(self):
  self.service.configure();self.assertEqual(self.worker.configure.call_args.args[1],'scan')
 def test_old_pause_is_not_replayed_at_boot(self):
  write_private(self.data/'control.json',json.dumps({'id':'old','mode':'paused'}))
  service=ScannerService(self.data,self.data/'status.json',lambda *args:self.worker,lambda **kwargs:self.door)
  try:service.configure();self.assertEqual(self.worker.configure.call_args.args[1],'scan')
  finally:service.outbox.close()
 def test_fresh_pause_and_resume(self):
  self.service.configure()
  for mode in ['paused','scan']:
   write_private(self.data/'control.json',json.dumps({'id':mode,'mode':mode}));self.service.configure();self.assertEqual(self.worker.configure.call_args.args[1],mode)
 def test_credentials_from_different_backend_do_not_scan(self):
  write_private(self.data/'config.json',json.dumps({**CFG,'origin':'https://other.invalid'}));self.service.configure()
  self.assertEqual(self.worker.configure.call_args.args[1],'paused')
 def test_reconnection_recovers_without_pairing_or_unlock(self):
  self.service.configure()
  with patch('scanner_service.heartbeat',side_effect=[OSError,{'enabled':True,'doorEnabled':False,'name':'Front'}]):
   self.service.network_tick();self.assertEqual(self.service.connection,'OFFLINE');self.door.approved.assert_not_called()
   self.service.next_heartbeat=0;self.service.network_tick();self.assertEqual(self.service.connection,'CONNECTED');self.door.approved.assert_not_called()
 def test_status_has_no_credential(self):
  self.service.configure();self.service.write_status();self.assertNotIn('deviceKey',(self.data/'status.json').read_text())
 def test_system_service_requires_no_gui_and_restarts(self):
  from install_service import unit
  text=unit('pi','/home/pi','pi',True)
  self.assertIn('WantedBy=multi-user.target',text);self.assertIn('Restart=on-failure',text);self.assertIn('WatchdogSec=60',text)
  self.assertNotIn('DISPLAY',text);self.assertNotIn('graphical.target',text)
 def test_simulation_has_no_gpio(self):
  import subprocess,sys
  result=subprocess.run([sys.executable,'simulate_door.py'],cwd=Path(__file__).parent,capture_output=True,text=True)
  self.assertEqual(result.returncode,0,result.stderr);self.assertIn('No physical pin',result.stdout)

class ActualWorkerServiceTest(unittest.TestCase):
 def test_service_scans_and_recovers_reader_without_gui(self):
  import threading
  from scanner_service import ScannerService
  from engine import Worker
  with tempfile.TemporaryDirectory() as folder:
   data=Path(folder);write_private(data/'config.json',json.dumps(CFG));write_private(data/'credentials.json',json.dumps(KEY))
   reader=Mock();first=threading.Event();second=threading.Event();count=[0]
   def poll():
    count[0]+=1
    if count[0]==1:raise OSError('unplugged')
    if count[0]==2:first.set();return '00112233'
    if count[0]==3:second.set();return '00112244'
    return None
   reader.poll.side_effect=poll
   decision={'granted':False,'reason':'Denied','userName':'','error':False,'scanId':1}
   with patch('engine.Hardware',return_value=reader),patch('engine.checkin',return_value=decision) as scan,patch('scanner_service.heartbeat',return_value={'enabled':True,'doorEnabled':False}),patch('event_queue.call',side_effect=OSError('offline')):
    service=ScannerService(data,data/'status.json');thread=threading.Thread(target=service.run);thread.start()
    try:
     self.assertTrue(second.wait(6))
     deadline=time.monotonic()+2
     while scan.call_count<2 and time.monotonic()<deadline:time.sleep(.02)
     self.assertGreaterEqual(scan.call_count,2)
     self.assertGreater(service.outbox.pending(binding(CFG,KEY)),0)
     self.assertTrue((data/'status.json').exists())
    finally:service.stop.set();thread.join(4)
    self.assertFalse(thread.is_alive())
