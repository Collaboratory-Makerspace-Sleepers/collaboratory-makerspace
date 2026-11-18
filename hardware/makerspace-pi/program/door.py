"""Optional fail-safe lock signal. GPIO drives a rated driver, never a lock coil.

Logical ON requests locked; OFF requests released. Hardware must release without
controller power. No GPIO is allocated until BOTH local setup and backend enable
it. Wi-Fi loss does not itself unlock; an already approved pulse still finishes.
"""
import threading

ALLOWED_PINS = {4,5,6,12,13,16,17,18,19,20,21,22,23,24,25,26,27}

class Door:
    def __init__(self, factory=None, timer_factory=threading.Timer, notify=None):
        self.notify=notify or (lambda *args,**kwargs: None)
        self.factory=factory
        self.timer_factory=timer_factory
        self.lock=threading.RLock()
        self.output=None
        self.timer=None
        self.signature=None
        self.generation=0
        self.closed=False

    def _release(self):
        self.generation+=1
        if self.timer: self.timer.cancel(); self.timer=None
        if self.output:
            self.notify('DOOR_OUTPUT_RELEASED','Control output released; physical position is not sensed')
            try: self.output.off()
            finally: self.output.close(); self.output=None
        self.signature=None

    def configure(self, local, remote):
        with self.lock:
            if self.closed:return
            if local.get('door_gpio_enabled') is not True or remote.get('doorEnabled') is not True:
                self._release(); return
            pin=local.get('door_gpio_pin')
            active_high=local.get('door_active_high')
            if type(pin) is not int or pin not in ALLOWED_PINS or type(active_high) is not bool:
                self._release(); raise ValueError('Set a supported BCM GPIO and relay polarity in local door settings.')
            signature=(pin,active_high)
            if self.signature==signature:return
            self._release()
            factory=self.factory
            if factory is None:
                from gpiozero import OutputDevice
                factory=OutputDevice
            self.output=factory(pin,active_high=active_high,initial_value=False)
            self.signature=signature
            self.notify('DOOR_ARMED','Control output armed; physical position is not sensed')
            self.output.on()  # Explicitly armed: energize driver to request locked.

    def approved(self, decision):
        with self.lock:
            remote=decision.get('door',{})
            if self.closed or not self.output or decision.get('granted') is not True or decision.get('error') is not False or remote.get('enabled') is not True:
                return False
            seconds=remote.get('unlockSeconds')
            if type(seconds) is not int or not 1<=seconds<=30:raise ValueError('Invalid unlock duration')
            # Do not extend a pulse already in progress; require a new tap afterward.
            if self.timer:return False
            self.output.off()
            self.notify('DOOR_UNLOCK_COMMAND',f'Release signal for {seconds} seconds; physical position is not sensed')
            self.generation+=1;generation=self.generation
            def finish():
                with self.lock:
                    if not self.closed and self.output and generation==self.generation:
                        try:
                            self.output.on();self.timer=None
                            self.notify('DOOR_RELOCK_COMMAND','Lock signal restored; physical position is not sensed')
                        except Exception:
                            self.notify('DOOR_ERROR','Could not restore lock signal; inspect hardware',error=True)
            self.timer=self.timer_factory(seconds,finish)
            self.timer.daemon=True;self.timer.start()
            return True

    def close(self):
        with self.lock:
            self.closed=True;self._release()
