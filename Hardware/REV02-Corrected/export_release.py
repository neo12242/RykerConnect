"""Export and verify the corrected prototype manufacturing files with KiCad 10."""
from pathlib import Path
import csv, hashlib, json, re, subprocess, zipfile
R=Path(__file__).resolve().parent
CLI=r'C:\Program Files\KiCad\10.0\bin\kicad-cli.exe'
V=R/'validation'; V.mkdir(exist_ok=True)
M=R/'manufacturing'; M.mkdir(exist_ok=True)
G=M/'gerbers'; G.mkdir(exist_ok=True)
pcb=str(R/'RykerConnect.kicad_pcb'); sch=str(R/'RykerConnect.kicad_sch')
def run(*args): subprocess.run([CLI,*map(str,args)],check=True)
run('sch','erc','--format','json','--exit-code-violations','-o',V/'erc.json',sch)
run('pcb','drc','--format','json','--schematic-parity','--exit-code-violations','-o',V/'drc-parity.json',pcb)
run('sch','export','netlist','-o',V/'netlist.xml',sch)
run('pcb','export','pos','--format','csv','--units','mm','--use-drill-file-origin','--exclude-dnp','--side','both','-o',V/'native-positions.csv',pcb)
run('pcb','export','gerbers','-l','F.Cu,B.Cu,F.Paste,B.Paste,F.Silkscreen,B.Silkscreen,F.Mask,B.Mask,Edge.Cuts','--use-drill-file-origin','--subtract-soldermask','-o',str(G)+'/',pcb)
run('pcb','export','drill','--format','excellon','--drill-origin','plot','--excellon-units','mm','--excellon-separate-th','--excellon-oval-format','route','-o',str(G)+'/',pcb)
run('pcb','export','pdf','-l','F.Cu,F.Silkscreen,Edge.Cuts','--mode-single','--scale','0','-o',V/'board-top.pdf',pcb)
run('pcb','export','pdf','-l','B.Cu,B.Silkscreen,Edge.Cuts','--mode-single','--scale','0','-o',V/'board-bottom.pdf',pcb)
run('sch','export','pdf','-o',V/'schematics.pdf',sch)
rows=list(csv.DictReader((V/'native-positions.csv').open(encoding='utf-8-sig')))
pos={x['Ref']:x for x in rows}
parts=json.loads((R/'selected-parts.json').read_text())
refs=[ref for part in parts for ref in part['refs'].split(',')]
assert len(refs)==len(set(refs))==32 and len(parts)==21
assert set(refs)==set(pos), (set(refs)-set(pos),set(pos)-set(refs))
assert all(re.fullmatch(r'C\d+',x['code']) for x in parts)
with (M/'BOM.csv').open('w',newline='',encoding='utf-8-sig') as f:
    w=csv.writer(f);w.writerow(['Comment','Designator','Footprint','LCSC Part #','Quantity','Manufacturer','Manufacturer Part Number'])
    for part in parts:
        rr=part['refs'].split(','); package=pos[rr[0]]['Package'].split('_',1)[1]
        w.writerow([part['mpn']+'; '+pos[rr[0]]['Val'],part['refs'],package,part['code'],len(rr),part['manufacturer'],part['mpn']])
with (M/'CPL.csv').open('w',newline='',encoding='utf-8-sig') as f:
    w=csv.writer(f);w.writerow(['Designator','Mid X','Mid Y','Layer','Rotation'])
    for ref in refs:
        row=pos[ref]; x=float(row['PosX']);y=float(row['PosY']);rot=float(row['Rot'])
        # Preserve the original assembler export's model/centroid conventions.
        # These remain subject to the actual JLCPCB model preview.
        if ref=='J1':x-=1.5;y-=2.53
        if ref=='U1':rot+=180
        if ref=='U2':rot-=90
        w.writerow([ref,f'{x:.4f}mm',f'{y:.4f}mm','Top' if row['Side']=='top' else 'Bottom',f'{rot%360:.2f}'])
old=json.loads((R.parent/'JLCPCB-REV02-review'/'validation.json').read_text())
hashes={path:hashlib.sha256(Path(path).read_bytes()).hexdigest() for path in old['source_inputs_sha256']}
assert hashes==old['source_inputs_sha256'],'Original source changed'
with zipfile.ZipFile(M/'Gerbers.zip','w',zipfile.ZIP_DEFLATED) as z:
    for f in sorted(G.iterdir()):
        if f.is_file():z.write(f,f.name)
report={'status':'PROTOTYPE FILES - sourcing, assembly model preview and installation validation pending','fitted_components':32,'bom_groups':21,'mapped_components':32,'original_sources_unchanged':True,'source_inputs_sha256':hashes,'manufacturing_sha256':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in M.iterdir() if f.is_file()},'CPL_adjustments':{'J1':'X -1.5 mm, Y -2.53 mm from new footprint origin','U1':'rotation +180 degrees','U2':'rotation -90 degrees'},'not_verified':['JLCPCB stock and exact assembly model alignment','Physical assembly, OLED variant and sensor operation','Vehicle supply protection','Sensor cable length, ESD and EMC']}
(V/'release-validation.json').write_text(json.dumps(report,indent=2))
print('PASS: 32 fitted components, 21 BOM groups, all catalog IDs populated; original input hashes unchanged.')
