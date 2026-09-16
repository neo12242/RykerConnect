"""Validate and export the protected prototype as one matched manufacturing set."""
from pathlib import Path
import csv,hashlib,json,re,subprocess,zipfile,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent;V=R/'validation';M=R/'manufacturing';G=M/'gerbers'
G.mkdir(parents=True,exist_ok=True)
CLI=r'C:\Program Files\KiCad\10.0\bin\kicad-cli.exe'
pcb=R/'RykerConnect.kicad_pcb';sch=R/'RykerConnect.kicad_sch'
def run(*args):subprocess.run([CLI,*map(str,args)],check=True)
run('sch','erc','--format','json','--exit-code-violations','-o',V/'erc.json',sch)
run('pcb','drc','--format','json','--schematic-parity','--exit-code-violations','-o',V/'final-drc.json',pcb)
run('sch','export','netlist','--format','kicadxml','-o',V/'netlist.xml',sch)
run('pcb','export','pos','--format','csv','--units','mm','--use-drill-file-origin','--exclude-dnp','--side','both','-o',V/'native-positions.csv',pcb)
run('pcb','export','gerbers','-l','F.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts','--use-drill-file-origin','--subtract-soldermask','-o',str(G)+'/',pcb)
run('pcb','export','drill','--format','excellon','--drill-origin','plot','--excellon-units','mm','--excellon-separate-th','--excellon-oval-format','route','-o',str(G)+'/',pcb)
for side,mirror in [('F',[]),('B',['--mirror'])]:
 run('pcb','export','pdf','-l',f'{side}.Cu,{side}.Silkscreen,Edge.Cuts','--mode-single','--scale','0',*mirror,'-o',V/f'board-{side}.pdf',pcb)
 run('pcb','export','pdf','-l',f'{side}.Fab,{side}.Silkscreen,Edge.Cuts','--sketch-pads-on-fab-layers','--hide-DNP-footprints-on-fab-layers','--mode-single','--scale','0',*mirror,'-o',V/f'assembly-{side}.pdf',pcb)
run('sch','export','pdf','-o',V/'schematics.pdf',sch)
parts=json.loads((R/'selected-parts.json').read_text())
pos={x['Ref']:x for x in csv.DictReader((V/'native-positions.csv').open(encoding='utf-8-sig'))}
refs=[ref for part in parts for ref in part['refs'].split(',')]
assert len(refs)==len(set(refs))==44 and set(refs)==set(pos)
assert sum(x['Side']=='top' for x in pos.values())==32
assert sum(x['Side']=='bottom' for x in pos.values())==12
assert all(re.fullmatch(r'C\d+',x['code']) for x in parts)
groups={}
for part in parts:
 key=(part['mpn'],part['code']);group=groups.setdefault(key,dict(part,refs=[]))
 group['refs'].extend(part['refs'].split(','))
assert len(groups)==31
with (M/'BOM.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Comment','Designator','Footprint','LCSC Part #','Quantity','Manufacturer','Manufacturer Part Number'])
 for part in groups.values():
  rr=part['refs'];packages={pos[r]['Package'].split('_',1)[1] for r in rr}
  if len(packages)>1:
   assert all('R_0805' in x for x in packages);package='0805'
  else:package=next(iter(packages))
  w.writerow([part['mpn']+'; '+pos[rr[0]]['Val'],','.join(rr),package,part['code'],len(rr),part['manufacturer'],part['mpn']])
with (M/'CPL.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Designator','Mid X','Mid Y','Layer','Rotation'])
 for ref in refs:
  row=pos[ref];x=float(row['PosX']);y=float(row['PosY']);rot=float(row['Rot'])
  if ref=='J1':x-=1.5;y-=2.53
  if ref=='U1':rot+=180
  if ref=='U2':rot-=90
  w.writerow([ref,f'{x:.4f}mm',f'{y:.4f}mm','Top' if row['Side']=='top' else 'Bottom',f'{rot%360:.2f}'])
# Check actual exported schematic connectivity, independently of generation data.
netlist=ET.parse(V/'netlist.xml').getroot()
nets={n.get('name'):{(x.get('ref'),x.get('pin')) for x in n.findall('node')} for n in netlist.find('nets')}
assert ('J1','1') in nets['+12V'] and ('D1','1') not in nets['+12V']
assert ('D1','1') in nets['+12V_PROTECTED'] and ('U6','15') in nets['+12V_PROTECTED']
assert {('U6','8'),('U6','17'),('C16','2'),('R9','2'),('R10','2')} <= nets['PROT_RTN']
assert not nets['PROT_RTN'] & nets['GND']
assert nets['TVS_MID']=={('D2','2'),('D4','2')}
assert ('D2','1') in nets['+12V'] and ('D4','1') in nets['GND']
assert ('D3','1') in nets['+12V_PROTECTED'] and ('D3','2') in nets['GND']
old=json.loads((R.parent/'JLCPCB-REV02-review/validation.json').read_text())['source_inputs_sha256']
assert all(hashlib.sha256(Path(path).read_bytes()).hexdigest()==h for path,h in old.items())
with zipfile.ZipFile(M/'Gerbers.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(G.iterdir()):
  if f.is_file():z.write(f,f.name)
report={'status':'Protected prototype - assembly model, sourcing, enclosure and physical electrical validation pending','components':44,'top':32,'bottom':12,'bom_groups':31,'all_catalog_codes_populated':True,'original_source_hashes_unchanged':True,'critical_net_assertions':'passed','drc':0,'erc':0,'unconnected':0,'schematic_parity':0,'manufacturing_sha256':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in M.iterdir() if f.is_file()},'software':'Existing REV02/N8 build remains compatible; no firmware or app source changed for this power-only revision.'}
(V/'release-validation.json').write_text(json.dumps(report,indent=2))
print('PASS: 44 components (32 top/12 bottom), 31 BOM groups; critical net assertions and original input hashes pass.')
