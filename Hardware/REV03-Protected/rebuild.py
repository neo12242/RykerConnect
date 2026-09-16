"""Rebuild from preserved sources and the reviewed local routing session."""
from pathlib import Path
import subprocess,sys
R=Path(__file__).resolve().parent
KICAD=r'C:\Program Files\KiCad\10.0\bin\python.exe'
steps=[('build_schematic.py',sys.executable),('build_board.py',KICAD),('import_routing.py',KICAD),('finish_board.py',KICAD),('check_calculations.py',sys.executable),('export_release.py',sys.executable),('check_integrity.py',KICAD),('render_previews.py',sys.executable),('package_delivery.py',sys.executable)]
for script,python in steps:
 print('RUN',script,flush=True);subprocess.run([python,str(R/script)],check=True)
