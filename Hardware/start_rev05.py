from pathlib import Path
import subprocess,sys
R=Path(__file__).resolve().parent;KP=r'C:\Program Files\KiCad\10.0\bin\python.exe'
for v in ['12V','USB']:
 subprocess.run([KP,str(R/'define_rev05.py'),v],check=True)
 for name in ['build_design.py','mechanical_finish.py']:subprocess.run([KP,str(R/('REV05-'+v)/name)],check=True)
