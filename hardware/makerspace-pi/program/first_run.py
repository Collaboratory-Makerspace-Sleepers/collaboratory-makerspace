#!/usr/bin/env python3
"""One entry point for a fresh Pi, updates and optional repair. Standard library only."""
import argparse,json,os,pwd,subprocess,sys
from pathlib import Path
from release import VERSION
ROOT=Path(__file__).resolve().parent
INSTALLED=Path('/opt/collaboratory-scanner')
def needs_install(marker,username):
    try:data=json.loads(marker.read_text())
    except (OSError,ValueError):return True
    return data.get('version')!=VERSION or data.get('user')!=username
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--repair',action='store_true');args=parser.parse_args()
    if sys.platform!='linux' or not Path('/etc/os-release').exists():raise SystemExit('Run this folder on Raspberry Pi OS, not your Windows computer.')
    if os.geteuid()==0:raise SystemExit('Run Start-Scanner.sh as your normal Pi user. It asks for your password when needed.')
    username=pwd.getpwuid(os.getuid()).pw_name
    print('\nCOLLABORATORY SCANNER SETUP\n',flush=True)
    if args.repair or needs_install(INSTALLED/'installed.json',username) or not (INSTALLED/'scanner_service.py').exists():
        print('1/3 Installing requirements and automatic startup. Internet is needed.\nEnter your Pi password if asked. This can take several minutes.\n',flush=True)
        subprocess.run(['bash',str(ROOT/'install.sh')],check=True)
    else:print('1/3 Scanner software is already installed.',flush=True)
    if subprocess.run(['systemctl','is-enabled','--quiet','collaboratory-scanner.service']).returncode:
        subprocess.run(['sudo','systemctl','enable','collaboratory-scanner.service'],check=True)
    if subprocess.run(['systemctl','is-active','--quiet','collaboratory-scanner.service']).returncode:
        subprocess.run(['sudo','systemctl','start','collaboratory-scanner.service'],check=True)
    print('\n2/3 Checking the USB reader and scanner service...\n',flush=True)
    subprocess.run(['/usr/bin/python3',str(INSTALLED/'diagnostics.py')],check=False)
    print('\n3/3 Connect once. After pairing, scanning starts automatically at every power-on.\n',flush=True)
    if os.environ.get('DISPLAY') or os.environ.get('WAYLAND_DISPLAY'):
        subprocess.run(['/usr/bin/python3',str(INSTALLED/'launch_scanner.py')],check=True)
    else:
        print('No desktop detected. Use this command to pair from SSH / Pi OS Lite:\npython3 /opt/collaboratory-scanner/scannerctl.py pair\nOr add --url https://YOUR-BACKEND for hosted operation.')
if __name__=='__main__':
    try:main()
    except subprocess.CalledProcessError as exc:raise SystemExit('Setup stopped at a failed command. Read the specific error above, then run Start-Scanner.sh again after it is resolved. Saved pairing is retained.') from exc
