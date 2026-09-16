from pathlib import Path
import json,hashlib,zipfile,csv,io
R=Path(__file__).resolve().parent;W=R.parent
digest=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
baseline=json.loads((R/'REV05-USB/validation/baseline-hashes.json').read_text())
changed=[p for p,h in baseline.items() if not (W/p).exists() or digest(W/p)!=h]
assert not changed,changed
helpers=['define_rev05','finish_rev05','ground_finish_rev05','ground_adjust_rev05','final_adjust_rev05','rtc_ground_rev05','label_rev05','rebuild_rev05','export_rev05','render_rev05']
skip={'__pycache__','.pio','.git','pathlib'}
old_reports={'routed-drc.json','strict-drc.json','finish-drc.json','ground-drc.json'}
outputs=[]
for v in ['12V','USB']:
 d=R/('REV05-'+v);files={}
 def add(p,arc=None):files[arc or p.relative_to(W).as_posix()]=p
 for folder in [d,W/'Firmware/RykerConnect-REV05']:
  for p in folder.rglob('*'):
   if p.is_file() and not any(a in skip for a in p.parts) and p.name not in old_reports and not p.name.startswith('~') and p.suffix not in ['.lck','.kicad_prl','.pyc','.obj','.exe']:add(p)
 for n in helpers:add(R/(n+'.py'))
 for p in [R/'REV03-Protected/RykerConnect.kicad_pcb',R/'REV03-Protected/validation/netlist.xml',R/'REV04-Carrier/design.json']:add(p)
 for p in (R/'REV03-Protected/RykerConnect.pretty').rglob('*'):
  if p.is_file():add(p)
 add(R/'REV05-START-HERE.md','START-HERE.md')
 add(R/'RykerConnect-REV03-Protected-Prototype/LICENSE-original','LICENSE-original')
 report=json.loads((d/'validation/release-validation.json').read_text())
 for n,h in report['manufacturing_sha256'].items():assert digest(d/'manufacturing'/n)==h
 archive=R/f'RykerConnect-REV05-{v}-Prototype.zip'
 with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
  for arc,p in sorted(files.items()):z.write(p,arc)
  z.writestr('SHA256SUMS.json',json.dumps({a:digest(p) for a,p in sorted(files.items())},indent=2))
 with zipfile.ZipFile(archive) as z:
  assert z.testzip() is None
  for a,h in json.loads(z.read('SHA256SUMS.json')).items():assert hashlib.sha256(z.read(a)).hexdigest()==h
  prefix=f'Hardware/REV05-{v}/manufacturing/'
  bom=list(csv.DictReader(io.StringIO(z.read(prefix+'BOM.csv').decode('utf-8-sig'))));cpl=list(csv.DictReader(io.StringIO(z.read(prefix+'CPL.csv').decode('utf-8-sig'))))
  refs=[r for a in bom for r in a['Designator'].split(',')];assert len(refs)==len(set(refs))==report['components']
  assert set(refs)=={a['Designator'] for a in cpl} and all(a['Layer']=='Top' for a in cpl)
 outputs.append({'archive':archive.name,'sha256':digest(archive),'bytes':archive.stat().st_size,'files':len(files),'integrity':'passed','bom_cpl':'passed','prior_hardware_hashes_unchanged':len(baseline)})
(R/'REV05-package-validation.json').write_text(json.dumps(outputs,indent=2));print(json.dumps(outputs,indent=2))
