"""Reproduce reviewed REV04 from retained inputs and fixed SES, without rerouting."""
from pathlib import Path
import subprocess,sys
R=Path(__file__).resolve().parent
KP=r'C:\Program Files\KiCad\10.0\bin\python.exe'
subprocess.run([sys.executable,str(R/'define_design.py')],check=True)
for name in ['build_design','mechanical_finish','route_board','import_routes','finish_board','artwork_cleanup']:
 subprocess.run([KP,str(R/(name+'.py'))],check=True)
for name in ['check_calculations','export_release','render_previews']:
 subprocess.run([sys.executable,str(R/(name+'.py'))],check=True)
