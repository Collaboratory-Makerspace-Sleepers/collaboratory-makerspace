#!/usr/bin/env python3
"""Read-only setup checks. Does not take ownership of the reader or print secrets."""
import subprocess
from service_client import status
def report():
    lines=[]
    for unit,label in [('collaboratory-scanner','Scanner service'),('pcscd.socket','USB card service')]:
        try:
            result=subprocess.run(['systemctl','is-active',unit],capture_output=True,text=True,timeout=5)
            lines.append(label+': '+result.stdout.strip())
        except (OSError,subprocess.TimeoutExpired):lines.append(label+': could not check')
    try:
        from smartcard.System import readers
        found=[str(r) for r in readers()]
        matches=[r for r in found if 'ACR122' in r.upper()]
        if len(matches)==1:lines.append('USB reader detected: '+matches[0])
        elif not matches:lines.append('No ACR122U detected. Plug its USB cable directly into the Pi, then run this check again.')
        else:lines.append('Multiple readers detected. Use one ACR122U per Pi, or select its name in Advanced settings.')
    except ImportError:lines.append('Reader software is missing. Run: bash Start-Scanner.sh --repair')
    except Exception as exc:lines.append('PC/SC access error: '+str(exc)[:300]+'. Run setup again with --repair as the normal Pi user.')
    try:
        current=status();lines.extend(['Backend: '+current['connection'],'Reader: '+current['reader'],'Mode: '+current['mode']])
        if current.get('error'):lines.append(current['error'])
    except Exception:lines.append('Waiting for scanner startup. If this persists, run: bash Start-Scanner.sh --repair')
    lines.append('Close any old scanner or pcsc_scan before using this service. A detected reader alone does not prove a card can be read.')
    return '\n'.join(lines)
if __name__=='__main__':print(report())
