"""Validate actual final board/netlist and export a consistent manufacturing set."""
from pathlib import Path
import sys,subprocess,json,csv,re,zipfile,hashlib,xml.etree.ElementTree as ET
BASE=Path(__file__).resolve().parents[1];variant='12V';R=BASE/'REV05C-12V';V=R/'validation';M=R/'manufacturing';G=M/'gerbers';G.mkdir(exist_ok=True)
CLI=r'C:\Program Files\KiCad\10.0\bin\kicad-cli.exe';pcb=R/'RykerConnect.kicad_pcb';sch=R/'RykerConnect.kicad_sch'
def run(*args):subprocess.run([CLI,*map(str,args)],check=True)
run('sch','erc','--format','json','--exit-code-violations','-o',V/'erc.json',sch)
run('pcb','drc','--format','json','--schematic-parity','--exit-code-violations','-o',V/'final-drc.json',pcb)
report=json.loads((V/'final-drc.json').read_text());assert not any(report[k] for k in ['violations','unconnected_items','schematic_parity'])
assert all(x!='ignore' for x in json.loads((R/'RykerConnect.kicad_pro').read_text())['board']['design_settings']['rule_severities'].values())
run('sch','export','netlist','--format','kicadxml','-o',V/'netlist.xml',sch)
nl=ET.parse(V/'netlist.xml').getroot();nets={n.get('name'):{(a.get('ref'),a.get('pin')) for a in n.findall('node')} for n in nl.find('nets')}
assert {('U7','2'),('J5','2'),('J3','2'),('J4','1'),('D5','1')}<=nets['+3.3V']
assert not {('J6','1'),('J6','2')} & nets['+3.3V']
assert {('J6','21'),('J8','2'),('U7','3'),('C19','1')}<=nets['+5V_MODULE']
assert nets['VBAT']=={('BT1','1'),('D6','1')}
assert nets['RTC_VDD']=={('D5','3'),('D6','3'),('U3','8'),('C3','1')}
assert nets['RTC_OSCI']=={('U3','1'),('X1','1'),('C18','1')}
assert nets['RTC_OSCO']=={('U3','2'),('X1','2')}
assert ('U3','4') in nets['GND'] and ('U7','1') in nets['GND']
assert {('U3','5'),('J5','3'),('J6','12')}<=nets['SDA']
assert {('U3','6'),('J5','4'),('J6','11')}<=nets['SCL']
if variant=='12V':
 assert {('U6','8'),('U6','17'),('C16','2'),('R9','2'),('R10','2')}<=nets['PROT_RTN'];assert not nets['PROT_RTN']&nets['GND']
 assert {('L1','2'),('U5','1'),('J8','1'),('C12','1'),('C13','1')}<=nets['+5V_BUCK']
 assert {('U6','15'),('U6','16'),('U5','2'),('U5','3')}<=nets['+12V_PROTECTED']
 assert nets['TVS_MID']=={('D2','2'),('D4','2')}
else:assert nets['+5V_EXTERNAL']=={('J1','1'),('J8','1')}
parts=json.loads((R/'design.json').read_text());refs={a['ref'] for a in parts};assert 'U2' not in refs and 'C4' not in refs
assert len(refs)==len(parts)==(40 if variant=='12V' else 21)
if variant=='USB':assert not {'U5','U6','L1','D2','D3','D4'}&refs
run('pcb','export','pos','--format','csv','--units','mm','--use-drill-file-origin','--exclude-dnp','--side','both','-o',V/'native-positions.csv',pcb)
pos={a['Ref']:a for a in csv.DictReader((V/'native-positions.csv').open(encoding='utf-8-sig'))};assert set(pos)==refs;assert all(a['Side']=='top' for a in pos.values())
groups={}
for a in parts:groups.setdefault((a['code'],a['mpn'],a['fp']),[]).append(a)
with (M/'BOM.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Comment','Designator','Footprint','LCSC Part Number','Quantity','Manufacturer','Manufacturer Part Number'])
 for (code,mpn,fp),aa in groups.items():w.writerow([aa[0]['value'],','.join(a['ref'] for a in aa),fp.split(':')[-1],code,len(aa),aa[0]['manufacturer'],mpn])
with (M/'CPL.csv').open('w',encoding='utf-8-sig',newline='') as f:
 w=csv.writer(f);w.writerow(['Designator','Mid X','Mid Y','Layer','Rotation'])
 for a in parts:
  ref=a['ref'];row=pos[ref];x=float(row['PosX']);y=float(row['PosY']);rot=float(row['Rot'])
  if ref=='J1':x-=1.5;y-=2.53
  if ref in ['J6','J7']:x-=26.67
  if ref=='J8':x+=1.27
  w.writerow([ref,f'{x:.4f}mm',f'{y:.4f}mm','Top',f'{rot%360:.2f}'])
stock=json.loads((V/'stock-check.json').read_text());by={a['code']:a for a in stock['parts']};assert set(by)=={a['code'] for a in parts}
assert all(by[c]['available_order_quantity']>=5*sum(a['code']==c for a in parts) for c in by)
run('pcb','export','gerbers','-l','F.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts','--use-drill-file-origin','--subtract-soldermask','-o',str(G)+'/',pcb)
run('pcb','export','drill','--format','excellon','--drill-origin','plot','--excellon-units','mm','--excellon-separate-th','--excellon-oval-format','route','-o',str(G)+'/',pcb)
run('sch','export','pdf','-o',V/'schematics.pdf',sch)
for side,mirror in [('F',[]),('B',['--mirror'])]:run('pcb','export','pdf','-l',f'{side}.Cu,{side}.Silkscreen,Edge.Cuts','--mode-single','--scale','0',*mirror,'-o',V/f'board-{side}.pdf',pcb)
run('pcb','export','pdf','-l','F.Fab,F.Silkscreen,User.Drawings,Edge.Cuts','--sketch-pads-on-fab-layers','--mode-single','--scale','0','-o',V/'assembly-F.pdf',pcb)
with zipfile.ZipFile(M/'Gerbers.zip','w',zipfile.ZIP_DEFLATED) as z:
 for f in sorted(G.iterdir()):
  if f.is_file():z.write(f,f.name)
validation={'revision':'REV05C-12V','components':len(parts),'bom_groups':len(groups),'top':len(parts),'bottom':0,'erc':0,'drc':0,'unconnected':0,'schematic_parity':0,'all_drc_rules_enabled':True,'critical_net_assertions':'passed','stock_for_five_boards_at_observation':True,'component_cost_usd_tier1':stock['per_board_components_usd_tier1'],'vendor_placement_and_process_acceptance':'pending','physical_validation':'pending','manufacturing_sha256':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in M.iterdir() if f.is_file()}}
(V/'release-validation.json').write_text(json.dumps(validation,indent=2));print(json.dumps(validation,indent=2))
