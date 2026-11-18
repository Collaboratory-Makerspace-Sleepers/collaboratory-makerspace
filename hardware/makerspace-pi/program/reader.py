"""ACR122U PC/SC adapter, reused from the tested scanner hardware implementation."""
import time
import re

def safe(value):
    return ''.join(c for c in str(value) if c.isprintable())[:500]

def uid_text(value):
    value = str(value).strip().upper()
    if not re.fullmatch(r'(?:[0-9A-F]{2}){4,10}', value):
        raise ValueError('UID must be 4 to 10 bytes of hexadecimal.')
    return value

def led_command(color):
    # ACS API 2.03 section 6.2: bits 2/3 update both LEDs, bits 0/1 set their state.
    return [0xFF, 0, 0x40, {'off': 0x0C, 'red': 0x0D, 'green': 0x0E, 'amber': 0x0F}[color], 4, 0, 0, 0, 0]


def result_led_command(allowed, beep=False):
    # Approval: one green pulse. Denial: three amber/off flashes, 2 Hz.
    # Firmware timers return to RED. Beeper is active only in each T1 phase.
    return [0xFF, 0, 0x40, 0xED if allowed else 0xFD, 4,
            8 if allowed else 3, 0 if allowed else 2, 1 if allowed else 3, 1 if beep else 0]


class Hardware:
    def __init__(self, cfg, notify=None):
        try:
            from smartcard.System import readers
            from smartcard.Exceptions import NoCardException, CardConnectionException
        except ImportError:
            raise RuntimeError('Missing pyscard. Run: sudo apt install python3-pyscard pcscd libccid')
        self.readers = readers
        self.no_card = NoCardException
        self.connection_error = CardConnectionException
        self.cfg = cfg
        self.conn = None
        self.name = None
        self.last_uid = None
        self.last_message = ''
        self.notify = notify

    def message(self, text):
        if self.last_message != text:
            print(text)
            self.last_message = text
            if getattr(self, 'notify', None):
                self.notify(text)

    def close(self):
        if self.conn:
            try:
                self.conn.disconnect()
            except Exception:
                pass
        self.conn = None

    def poll(self):
        try:
            if self.conn is None:
                candidates = [r for r in self.readers() if self.cfg['reader_match'].lower() in str(r).lower()]
                if len(candidates) != 1:
                    self.last_uid = None
                    self.message('No matching USB reader. Plug in the ACR122U.' if not candidates else 'More than one reader found. Connect one ACR122U or select its name in Advanced settings.')
                    return None
                self.name = str(candidates[0])
                self.conn = candidates[0].createConnection()
                self.conn.connect()
                self.feedback('red')
            data, sw1, sw2 = self.conn.transmit([0xFF, 0xCA, 0, 0, 0])
            if (sw1, sw2) != (0x90, 0) or not data:
                message = f'UID read failed: {sw1:02X} {sw2:02X}. Remove card, then present one card flat.'
                if self.last_message != message:
                    self.feedback('red')
                    self.message(message)
                return None
            uid = uid_text(''.join(f'{b:02X}' for b in data))
            self.last_message = ''
            if uid == self.last_uid:
                return None
            self.last_uid = uid
            return uid
        except self.no_card:
            self.last_uid = None
            self.close()
            self.message('Reader ready — tap a card.')
            return None
        except Exception as exc:
            self.message(f'Reader error ({type(exc).__name__}): {safe(exc)}. Close old scanner apps; retrying automatically.')
            self.close()
            return None

    def feedback(self, color, beep=False):
        if not self.conn or not self.cfg['led_enabled']:
            return
        try:
            _, sw1, sw2 = self.conn.transmit(led_command(color))
            if sw1 != 0x90:
                raise RuntimeError(f'{sw1:02X} {sw2:02X}')
            if beep and self.cfg['buzzer_enabled']:
                _, sw1, sw2 = self.conn.transmit([0xFF, 0, 0x40, 0, 4, 1, 1, 1, 1])
                if sw1 != 0x90:
                    raise RuntimeError(f'{sw1:02X} {sw2:02X}')
        except Exception as exc:
            self.message('LED/buzzer command failed: ' + safe(exc) + '. Use the displayed result.')

    def result_feedback(self, allowed, uid=None):
        if not self.conn or not self.cfg['led_enabled']:
            return False
        try:
            if uid is not None:
                data, sw1, sw2 = self.conn.transmit([0xFF, 0xCA, 0, 0, 0])
                current = ''.join(f'{b:02X}' for b in data)
                if (sw1, sw2) != (0x90, 0) or current != uid:
                    raise RuntimeError('Card removed or changed before feedback')
            self.feedback('red')
            time.sleep(.15)
            beep = self.cfg['buzzer_enabled'] if allowed else self.cfg.get('denial_beep_enabled', True)
            _, sw1, sw2 = self.conn.transmit(result_led_command(allowed, beep))
            if sw1 != 0x90:
                raise RuntimeError(f'{sw1:02X} {sw2:02X}')
            return True
        except Exception as exc:
            self.message('Result LED failed: ' + safe(exc) + '. Use the on-screen decision.')
            self.feedback('red')
            return False
