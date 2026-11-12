import json,tempfile,unittest
from pathlib import Path
from unittest.mock import Mock,patch
from first_run import needs_install
from release import VERSION
from reader import Hardware
class SetupTests(unittest.TestCase):
 def test_fresh_pi_installs_and_current_install_can_reopen_without_apt(self):
  with tempfile.TemporaryDirectory() as tmp:
   marker=Path(tmp)/'installed.json';self.assertTrue(needs_install(marker,'pi'))
   marker.write_text(json.dumps({'version':VERSION,'user':'pi'}));self.assertFalse(needs_install(marker,'pi'))
   self.assertTrue(needs_install(marker,'other-user'))
   marker.write_text(json.dumps({'version':'older','user':'pi'}));self.assertTrue(needs_install(marker,'pi'))
 def test_broken_marker_requests_repair(self):
  with tempfile.TemporaryDirectory() as tmp:
   marker=Path(tmp)/'installed.json';marker.write_text('broken');self.assertTrue(needs_install(marker,'pi'))
 def test_repeat_start_opens_gui_without_running_installer(self):
  import first_run
  with tempfile.TemporaryDirectory() as tmp:
   folder=Path(tmp);(folder/'installed.json').write_text(json.dumps({'version':VERSION,'user':'pi'}));(folder/'scanner_service.py').touch()
   real_exists=Path.exists
   with patch.object(first_run.sys,'platform','linux'),patch.object(Path,'exists',lambda path: str(path)=='/etc/os-release' or real_exists(path)),patch.object(first_run,'INSTALLED',folder),patch.object(first_run.os,'geteuid',return_value=1000),patch.object(first_run.pwd,'getpwuid',return_value=Mock(pw_name='pi')),patch.object(first_run.sys,'argv',['first_run.py']),patch.dict(first_run.os.environ,{'DISPLAY':':test'}),patch.object(first_run.subprocess,'run',return_value=Mock(returncode=0)) as run:
    first_run.main()
   commands=[c.args[0] for c in run.call_args_list]
   self.assertFalse(any('install.sh' in ' '.join(c) for c in commands))
   self.assertTrue(any(c[-1]==str(folder/'launch_scanner.py') for c in commands))
 def test_extracted_folder_with_spaces_runs_entry_from_program(self):
  import os,subprocess
  with tempfile.TemporaryDirectory(prefix='scanner setup ') as tmp:
   folder=Path(tmp);(folder/'program').mkdir();binpath=folder/'bin';binpath.mkdir()
   (folder/'Start-Scanner.sh').write_text((Path(__file__).resolve().parents[1]/'Start-Scanner.sh').read_text())
   fake=binpath/'python3';fake.write_text('#!/bin/sh\npwd\nprintf "%s\\n" "$@"\n');fake.chmod(0o755)
   result=subprocess.run(['bash',str(folder/'Start-Scanner.sh'),'--repair'],env={**os.environ,'PATH':str(binpath)+':'+os.environ['PATH']},capture_output=True,text=True)
   self.assertEqual(result.returncode,0,result.stderr);self.assertIn(str(folder/'program'),result.stdout);self.assertIn('first_run.py\n--repair',result.stdout)
 def reader(self):
  hw=Hardware.__new__(Hardware);hw.conn=None;hw.cfg={'reader_match':'ACR122','led_enabled':False};hw.no_card=LookupError
  hw.last_message='Reader error';hw.last_uid='00112233';hw.notify=Mock();hw.name=None;return hw
 def test_no_card_clears_stale_reader_error_and_allows_same_card_again(self):
  hw=self.reader();device=Mock();device.__str__=Mock(return_value='ACS ACR122U');connection=Mock();device.createConnection.return_value=connection;connection.connect.side_effect=LookupError('No card');hw.readers=lambda:[device]
  self.assertIsNone(hw.poll());self.assertIn('Reader ready',hw.last_message);self.assertIsNone(hw.last_uid)
  connection.connect.side_effect=None;connection.transmit.return_value=([0,17,34,51],144,0)
  self.assertEqual(hw.poll(),'00112233');self.assertIsNone(hw.poll())
 def test_usb_unplug_and_replug_reconnects(self):
  hw=self.reader();hw.readers=lambda:[];self.assertIsNone(hw.poll());self.assertIn('No matching USB',hw.last_message);self.assertIsNone(hw.last_uid)
  device=Mock();device.__str__=Mock(return_value='ACS ACR122U');device.createConnection.return_value.transmit.return_value=([0,17,34,51],144,0);hw.readers=lambda:[device]
  self.assertEqual(hw.poll(),'00112233')
 def test_reader_error_includes_real_failure_and_retries(self):
  hw=self.reader();hw.readers=Mock(side_effect=RuntimeError('Permission denied'))
  self.assertIsNone(hw.poll());self.assertIn('Permission denied',hw.last_message);self.assertIsNone(hw.conn)
