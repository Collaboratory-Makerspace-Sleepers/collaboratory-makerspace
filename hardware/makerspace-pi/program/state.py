"""Private configuration shared by the setup window and boot service."""
import json
import os
from pathlib import Path
DATA=Path.home()/'.config/collaboratory-scanner-v2'
CONFIG=DATA/'config.json'
KEY=DATA/'credentials.json'
CONTROL=DATA/'control.json'
STATUS=Path('/run/collaboratory-scanner/status.json')
DEFAULTS=dict(origin='http://127.0.0.1:18080',connection_mode='local',reader_match='ACR122',
    led_enabled=True,buzzer_enabled=False,denial_beep_enabled=True,auto_start=True,
    tls_fingerprint='',server_id='',laptop_name='',door_gpio_enabled=False,
    door_gpio_pin=17,door_active_high=True)
def read(path,default=None):
    try:
        data=json.loads(path.read_text())
        if not isinstance(data,dict):raise ValueError('Expected an object')
        return data
    except FileNotFoundError:return {} if default is None else default
def write_private(path,text):
    import tempfile
    path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    fd,name=tempfile.mkstemp(prefix=path.name+'.',dir=path.parent)
    try:
        with os.fdopen(fd,'w') as f:f.write(text);f.flush();os.fsync(f.fileno())
        os.replace(name,path)
    finally:
        if os.path.exists(name):os.unlink(name)
