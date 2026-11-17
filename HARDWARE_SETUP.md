# Pi scanner integration — frontendAdmin

Based on frontendAdmin commit `afe0e5d34137eb08910af353404e13ffe99b9aa7` (November 15, 2026, Eastern time). This is a complete source folder, with the team's existing application retained. Nothing has been pushed to GitHub.

## Put this into the repository

1. In GitHub Desktop, select the repository and **frontendAdmin**. Fetch/pull first.
2. Extract this ZIP. Copy the **contents** of `collaboratory-makerspace` into your repository folder, replacing matching source files. Do not put a second repository folder inside the first.
3. Keep your local `.env`, database configuration, credentials and `.git` folder. None are supplied in this ZIP.
4. Review the changed files in GitHub Desktop, commit and push to **frontendAdmin**. If the branch has newer changes than the base commit above, merge those changes rather than overwriting them with this older snapshot.

If using GitHub's upload page, upload the extracted source files and folders, **not the ZIP itself**. GitHub Desktop is easier for a complete repository.

## What changed

- Deleting a member releases their card UIDs, cancels pending assignment and revokes their door exceptions. The migration also repairs assignments left behind by earlier deletions.
- Scan history is retained. Deleted-member scans keep the original numeric member ID without trying to load the deleted account.
- Assigning a disabled card no longer enables it. Use the explicit Enable card action.
- Create a member, then click **Assign card** in that same dialog. Existing members have the same control in their profile. Choose the Pi, click Assign card, then tap. No UID typing is needed.
- A pre-registered member can receive a card before signing in, but cannot enter until their account is active. Card assignment does not activate the account or grant access.
- Staff with both `MANAGE_USERS` and `MANAGE_EQUIPMENT` can grant a temporary general-entry override for a selected Pi, with a required reason and expiry (1 minute to 7 days). It can be revoked immediately.
- Override use is recorded in the card-scan log and hardware event log. Grant/revoke records retain staff IDs, reason and timestamps in `hardware_door_overrides`.

An override bypasses general-entry membership checks. It does not change payment, waiver or training records; it does not enable a disabled card or Pi, activate an account, or bypass equipment restrictions. Only an authenticated paired Pi can use it. A member must tap again after assignment; enrollment never opens a door.

The current branch's existing access rules remain authoritative. In particular, general entry uses facility membership/day-pass access; equipment scans also use the existing training/waiver checks. This update does not introduce a new facility-wide waiver rule or change billing expiration rules.

## Backend and AWS

Use the team's existing backend/frontend startup or deployment process from README.md. No personal-computer launcher is required on AWS.

1. Back up the database. Deploy the backend and frontend from this same source version.
2. Keep Flyway enabled and Hibernate schema mode `validate`. This branch adds **V27__hardware_card_lifecycle_and_overrides.sql**, after the branch's existing V26 paired-device migration. Do not edit previously applied migrations, reset the database or use Flyway repair to bypass a conflict.
3. Before merging into **main**, the backend team must reconcile migration versions: main may already have its own V26/V27. Keep both sets of changes and assign unapplied migrations a non-conflicting order. This ZIP targets frontendAdmin's migration history, not an arbitrary main database.
4. Supply a stable `APP_JWT_SECRET`, database and other existing service secrets through the deployment's secret configuration. Deploy a public HTTPS backend origin with a trusted certificate, such as `https://api.your-domain.example`.
5. Route `/api/v1/hardware/*` to Spring Boot, not the frontend HTML fallback. The proxy must forward `X-Device-Id` and `X-Device-Key`. The Pi has no browser login/cookies. Its ingress is authenticated by its device key.
6. Verify `GET /api/v1/hardware/device/protocol` returns `{"protocol":"makerspace-device-v1","version":1}`. Do not put a website-login redirect in front of Pi routes. Leave the application's per-route authentication in place.
7. In Admin → Hardware Access → Pis & areas, register a Pi. Enter its name and area; leave equipment unbound for a general entrance. Leave door output off until hardware is wired and verified.
8. On the Pi, use **Connect → Hosted backend**, enter the HTTPS origin and the eight-digit pairing code. Each Pi needs its own registration and code. Do not enter an RDS/database URL, AWS keys, or the website sign-in code.

Pairing codes expire in ten minutes and work once. Saved credentials survive Pi reboots. Regenerating a pairing code immediately revokes old credentials. Changing backend/database or migrating from the older `scanner_devices` implementation requires registering and pairing again; this code uses `hardware_devices` and does not import old private keys.

Use `app.hardware.legacy-device-key-enabled=false` when no old shared-key readers remain. This does not disable paired Pis.

## Pi setup

The matching scanner is included under **hardware/makerspace-pi**. Copy that folder to the Pi's Desktop. Open a terminal in it and run:

```bash
bash Start-Scanner.sh
```

See **hardware/makerspace-pi/START_HERE.md** for newly flashed Pi instructions. Setup installs reader dependencies and the boot service. The scanner runs headless at power-on after setup and pairing; its window is optional. The ACR122U connects over USB.

## Local testing

Run the app with the team's existing configuration and a separate development database. You can retain your existing local discovery/TLS gateway if it forwards to this backend. The gateway launcher is a separate local-testing component, not required by AWS and not included here. Older launcher database/version checks may not recognize this branch; do not bypass them to reuse an incompatible database.

For developers without a gateway, SSH port forwarding is another option. From the Pi, with Windows OpenSSH Server configured on the development computer:

```bash
ssh -N -L 127.0.0.1:18080:127.0.0.1:8080 YOUR_USERNAME@COMPUTER_IP
```

Leave that connection open. In another Pi terminal:

```bash
python3 /opt/collaboratory-scanner/scannerctl.py pair --url http://127.0.0.1:18080
```

Substitute the actual backend port if it is not 8080. Loopback HTTP is accepted for this encrypted SSH tunnel; arbitrary HTTP LAN addresses are deliberately rejected. Hosted HTTPS requires neither SSH nor a local gateway.

## Logs, disconnection and door output

Pis send heartbeats and queue telemetry for retry. The backend records loss of contact after approximately 45–55 seconds and restoration on contact. Power failure cannot be directly reported by a powered-off Pi: the event means no contact, not proof of a particular cause. Unknown cards are denied and logged. API/network errors result in manual check-in, never an offline door approval or replayed unlock.

Door output defaults off on both sides. The Pi requires its local GPIO configuration as well as backend permission; a fresh approved scan may produce a bounded pulse (1–30 seconds). GPIO controls a suitable relay/driver, **not the lock's power supply**. Fail-unlocked behavior must be provided by the lock and electrical design, not a software promise. See the included Pi wiring reference before any physical connection.

## Before the demonstration

Check a registered card, an unknown card, a disabled card, a deleted member's old card, next-tap assignment, override expiry/revocation, Pi power loss/reconnection, and boot without a signed-in desktop. Confirm that a different Pi cannot capture the selected Pi's assignment. Physical Pi/reader/door operation and the team's AWS deployment must still be tested on site; software checks do not establish those outcomes.
