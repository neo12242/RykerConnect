"""Create the reviewable prototype archive and verify every archived file hash."""
from pathlib import Path
import hashlib,json,zipfile
R=Path(__file__).resolve().parent;ROOT=R.parents[1];out=R.parent/'RykerConnect-REV04-Carrier-Prototype.zip'
report=json.loads((R/'validation/release-validation.json').read_text())
assert all(report[k]==0 for k in ['erc','drc','unconnected','schematic_parity','bottom'])
assert report['components']==36 and report['bom_groups']==27 and report['all_drc_rules_enabled']
files={}
def add(f,name=None):
 assert f.is_file(),f
 files[name or f.relative_to(ROOT).as_posix()]=f.read_bytes()
validation={'erc.json','final-drc.json','netlist.xml','native-positions.csv','release-validation.json','stock-check.json','protection-calculations.json','prior-release-integrity.json','firmware-build.log','firmware-config.json','carrier.ses','imported-routes.json','board-F.pdf','board-B.pdf','assembly-F.pdf','schematics.pdf','board-F.png','board-B.png','assembly-F.png','schematic-1.png','schematic-2.png','schematic-3.png','schematic-4.png','pads.json'}
for f in R.rglob('*'):
 if not f.is_file() or any(x in f.parts for x in ['__pycache__','strict-audit']):continue
 if f.suffix in ['.pyc','.kicad_prl'] or f.name.endswith('.lck'):continue
 if f.relative_to(R).parts[0]=='validation' and f.name not in validation:continue
 add(f)
# Minimal prior inputs needed by the generator; no earlier Gerbers/BOM in this package.
S=R.parent/'REV03-Protected'
for n in ['RykerConnect.kicad_pcb','selected-parts.json','protection_defs.py','validation/netlist.xml']:add(S/n)
for f in (S/'RykerConnect.pretty').glob('*.kicad_mod'):add(f)
add(R.parent/'JLCPCB-REV02-review/validation.json')
fw=ROOT/'Firmware/RykerConnect-REV04'
for f in fw.rglob('*'):
 if f.is_file() and not any(x in f.relative_to(fw).parts for x in ['.pio','.git','__pycache__']) and f.name!='pathlib':add(f)
for name in ['firmware.bin','bootloader.bin','partitions.bin']:add(fw/'.pio/build/RykerConnect_REV04'/name,'software/'+name)
add((Path.home() / '.platformio/packages/framework-arduinoespressif32/tools/partitions/boot_app0.bin'),'software/boot_app0.bin')
add(fw/'REV04-README.md','software/README.md')
add(R.parent/'RykerConnect-REV03-Protected-Prototype/LICENSE-original','LICENSE-original')
files['README.md']=b'# RykerConnect REV04 carrier prototype\n\nStart with Hardware/REV04-Carrier/README.md.\n\nJLCPCB files are Hardware/REV04-Carrier/manufacturing/Gerbers.zip, BOM.csv and CPL.csv. Upload that Gerbers.zip for fabrication, not this complete delivery archive.\n\nAll 36 carrier component bodies are on top. ESP module and accessories are separate. Board is 102 x 46mm; enclosure changes are required. This is a reviewed, untested prototype; vendor assembly acceptance and physical qualification remain pending.\n\nThe included REV03 files are generator inputs only. Firmware source and built images are included. Original license is preserved. No order has been placed.\n'
manifest={n:hashlib.sha256(v).hexdigest() for n,v in sorted(files.items())}
files['SHA256SUMS.json']=json.dumps(manifest,indent=2).encode()
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for n,data in sorted(files.items()):z.writestr(n,data)
with zipfile.ZipFile(out) as z:
 assert z.testzip() is None
 assert all(hashlib.sha256(z.read(n)).hexdigest()==h for n,h in manifest.items())
(R/'validation/package-validation.json').write_text(json.dumps({'archive':out.name,'files':len(files),'bytes':out.stat().st_size,'sha256':hashlib.sha256(out.read_bytes()).hexdigest(),'archive_crc_and_all_file_hashes':'passed'},indent=2))
print(out);print(len(files),'files; archive and per-file hash verification passed.')
