"""Native validation and matched prototype manufacturing export. Run with normal Python."""
from pathlib import Path
import csv,json,re,subprocess,zipfile,hashlib,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent;V=R/'validation';M=R/'manufacturing';G=M/'gerbers';G.mkdir(parents=True,exist_ok=True)
CLI=r'C:\Program Files\KiCad\10.0\bin\kicad-cli.exe'
pcb=R/'RykerConnect.kicad_pcb';sch=R/'RykerConnect.kicad_sch'
def run(*a):subprocess.run([CLI,*map(str,a)],check=True)
run('sch','erc','--format','json','--exit-code-violations','-o',V/'erc.json',sch)
run('pcb','drc','--format','json','--schematic-parity','--exit-code-violations','-o',V/'final-drc.json',pcb)
run('sch','export','netlist','--format','kicadxml','-o',V/'netlist.xml',sch)
run('pcb','export','pos','--format','csv','--units','mm','--use-drill-file-origin','--exclude-dnp','--side','both','-o',V/'native-positions.csv',pcb)
run('pcb','export','gerbers','-l','F.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts','--use-drill-file-origin','--subtract-soldermask','-o',str(G)+'/',pcb)
run('pcb','export','drill','--format','excellon','--drill-origin','plot','--excellon-units','mm','--excellon-separate-th','--excellon-oval-format','route','-o',str(G)+'/',pcb)
for side,mirror in [('F',[]),('B',['--mirror'])]:
 run('pcb','export','pdf','-l',f'{side}.Cu,{side}.Silkscreen,Edge.Cuts','--mode-single','--scale','0',*mirror,'-o',V/f'board-{side}.pdf',pcb)
run('pcb','export','pdf','-l','F.Fab,F.Silkscreen,User.Drawings,Edge.Cuts','--sketch-pads-on-fab-layers','--mode-single','--scale','0','-o',V/'assembly-F.pdf',pcb)
run('sch','export','pdf','-o',V/'schematics.pdf',sch)
parts=json.loads((R/'design.json').read_text());refs={x['ref'] for x in parts};groups={}
for part in parts:groups.setdefault((part['code'],part['mpn'],part['fp']),[]).append(part)
pos={x['Ref']:x for x in csv.DictReader((V/'native-positions.csv').open(encoding='utf-8-sig'))}
assert len(refs)==len(parts)==36 and set(pos)==refs
assert all(v['Side']=='top' for v in pos.values())
assert all(re.fullmatch(r'C\d+',a['code']) for a in parts)
with (M/'BOM.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Comment','Designator','Footprint','LCSC Part Number','Quantity','Manufacturer','Manufacturer Part Number'])
 for (code,mpn,fp),items in groups.items():w.writerow([items[0]['value'],','.join(i['ref'] for i in items),fp.split(':')[-1],code,len(items),items[0]['manufacturer'],mpn])
with (M/'CPL.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Designator','Mid X','Mid Y','Layer','Rotation'])
 for part in parts:
  ref=part['ref'];row=pos[ref];x=float(row['PosX']);y=float(row['PosY']);rot=float(row['Rot'])
  if ref=='J1':x-=1.5;y-=2.53
  if ref in ['J6','J7']:x-=26.67
  if ref=='J8':x+=1.27
  if ref=='U2':rot-=90
  w.writerow([ref,f'{x:.4f}mm',f'{y:.4f}mm','Top',f'{rot%360:.2f}'])
nl=ET.parse(V/'netlist.xml').getroot();nets={n.get('name'):{(a.get('ref'),a.get('pin')) for a in n.findall('node')} for n in nl.find('nets')}
assert {('J1','1'),('U6','1'),('U6','2'),('D2','1')} <= nets['+12V']
assert {('U6','15'),('U6','16'),('D3','1'),('U5','2'),('U5','3')} <= nets['+12V_PROTECTED']
assert {('U6','8'),('U6','17'),('C16','2'),('R9','2'),('R10','2')} <= nets['PROT_RTN']
assert not nets['PROT_RTN'] & nets['GND']
assert nets['TVS_MID']=={('D2','2'),('D4','2')}
assert ('D4','1') in nets['GND'] and ('D3','2') in nets['GND']
assert {('L1','2'),('U5','1'),('J8','1'),('C12','1'),('C13','1')} <= nets['+5V_BUCK']
assert nets['+5V_MODULE']=={('J8','2'),('J6','21')}
assert {('J6','1'),('J6','2'),('U3','2'),('U2','8'),('J5','2')} <= nets['+3.3V']
assert nets['VBAT']=={('BT1','1'),('U3','14')}
assert all(('U3',str(i)) in nets['GND'] for i in range(5,14))
for pin,net in [(7,'GPIO7'),(8,'DSP_RES'),(11,'SCL'),(12,'SDA'),(16,'CS2'),(17,'SPI_MOSI'),(18,'SPI_SCK'),(19,'DSP_DC'),(20,'CS1')]:assert ('J6',str(pin)) in nets[net]
assert all(('J7',str(n)) in nets['GND'] for n in [1,21,22])
assert all(('J5',str(pin)) in nets[net] for pin,net in [(1,'GND'),(2,'+3.3V'),(3,'SDA'),(4,'SCL')])
stock=json.loads((V/'stock-check.json').read_text());stock_by={x['code']:x for x in stock['parts']}
assert set(stock_by)=={x['code'] for x in parts}
assert all(stock_by[code]['available_order_quantity']>=5*sum(x['code']==code for x in parts) for code in stock_by)
old=json.loads((R.parent/'JLCPCB-REV02-review/validation.json').read_text())['source_inputs_sha256']
originals_available=all(Path(path).is_file() for path in old)
assert all(hashlib.sha256(Path(path).read_bytes()).hexdigest()==h for path,h in old.items() if Path(path).is_file())
assert '[SUCCESS]' in (V/'firmware-build.log').read_text()
with zipfile.ZipFile(M/'Gerbers.zip','w',zipfile.ZIP_DEFLATED) as z:
 for file in sorted(G.iterdir()):
  if file.is_file():z.write(file,file.name)
report={'status':'Prototype files - vendor placement/process acceptance and physical validation pending','components':len(parts),'bom_groups':len(groups),'catalog_parts':len(stock_by),'top':len(parts),'bottom':0,'drc':0,'erc':0,'unconnected':0,'schematic_parity':0,'independent_critical_nets':'passed','stock_check':'All catalog identities available for 5 boards at observation time; no reservation','firmware':'RykerConnect_REV04 build passed; no hardware boot test','original_downloads_hashes_unchanged':True,'board_size_mm':[102,46],'manufacturing_sha256':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in M.iterdir() if f.is_file()}}
(V/'release-validation.json').write_text(json.dumps(report,indent=2))
report['original_downloads_hashes_unchanged']=True if originals_available else None
report['all_drc_rules_enabled']=all(v!='ignore' for v in json.loads((R/'RykerConnect.kicad_pro').read_text())['board']['design_settings']['rule_severities'].values())
(V/'release-validation.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
