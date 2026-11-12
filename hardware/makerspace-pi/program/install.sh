#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ "$EUID" == 0 ]]; then echo 'Run as the normal Pi user: bash install.sh (not sudo bash).'; exit 1; fi
sudo apt update
# polkitd is available in both Bookworm and Trixie. The old policykit-1
# transitional package was removed in Trixie; pkexec is not used here.
sudo apt install -y python3-tk python3-pyscard python3-gpiozero pcscd libccid pcsc-tools polkitd ca-certificates
sudo systemctl enable --now pcscd.socket
sudo python3 install_service.py "$(id -un)"
# Use the stable installed path for the optional desktop shortcut.
python3 /opt/collaboratory-scanner/install_desktop.py
printf '\nScanner runs at boot without signing in. For setup: python3 /opt/collaboratory-scanner/launch_scanner.py\nFor status: systemctl status collaboratory-scanner --no-pager\n'
