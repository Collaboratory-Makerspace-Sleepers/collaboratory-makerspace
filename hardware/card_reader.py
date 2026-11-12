"""Reads taps from an ACS ACR122U USB NFC reader and sends each card UID to the backend.

Setup (macOS or a Raspberry Pi):
    python3 -m venv .venv && .venv/bin/pip install pyscard
    .venv/bin/python card_reader.py

Environment variables:
    BACKEND_URL   default http://localhost:8080
    DEVICE_ID     default reader-1
    DEVICE_KEY    optional, sent as X-Device-Key when app.hardware.device-key is set
"""
import json
import os
import time
import urllib.error
import urllib.request

from smartcard.System import readers

BACKEND_URL = os.environ.get("BACKEND_URL", "http://localhost:8080").rstrip("/")
DEVICE_ID = os.environ.get("DEVICE_ID", "reader-1")
DEVICE_KEY = os.environ.get("DEVICE_KEY")
GET_UID = [0xFF, 0xCA, 0x00, 0x00, 0x00]


def read_uid(connection):
    data, sw1, sw2 = connection.transmit(GET_UID)
    if (sw1, sw2) != (0x90, 0x00):
        raise RuntimeError(f"Reader returned status {sw1:02X}{sw2:02X}")
    return "".join(f"{b:02X}" for b in data)


def send_scan(uid):
    headers = {"Content-Type": "application/json"}
    if DEVICE_KEY:
        headers["X-Device-Key"] = DEVICE_KEY
    request = urllib.request.Request(
        f"{BACKEND_URL}/api/v1/hardware/card-scan",
        data=json.dumps({"cardUid": uid, "deviceId": DEVICE_ID}).encode(),
        headers=headers,
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        return {"approved": False, "message": f"Backend error {error.code}"}
    except urllib.error.URLError as error:
        return {"approved": False, "message": f"Cannot reach backend: {error.reason}"}


def main():
    available = readers()
    if not available:
        raise SystemExit("No NFC reader found. Plug in the ACR122U and try again.")
    reader = available[0]
    print(f"Using {reader}. Sending taps to {BACKEND_URL}. Tap a card (Ctrl+C to stop).")
    last_uid = None
    while True:
        try:
            connection = reader.createConnection()
            connection.connect()
            uid = read_uid(connection)
            connection.disconnect()
        except Exception:
            last_uid = None
            time.sleep(0.2)
            continue
        if uid != last_uid:
            last_uid = uid
            result = send_scan(uid)
            verdict = "APPROVED" if result.get("approved") else "DENIED"
            who = result.get("memberName") or ""
            print(f"{uid}: {verdict} {who} - {result.get('message', '')}")
        time.sleep(0.3)


if __name__ == "__main__":
    main()
