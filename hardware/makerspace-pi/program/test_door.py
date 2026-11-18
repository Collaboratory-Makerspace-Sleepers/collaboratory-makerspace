import unittest
from unittest.mock import Mock
from door import Door
class FakeTimer:
    def __init__(self,seconds,fn):self.seconds=seconds;self.fn=fn;self.cancelled=False
    def start(self):pass
    def cancel(self):self.cancelled=True
class DoorTests(unittest.TestCase):
    def setUp(self):
        self.output=Mock();self.factory=Mock(return_value=self.output)
        self.door=Door(self.factory,FakeTimer)
        self.local={'door_gpio_enabled':True,'door_gpio_pin':17,'door_active_high':True}
        self.remote={'doorEnabled':True}
        self.good={'granted':True,'error':False,'door':{'enabled':True,'unlockSeconds':5}}
    def tearDown(self):self.door.close()
    def test_default_does_not_allocate_gpio(self):
        self.door.configure({},self.remote);self.factory.assert_not_called()
        self.door.configure(self.local,{});self.factory.assert_not_called()
    def test_approval_pulses_then_locks_on_local_timer(self):
        self.door.configure(self.local,self.remote);self.output.on.assert_called_once()
        self.assertTrue(self.door.approved(self.good));timer=self.door.timer
        self.assertEqual(timer.seconds,5);self.output.off.assert_called_once()
        timer.fn();self.assertEqual(self.output.on.call_count,2)
    def test_denial_error_or_no_backend_enable_never_pulses(self):
        self.door.configure(self.local,self.remote)
        for decision in [{**self.good,'granted':False},{**self.good,'error':True},{**self.good,'door':{'enabled':False}}]:
            self.assertFalse(self.door.approved(decision))
        self.output.off.assert_not_called()
    def test_shutdown_releases_and_stale_timer_cannot_relock(self):
        self.door.configure(self.local,self.remote);self.door.approved(self.good);timer=self.door.timer
        self.door.close();timer.fn();self.output.on.assert_called_once();self.output.close.assert_called_once()
    def test_repeated_approval_does_not_extend_existing_pulse(self):
        self.door.configure(self.local,self.remote);self.door.approved(self.good);timer=self.door.timer
        self.assertFalse(self.door.approved(self.good));self.assertIs(timer,self.door.timer)
    def test_invalid_duration_never_releases(self):
        self.door.configure(self.local,self.remote)
        for seconds in [0,31,True,'5',None]:
            with self.assertRaises(ValueError):self.door.approved({**self.good,'door':{'enabled':True,'unlockSeconds':seconds}})
        self.output.off.assert_not_called()
    def test_active_low_and_pin_validation(self):
        self.door.configure({**self.local,'door_active_high':False},self.remote)
        self.factory.assert_called_once_with(17,active_high=False,initial_value=False)
        with self.assertRaises(ValueError):self.door.configure({**self.local,'door_gpio_pin':2},self.remote)
    def test_admin_removing_door_configuration_releases_output(self):
        self.door.configure(self.local,self.remote);self.door.configure(self.local,{'doorEnabled':False})
        self.output.off.assert_called_once();self.output.close.assert_called_once()
    def test_heartbeat_during_unlock_does_not_relock_early(self):
        self.door.configure(self.local,self.remote);self.door.approved(self.good)
        self.door.configure(self.local,self.remote);self.output.on.assert_called_once()
