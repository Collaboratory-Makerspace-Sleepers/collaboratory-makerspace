# Tests and remaining checks — easy setup and deletion fix — November 10, 2026

## Local hardware-only verification — November 12, 2026

Status: the backend is not yet paired with the physical Raspberry Pi / card reader.
Passing software tests do not confirm physical pairing or end-to-end operation.

Verified on macOS with Python 3.14:

```bash
python3 -m unittest discover -s hardware/makerspace-pi/program -p 'test_*.py'
python3 -m unittest discover -s hardware -p 'test_card_reader.py' -v
bash -n hardware/makerspace-pi/Start-Scanner.sh hardware/makerspace-pi/program/install.sh
```

All 66 Pi software tests and 8 standalone card-reader tests passed. Pi tests use
fake readers/GPIO, temporary configuration/outbox files and local HTTP/TLS servers.
Standalone tests cover UID leading zeroes, APDU errors, empty UIDs, scan payloads,
device-key headers, malformed responses, connection timeouts and held-card
debouncing. Invalid responses and network failures never produce approval.

Corrected the setup tests to use the packaged top-level launcher and simulate
Linux explicitly, without installing software or running system services on this
computer. Closed HTTP error responses to avoid leaking sockets during retries.

A read-only check of the running local backend's
`/api/v1/hardware/device/protocol` returned HTTP 401. The paired-Pi client requires
this public protocol check to succeed before sending credentials, so actual
pairing/scanning against this backend is not verified. No frontend, backend or
database changes were made. Use a matching backend release before Pi acceptance
testing; this result does not replace the physical checks below.

## Earlier release verification (not rerun in this hardware-only check)

Target: frontendAdmin at 158e818bdc0b4b1647efa947aa97fea514eae540.
No GitHub push, AWS deployment or production database changes were made.

## Passed software checks

- 58 Java tests: paired device authentication/management, card assignment, 18 door
  override cases and 13 hardware event cases. Tests include permissions, idempotency,
  expiry/revocation, selected/all/future doors, disabled cards/Pis, deleted users,
  equipment/maintenance hard stops, event source validation and CSV cell escaping.
- 66 Pi tests: HTTP/TLS and connection handling, reader/LED logic, door timer/cleanup,
  paused in-flight result rejection, headless service and persistent event outbox.
  A worker test simulates reader disconnection/recovery and scans without importing Tk.
  Offline telemetry tests verify retry acknowledgement, identity isolation, duplicate
  safety, queue overflow accounting, and use of the events endpoint only.
- 11 Windows launcher/gateway software tests. Python compile checks and bash installer
  syntax passed. The generated systemd unit passed systemd-analyze verify here.
- React production build and targeted lint passed for changed hardware/user components.
- React DOM tests: profile/new-user card assignment, single-Pi automatic selection,
  success/cancel, selected-door override, default expiry, revoke and ongoing all-doors
  grant. New event UI tests cover source labels, automatic refresh, filters, pagination and CSV download.
  These tests use mocked API responses; they are not visual-browser acceptance tests.
- Real Pi HTTP client → pinned TLS gateway → Java backend → PGlite SQL integration:
  pairing, multiple areas, UID/member assignment, unknown/disabled cards, overrides,
  enroll/cancel/conflict, Pi enable/disable/delete, revoked keys and retained history.
  Offline event upload, retry deduplication, backend/report source separation, authorized
  filtering and CSV export also passed. Enrollment produced no access approval/unlock.
- All SQL migrations through V26 applied in order to a fresh PGlite database.
- frontendAdmin patch application checked against the exact base commit above.
- Conceptual wiring SVG rendered and visually checked for legibility.

## What these checks do not prove

No physical Raspberry Pi, ACR122U, relay/lock, Windows/WSL/Wi-Fi system or AWS/RDS
service was available here. Headless startup, polkit/USB access while logged out,
real LED behavior and actual Wi-Fi discovery/recovery must be confirmed on your Pi.
The door simulation uses fake output/timer objects; it never energizes a GPIO.
GPIO remains off by default. Door commands do not prove physical door position.

Flyway startup was disabled in the PGlite integration harness because its connection
multiplexer stalls Flyway locking. Real PostgreSQL/Flyway migration and production
startup remain team acceptance checks. Test-only OTP/mail/Redis settings were used;
those do not replace production authentication or configuration.
The complete repository's unrelated tests are not claimed as passing. The original
wireless drop was not reproduced from user logs. No ESP32 firmware is included: this
implementation targets Raspberry Pi 4 with the USB ACR122U.

## Team acceptance checklist

1. Review/apply the frontendAdmin patch and run migrations against backed-up local
   PostgreSQL. Reconcile migration numbers separately when eventually merging main.
2. Start matched app/launcher, install the Pi update and pair once. Reboot the Pi and
   scan without any desktop login; close its setup window and confirm scans continue.
3. Create a user → Assign card → tap. Remove/re-tap for access. Test two Pis in distinct
   areas, unknown/disabled cards, assignment conflicts, expiry and manual override revoke.
4. Unplug/reconnect the reader, restart backend/Pi, interrupt/restore Wi-Fi and confirm
   event uploads resume once without duplicate rows or delayed access requests/unlocks.
5. Check searchable event history/CSV and authorization. Agree retention for backend
   events; the Pi outbox is bounded to 10,000 pending records, with gap reporting.
6. Run simulate_door.py. Physical wiring/power-loss/independent-exit acceptance must wait
   for selection of a suitable driver, fail-safe lock and supply.
7. Test hosted HTTPS separately. The admin computer may be off only when the deployed
   backend remains reachable. Local testing still needs the computer backend running.
8. Normal room reservation enforcement remains a pre-existing policy gap; no new room
   schedule rules were added. See HARDWARE_INTEGRATION.md and Pi DEMO_CHECKLIST.md.

## New regression checks

- Reproduced deleted-user card/list HTTP 500 before the fix using real API calls.
- V25 tested against stranded card/OTP fixtures. Other cards/accounts and both history
  tables remained. Trigger tests covered future deletion of active/disabled cards,
  account restore without restoring cards, and reuse of the released UID.
- Real API regression passed: DELETE user, list cards (200), scan released UID (normal
  unregistered denial plus scan row), retain history, tap-to-assign and approve for
  the new member. The original unrelated card still worked.
- Actual scheduled backend timeout test passed with no Pi messages: one backend lost
  connection event, then one restored event after authenticated contact. Unit tests
  also cover repeated ticks, recent contact and disabled-but-paired devices.
- First-run tests cover fresh/current/old installers, broken version marker, repeat
  launches without reinstalling, and the extracted folder path containing spaces.
  Reader mocks cover clearing stale errors on an empty reader, USB replug, preserving
  actual failure details, and held-card debounce. Desktop entry validation, shell
  syntax and Python compilation passed. Actual apt/systemd/desktop installation on
  Raspberry Pi OS remains a physical-device acceptance test.
- The Pi ZIP keeps implementation files in program/ and start files at its top level.
  Keep them together. Previous Windows launcher remains compatible without reinstall.

## Trixie installer correction — 2026.11.10.2

Replaced the removed policykit-1 transitional package with polkitd, which provides
the permission service used by the scanner and exists in Debian Bookworm and Trixie.
No pkexec command is used by this installer. Release marker bumped so re-running the
updated start file installs the corrected requirements. Setup failure text now refers
to the actual error instead of suggesting the password or internet must be wrong.
Shell syntax and the existing Pi test suite were checked again. Actual installation
on the user's Raspberry Pi still needs confirmation. Package references:
https://packages.debian.org/trixie/polkitd
https://packages.debian.org/bookworm/polkitd
