"""Reader worker shared by the headless service. No graphical dependencies."""
import threading
import time
from reader import Hardware
from api import checkin
from event_queue import binding

class Worker(threading.Thread):
    def __init__(self, events, door=None, audit=None):
        super().__init__(daemon=True)
        self.events = events
        self.door = door
        self.audit = audit or (lambda *args, **kwargs: None)
        self.progress = time.monotonic()
        self.lock = threading.Lock()
        self.desired = (0, 'paused', {}, '')
        self.stop_event = threading.Event()

    def configure(self, epoch, mode, cfg, key):
        with self.lock:
            self.desired = (epoch, mode, dict(cfg), key)

    def snapshot(self):
        with self.lock:
            return self.desired

    def current(self, epoch):
        return not self.stop_event.is_set() and self.snapshot()[0] == epoch

    def run(self):
        hw, active = None, None
        try:
            while not self.stop_event.is_set():
                self.progress = time.monotonic()
                epoch, mode, cfg, key = self.snapshot()
                def audit(kind,detail='',**fields):
                    self.audit(kind,detail,scope=binding(cfg,key or {}),**fields)
                if active != epoch:
                    if hw:
                        hw.feedback('red'); hw.close()
                    hw, active = None, epoch
                if mode == 'paused':
                    self.stop_event.wait(.1); continue
                try:
                    if hw is None:
                        hw = Hardware(cfg, lambda msg, e=epoch: self.events.put((e, 'notice', msg)))
                    uid = hw.poll()
                    if not uid:
                        self.stop_event.wait(.15); continue
                    audit('CARD_TAP','Card detected',uid=uid)
                    hw.feedback('red')
                    self.events.put((epoch, 'reading', uid))
                    if mode == 'uid':
                        audit('UID_CAPTURE','UID read without an access request',uid=uid)
                        self.events.put((epoch, 'uid', uid)); continue
                    result = checkin(uid, cfg['origin'], key, cfg.get('tls_fingerprint') or None)
                    if result.get('error'):audit('SCAN_TRANSPORT_ERROR',result['reason'],uid=uid,error=True)
                    # Pausing or changing settings invalidates in-flight results.
                    with self.lock:
                        if self.stop_event.is_set() or self.desired[0] != epoch:
                            continue
                        if self.door:
                            try: self.door.approved(result)
                            except Exception:
                                audit('DOOR_ERROR','Door command failed; inspect driver',uid=uid,error=True)
                                self.events.put((epoch,'notice','Door output failed. Ask staff to check the relay.'))
                    self.events.put((epoch, 'decision', (uid, result)))
                    if result.get('enrollment'):
                        hw.feedback('red')  # Assignment is neither approval nor an unlock.
                    else:
                        if hw.result_feedback(result['granted'], uid):
                            audit('LED_FEEDBACK','Approved feedback' if result['granted'] else 'Denied feedback',uid=uid,scan_id=result.get('scanId'))
                    self.stop_event.wait(.9 if result['granted'] else 1.6)
                except Exception as exc:
                    audit('READER_ERROR',f'Reader unavailable ({type(exc).__name__})',error=True)
                    self.events.put((epoch, 'notice', f'Reader unavailable ({type(exc).__name__}). Use manual check-in.'))
                    if hw: hw.close()
                    hw = None
                    self.stop_event.wait(1)
        finally:
            if hw:
                hw.feedback('red'); hw.close()
