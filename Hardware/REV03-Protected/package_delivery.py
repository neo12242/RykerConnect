"""Package this revision with verified, unchanged software from the REV02 delivery."""
from pathlib import Path
import json,hashlib,zipfile
R=Path(__file__).resolve().parent
report=json.loads((R/'validation/release-validation.json').read_text());assert report['drc']==report['erc']==report['unconnected']==report['schematic_parity']==0
assert json.loads((R/'validation/design-integrity.json').read_text())['outline_unchanged']
files={}
for f in R.rglob('*'):
 if not f.is_file() or any(x in f.relative_to(R).parts for x in ['.tools','sources','__pycache__']):continue
 if f.suffix in ['.log'] or f.name in ['placement-drc.json','routed-drc.json','protection.dsn','build-board.log','new-pad-positions.json']:continue
 files['Hardware/REV03-Protected/'+f.relative_to(R).as_posix()]=f.read_bytes()
files['README.md']=(R/'README.md').read_bytes()
files['Hardware/JLCPCB-REV02-review/validation.json']=(R.parent/'JLCPCB-REV02-review/validation.json').read_bytes()
# Include the small baseline required by the deterministic build scripts.
S=R.parent/'REV02-Corrected'
for name in ['RykerConnect.kicad_pcb','RykerConnect.kicad_sch','power.kicad_sch','RykerConnect.kicad_pro','RykerConnect.kicad_sym','selected-parts.json']:
 files['Hardware/REV02-Corrected/'+name]=(S/name).read_bytes()
with zipfile.ZipFile(R.parent/'RykerConnect-REV02-Corrected-Prototype.zip') as z:
 old=json.loads(z.read('SHA256SUMS.json'))
 for name in z.namelist():
  if name.startswith('Firmware/RykerConnect-REV02/') or name.startswith('Hardware/REV02-Corrected/software/'):
   data=z.read(name);assert hashlib.sha256(data).hexdigest()==old[name]
   target='software/'+name.split('/software/',1)[1] if '/software/' in name else name
   files[target]=data
 files['software/REV02-build-validation.json']=z.read('Hardware/REV02-Corrected/validation/android-test-summary.json')
files['software/README.txt']=b'Unchanged REV02/N8 firmware and Android APK, compatible with REV03 GPIOs. Use the included PlatformIO source for full blank-chip upload; firmware.bin is only the application image. No device was flashed or tested by this package. See root README.\n'
license_path=(Path(__file__).resolve().parents[2] / 'LICENSE')
if license_path.exists():files['LICENSE-original']=license_path.read_bytes()
archive=R.parent/'RykerConnect-REV03-Protected-Prototype.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(files.items()):z.writestr(name,data)
 z.writestr('SHA256SUMS.json',json.dumps({n:hashlib.sha256(d).hexdigest() for n,d in files.items()},indent=2))
with zipfile.ZipFile(archive) as z:
 assert z.testzip() is None
 manifest=json.loads(z.read('SHA256SUMS.json'))
 assert all(hashlib.sha256(z.read(n)).hexdigest()==h for n,h in manifest.items())
print(f'PASS: {archive.name}, {len(files)} files, {archive.stat().st_size:,} bytes; ZIP CRC and every SHA-256 verified.')
