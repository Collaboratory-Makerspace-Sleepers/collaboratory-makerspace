#!/usr/bin/env python3
"""Install as root for the invoking normal Pi user. Does not reset saved pairing."""
import fcntl,grp,json,os,pwd,re,shutil,subprocess,sys
from pathlib import Path
from release import VERSION

def unit(username,home,group,has_gpio):
    quoted=lambda v:'"'+v.replace('\\','\\\\').replace('"','\\"').replace('%','%%')+'"'
    return f'''[Unit]
Description=Collaboratory headless card scanner
Wants=network-online.target pcscd.socket
After=network-online.target pcscd.socket
StartLimitIntervalSec=0
[Service]
Type=notify
NotifyAccess=main
User={username}
Group={group}
{('SupplementaryGroups=gpio' if has_gpio else '')}
Environment={quoted('HOME='+home)}
WorkingDirectory=/opt/collaboratory-scanner
ExecStart=/usr/bin/python3 /opt/collaboratory-scanner/scanner_service.py
Restart=on-failure
RestartSec=5
WatchdogSec=60
TimeoutStopSec=15
RuntimeDirectory=collaboratory-scanner
RuntimeDirectoryMode=0700
UMask=0077
NoNewPrivileges=true
[Install]
WantedBy=multi-user.target
'''
def main():
    if os.geteuid()!=0:raise SystemExit('Use bash install.sh as your normal Pi user.')
    user=pwd.getpwnam(sys.argv[1])
    if user.pw_uid==0 or not re.fullmatch(r'[a-zA-Z_][a-zA-Z0-9_-]*',user.pw_name):raise SystemExit('Select a normal Pi user')
    source=Path(__file__).resolve().parent;dest=Path('/opt/collaboratory-scanner')
    subprocess.run(['systemctl','stop','collaboratory-scanner.service'],check=False,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    old_lock=Path(user.pw_dir)/'.config/collaboratory-scanner-v2/scanner.lock'
    if old_lock.exists():
        with old_lock.open('r') as handle:
            try:fcntl.flock(handle,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:raise SystemExit('Close the old scanner window before running setup again.')
    dest.mkdir(exist_ok=True,mode=0o755)
    for path in source.iterdir():
        if path.is_file() and (path.suffix in ('.py','.sh','.md','.svg')) and not path.name.startswith('test_'):
            target=dest/path.name
            if path.resolve()!=target.resolve():shutil.copyfile(path,target)
            target.chmod(0o644)
    has_gpio=any(g.gr_name=='gpio' for g in grp.getgrall())
    Path('/etc/systemd/system/collaboratory-scanner.service').write_text(unit(user.pw_name,user.pw_dir,grp.getgrgid(user.pw_gid).gr_name,has_gpio))
    # Grant this service user access to PC/SC and the ACR122 reader while logged out.
    rule_dir=Path('/etc/polkit-1/rules.d');rule_dir.mkdir(parents=True,exist_ok=True)
    rule_dir.joinpath('49-collaboratory-scanner.rules').write_text('''polkit.addRule(function(action, subject) {
  if (subject.user !== USER) return;
  if (action.id === "org.debian.pcsc-lite.access_pcsc") return polkit.Result.YES;
  if (action.id === "org.debian.pcsc-lite.access_card" && String(action.lookup("reader")).indexOf("ACR122") >= 0) return polkit.Result.YES;
});
'''.replace('USER',json.dumps(user.pw_name)))
    subprocess.run(['systemctl','daemon-reload'],check=True)
    subprocess.run(['systemctl','enable','--now','collaboratory-scanner.service'],check=True)
    dest.joinpath('installed.json').write_text(json.dumps({'version':VERSION,'user':user.pw_name}))
    print('Installed headless service. Existing settings and pairing are retained.')
if __name__=='__main__':main()
