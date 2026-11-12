# Advanced scanner reference

For first-time setup, read ../START_HERE.md and run Start Scanner Setup in the outer
makerspace-pi folder. The files in this program folder are installed automatically.

## Pair without a desktop (SSH or terminal)

For local discovery:

```bash
python3 /opt/collaboratory-scanner/scannerctl.py pair
```

If discovery is blocked, specify the computer's actual IPv4 address:

```bash
python3 /opt/collaboratory-scanner/scannerctl.py pair --host 192.168.1.46
```

That address is only an example; check the current computer address. Compare the
verification code, then enter the eight-digit code at the hidden prompt.

For the hosted backend, replace the example with the address supplied by your team:

```bash
python3 /opt/collaboratory-scanner/scannerctl.py pair --url https://your-api.example
```

No database/RDS URL, AWS credential, SSH tunnel or inbound Pi port is needed. Pairing
is bound to the backend identity. A different database requires pairing there; it
does not automatically inherit local members or cards.

## Local computer versus hosted operation

- **Local test:** the computer runs the backend and must remain awake. Pi and computer
  use the same trusted Wi-Fi/hotspot. Headless Pi operation does not replace a backend.
- **Hosted:** the Pi talks directly to the team's HTTPS backend. The admin computer
  can be switched off. This requires the team to deploy the included hardware changes.
- **Offline:** no access approval or queued unlock. Use staff/manual check-in. An already
  authorized pulse completes on its local timer. Events upload after reconnection.

## Assign a card and grant exceptions

Users → create/open user → Assign card → tap. One online Pi is selected automatically;
with several online Pis select one. Enrollment stays red and never opens a door.
Remove and re-tap for access. Approved feedback is green then red; denied feedback is
amber then red. A held card is not continuously submitted.

Administrators can choose Door access override on the user: selected doors or all
doors, a reason, expiration or no expiration, and Revoke. It does not alter waiver,
payment or training records. Disabled cards/Pis, deleted accounts and maintenance
still block access. All-doors exceptions exclude equipment stations.

## Service controls

```bash
# Optional status, including current mode, reader and pending event count
python3 /opt/collaboratory-scanner/scannerctl.py status
# Temporary pause/resume; check status for acknowledgement
python3 /opt/collaboratory-scanner/scannerctl.py pause
python3 /opt/collaboratory-scanner/scannerctl.py resume
# Service restart and recent diagnostics
sudo systemctl restart collaboratory-scanner
journalctl -u collaboratory-scanner -n 60 --no-pager
# Stop it until manually started, or disable future boot startup
sudo systemctl stop collaboratory-scanner
sudo systemctl disable collaboratory-scanner
```

The service restarts after failure, and a watchdog requests restart if it stops
responding. It waits for network recovery and retries reader connection. Boot/restart
resumes normal scanning; a temporary pause is not persisted across restarts. UID-only
capture automatically returns to normal scanning after two minutes. To keep scanning
off across reboots, stop **and** disable the service. Closing the GUI is not a stop.

The service is the only USB/GPIO owner. Do not run an older scanner or pcsc_scan beside
it. To use pcsc_scan for diagnostics, stop the service, run the diagnostic, close it,
then start the service. The installer grants PC/SC access only to the selected Pi user
and ACR122 readers so no graphical authorization prompt is needed at the door.

## Hardware event history

Admin → Hardware Access → Hardware events contains searchable, paginated events with
Pi/area, member/UID/message, result, event type and date filters, plus CSV export.
Exports include up to the newest 10,000 matching records; narrow the date range if
needed. Backend scan decisions and Pi reports are explicitly distinguished.

The Pi records card detections, UID-only captures, transport failures, reader status/
errors, connection changes, service/config/mode changes, LED feedback, and door-control
commands. Repeated identical reader notices are coalesced; it does not log every empty
reader poll. A power loss cannot log its own shutdown. The next boot is recorded.

`events.sqlite3` is a private local outbox with up to 10,000 pending records. Uploads
are acknowledged and deduplicated. Successful uploads are removed locally. If full,
the oldest pending record is discarded and a QUEUE_GAP count is reported after space
becomes available. This is bounded storage, not a guarantee of unlimited offline logs.
Events contain timestamps, card UIDs and details; keep the SD card/data private.

Events stay bound to their original backend identity and Pi registration. Unpaired
startup events or records belonging to a deleted/recreated registration remain local
and are not sent as another Pi. `scannerctl.py events` shows total queued records and
overflow counts. Old scan history remains in Card taps; the new event table starts
collecting after this update. Door reports describe commands, not physical position.
The runtime status file keeps a short member display history under private `/run`,
which is cleared on reboot; it is not a member database.

## Optional door hardware

Leave GPIO off until a compatible fail-safe lock, relay/driver and separate supply
have been selected and checked. Default software pin is **BCM17 / physical pin 11**;
this is a 3.3 V signal, never lock/relay-coil power. Both admin door permission and the
Pi's Optional door hardware setting must be enabled. Unlock duration is 1–30 seconds.
Logical active requests locked; inactive requests released. Denials never pulse.
No physical door feedback sensor is implemented.

The control arrangement must release safely without controller/lock power and allow
independent emergency exit. Wi-Fi loss does not initiate unlocking. Service stop/crash
cleanup releases the output when cleanup runs; abrupt power loss relies on hardware.
A frozen process/relay fault needs hardware protection. See WIRING_OVERVIEW.svg: it is
a conceptual diagram, not instructions for a specific relay or lock.

For a software-only demonstration (no GPIO, no network, no lock):

```bash
python3 /opt/collaboratory-scanner/simulate_door.py
```

The GUI also offers Run simulation inside Optional door hardware. This does not
prove wiring, supply ratings, physical relocking or power-loss release.

## Troubleshooting

- Not paired: use Connect or scannerctl.py pair. Do not delete saved configuration.
- Service not starting: check systemctl status and journalctl above. Close the old
  scanner if the reader lock is held. Run `bash Start-Scanner.sh --repair` from the outer folder if files were missing.
- No reader: confirm ACR122U USB connection and pcscd.socket status; unplug/reconnect.
- Reader authorization error: run `bash Start-Scanner.sh --repair` from the outer folder for the correct normal Pi user; check
  `/etc/polkit-1/rules.d/49-collaboratory-scanner.rules` and pcscd logs.
- Local computer not found: launcher running, same network, Windows Private profile;
  some campus/guest networks block device-to-device traffic. Try a permitted hotspot.
- Pending logs never clear: update all three packages. The gateway must allow the
  new `/api/v1/hardware/device/events` route. Logs cannot upload to a revoked registration.
- Hosted TLS failure: verify hostname, Pi clock and CA certificates; do not bypass TLS.
- Stop/start troubleshooting is not an emergency-exit mechanism for a physical door.

See TEST_RESULTS.md for software checks and required real-Pi tests.
