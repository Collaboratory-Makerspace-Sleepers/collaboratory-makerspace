# Start here — Raspberry Pi scanner

Use this with a Raspberry Pi 4 and ACS ACR122U USB reader.
Package version: 2026.11.16.1 (Raspberry Pi OS Bookworm/Trixie).
The Pi reads cards. The Makerspace backend stores members and decides access.

## 1. Set up a new Pi

If your Pi already runs Raspberry Pi OS, skip to step 2. You do not need to reflash it.

For a newly flashed Pi, use Raspberry Pi Imager on your computer. Select Raspberry
Pi 4 and **Raspberry Pi OS (64-bit) with a desktop** for the graphical setup below.
Set your own username, password, time zone and Wi-Fi. Boot the Pi and finish its
first-start screens. Confirm it can open a website before installing the scanner.

## 2. Put this folder on the Pi

Download Makerspace-Pi-Scanner.zip **on the Pi**, or transfer it there with a USB drive.
Extract it onto the Desktop. Open the extracted **makerspace-pi** folder.
Keep its **program** folder in place. Plug the ACR122U into a USB port on the Pi.
Close any old scanner window or pcsc_scan program.

## 3. Run setup

Double-click **Start Scanner Setup** (the Start-Scanner.desktop file).
If the desktop asks, choose **Execute / Allow launching**. If it opens as text or
will not launch, use this reliable alternative:

1. Right-click inside the extracted folder and choose **Open in Terminal**.
2. Enter this one command:

   ```bash
   bash Start-Scanner.sh
   ```

Setup installs the reader software, Python requirements, card service and automatic
startup. Enter your **Pi password** if asked. Password typing may not show characters.
Keep internet connected and wait for setup to finish. The first install can take
several minutes. Do not close the terminal while it is installing.

Setup then checks for the reader and opens the scanner window. Running the same
start file later opens the installed app without reinstalling everything.

## 4. Connect once

1. Ask your backend team for the deployed HTTPS backend address.
2. In the website, open **Admin → Hardware Access → Pis & areas → Register Pi**.
   Give it a name and area, such as Main entrance. Leave equipment unbound for
   general entry and leave door output off.
3. In the Pi window, choose **Connect → Hosted backend** and enter that address.
   Enter the **eight-digit pairing code** from admin. This is not a sign-in code.
4. Tap a card. An unassigned card should show **Denied**, not Manual check-in.
5. Open/create the member in admin → **Assign card** → select the Pi → tap.
   Remove the card and tap again to check access. Pre-registered accounts can
   receive cards, but must activate their account before entry is approved.

Each Pi needs its own registration. Existing credentials remain usable with the
same paired-device backend/database. Pair again if you switch backends or move
from the older scanner_devices implementation to this hardware_devices version.

### Cards to order and verify

The ACR122U reads **13.56 MHz contactless cards**; it does not read 125 kHz
proximity cards, magnetic stripes, or contact-only chip cards. For purchasing,
start with a sample 13.56 MHz ISO/IEC 14443 Type A card (such as MIFARE Classic
or Ultralight), then verify the exact SKU using the supplied reader and scanner
before ordering in bulk. The app uses the UID returned by the reader; use
cards that return the same UID on repeated presentations. Do not treat
manufacturer protocol support as proof that a particular badge works.

The seven local card variants are not identified in this repository. For each
variant, record its vendor/model, successful UID reads over five separate
presentations, and a successful assigned-member tap. Mark any untested or
failed variant as not approved for deployment.

### Manage the Pi from the website

**Register Pi** creates a record and a pairing code; it does not install software
remotely. Use steps 2 and 3 first. After pairing, **Connected/Offline** reports
heartbeat freshness; **Enabled/Disabled** is the admin setting. Use **Edit →
Device enabled → Save changes** to change it without revoking pairing. Use
**Assign next tap** on a connected, enabled Pi to select a member and arm that
reader. Confirm the result in **Recent device events** and **Cards**; the
assignment tap is not an entry approval, so remove and tap the card again.

**Local testing:** if your team is running the backend locally, use an existing
local TLS gateway and **Find computer**, or the SSH tunnel described in the app's
HARDWARE_SETUP.md. A local gateway is not included in this folder and is not needed
for AWS. Keep that development backend/gateway running during the test.

## 5. Daily use

After the first setup, **just power on the Pi**. Scanning starts without signing in,
opening a window or connecting a monitor. Closing the scanner window does not stop it.
The desktop Collaboratory Scanner shortcut opens the optional status/settings window.

During local testing, your computer and backend must stay running. With a deployed
HTTPS backend, the Pi can work with the admin computer switched off.

When changing Wi-Fi, first connect both devices to the new network. Local mode looks
for the same saved computer again. Guest/campus networks may block device discovery;
use a permitted hotspot or the manual computer-address option if needed.

## If something is wrong

| What you see | What to do |
|---|---|
| Reader error / no reader | Choose **Help & settings → Check reader and connection**. Close old scanner apps, unplug/replug USB, then tap again. |
| Manual check-in | Read the displayed reason. It can mean the backend is unavailable or returned an error; it is not an access approval. |
| Computer not found | Start the Windows launcher; check both devices' Wi-Fi. |
| Pairing rejected | Generate a new Pi pairing code in admin, then Connect on the Pi. |
| Missing software / setup failed | Open this folder in Terminal and run `bash Start-Scanner.sh --repair`. |

**Hardware events** refresh automatically. If the Pi stops contacting the running
backend, a **Connection lost** entry appears after about 45–55 seconds. It cannot
prove whether power, Wi-Fi or the scanner service failed. Reconnection is logged too.
A powered-off backend cannot monitor Pis while it is off; very short outages may not
cross the timeout. Pi startup and queued reports appear when connection is restored.

### Busy-entry acceptance check

Present **one card at a time** and remove it before the next tap. The scanner
waits for each backend decision and then pauses 0.9 seconds after approval or
1.6 seconds otherwise, before polling again. Network latency and reader feedback
add time, so these pauses are not a throughput guarantee. Do not expect
overlapping taps or stacked cards to be processed as separate visits.

Before opening a high-traffic entrance, turn door output off and time at least
30 successive taps using several test cards, including denied taps. Record
tap-to-feedback time and check server-side device events for missed or duplicate
decisions. Compare the sustained rate with the site's expected peak; if one
reader is not enough, test independent lanes with a Pi/reader per lane.

For an outage, use staffed manual check-in; never treat an offline tap as
approved. Check Pi power, USB, and Wi-Fi, then use the scanner diagnostics and
compare **Last seen** with **Recent device events**. After recovery, verify
one known allowed and one denied test tap before returning to normal use.

## Updating

Close the old scanner window, extract the newest Pi ZIP into a new folder and run
**Start Scanner Setup** again. It replaces the installed program and keeps pairing.
Use the updated Makerspace app too for connection monitoring and deleted-card repairs.
You do not need to delete your database or reflash the Pi.

For SSH/Pi OS Lite, detailed controls and door options, see **program/ADVANCED.md**.
The demonstration checklist, test notes and conceptual wiring diagram are also in
**program**. GPIO stays off until suitable lock hardware is selected and tested.

Official Pi OS setup reference:
https://www.raspberrypi.com/documentation/computers/getting-started.html
