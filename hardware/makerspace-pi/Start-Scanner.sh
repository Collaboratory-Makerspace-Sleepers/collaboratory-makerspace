#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
if [[ -d program ]]; then cd program; fi
trap 'code=$?; if [ "$code" -ne 0 ]; then printf "\nSetup stopped. Read the message above.\n"; if [ -t 0 ]; then read -r -p "Press Enter to close..." || true; fi; fi' EXIT
if ! command -v python3 >/dev/null; then
  echo 'Python 3 is missing. Use Raspberry Pi OS, or install python3 first.'; exit 1
fi
python3 first_run.py "$@"
