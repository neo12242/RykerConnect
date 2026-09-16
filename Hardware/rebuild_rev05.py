"""Rebuild one variant using its reviewed SES. Re-routing requires renewed review."""
from pathlib import Path
import sys,subprocess
R=Path(__file__).resolve().parent;v=sys.argv[1];assert v in ['12V','USB'];d=R/('REV05-'+v);KP=r'C:\Program Files\KiCad\10.0\bin\python.exe'
subprocess.run([KP,str(R/'define_rev05.py'),v],check=True)
for name in ['build_design','mechanical_finish','route_board','import_routes']:subprocess.run([KP,str(d/(name+'.py'))],check=True)
for name in ['finish_rev05','ground_finish_rev05']:subprocess.run([KP,str(R/(name+'.py')),v],check=True)
subprocess.run([KP,str(d/'artwork_cleanup.py')],check=True)
for name in ['ground_adjust_rev05','final_adjust_rev05','rtc_ground_rev05','label_rev05']:subprocess.run([KP,str(R/(name+'.py')),v],check=True)
subprocess.run([sys.executable,str(R/'export_rev05.py'),v],check=True)
