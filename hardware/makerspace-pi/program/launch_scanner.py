#!/usr/bin/env python3
"""Open the optional setup/status window. The boot service keeps scanning separately."""
from pathlib import Path
import os,sys
os.execv('/usr/bin/python3',['/usr/bin/python3',str(Path(__file__).with_name('scanner_gui.py'))])
