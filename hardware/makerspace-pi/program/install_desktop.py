#!/usr/bin/env python3
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parent
try:
    desktop=Path(subprocess.check_output(['xdg-user-dir','DESKTOP'],text=True).strip())
except (OSError,subprocess.CalledProcessError): desktop=Path.home()/'Desktop'
def entry(path):
    path.parent.mkdir(parents=True,exist_ok=True)
    script=str(root/'launch_scanner.py').replace('\\','\\\\').replace('"','\\"').replace('`','\\`').replace('$','\\$').replace('%','%%')
    path.write_text('[Desktop Entry]\nType=Application\nName=Collaboratory Scanner\nComment=Configure and monitor the background scanner service\nExec=/usr/bin/python3 "'+script+'"\nIcon=dialog-password\nTerminal=false\nCategories=Utility;\nX-GNOME-Autostart-enabled=true\n')
    path.chmod(0o755)
entry(desktop/'Collaboratory-Scanner.desktop')
(Path.home()/'.config/autostart/Collaboratory-Scanner.desktop').unlink(missing_ok=True)
print('Created optional setup/status shortcut. Scanning starts through the system service.')
