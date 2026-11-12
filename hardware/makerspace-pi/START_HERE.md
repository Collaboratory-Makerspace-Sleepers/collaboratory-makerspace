# Start here — Raspberry Pi scanner

Use this with a Raspberry Pi 4 and ACS ACR122U USB reader.
Installer version: 2026.11.10.2 (corrected for Raspberry Pi OS Trixie).
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

1. Start the Makerspace app on your computer with its Windows launcher.
2. In the website, open **Admin → Hardware Access → Pis & areas → Add a Pi**.
   Give it a name and area, such as Main entrance. Leave door output off.
3. In the Pi window, choose **Connect → Find computer**. Select your computer and
   compare its verification code with the admin page. Enter the **eight-digit Pi
   pairing code** from that page. This is different from your website sign-in code.
4. Tap a card. An unassigned card should show **Denied**, not Manual check-in.
5. To assign it: open/create a member in admin → **Assign card** → tap on that Pi.
   Remove the card and tap again to check access.

Each Pi gets its own registration and area. Do not copy pairing credentials between
Pis. For an AWS/hosted deployment, choose **Hosted backend** instead of Find computer
and enter the HTTPS address supplied by your team.

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
