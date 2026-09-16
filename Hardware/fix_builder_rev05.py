from pathlib import Path
R=Path(__file__).resolve().parent
for v in ['12V','USB']:
 f=R/('REV05-'+v)/'build_design.py';s=f.read_text()
 s=s.replace("('+3.3V',1,365,210),",'')
 s=s.replace("if ref.startswith(('C','R','L','D')):","if ref.startswith(('C','R','L','D')) and not part.get('pins'):")
 f.write_text(s)
