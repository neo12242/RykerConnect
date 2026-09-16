"""Prove preservation of old geometry and all unrelated signal routing."""
from pathlib import Path
from collections import Counter
import json,hashlib,zipfile
import pcbnew as p
R=Path(__file__).resolve().parent;S=R.parent/'REV02-Corrected'
old=p.LoadBoard(str(S/'RykerConnect.kicad_pcb'));new=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
def pt(v):return [v.x,v.y]
def edges(b):
 out=[]
 for x in b.GetDrawings():
  if x.GetLayer()==p.Edge_Cuts:
   v=[int(x.GetShape()),pt(x.GetStart()),pt(x.GetEnd()),x.GetWidth()]
   if x.GetShape()==p.SHAPE_T_ARC:v.append(pt(x.GetCenter()))
   out.append(v)
 return sorted(out,key=str)
assert edges(old)==edges(new),'Outline changed'
of={f.GetReference():f for f in old.GetFootprints()};nf={f.GetReference():f for f in new.GetFootprints()}
retained=0
for ref,f in of.items():
 if ref.startswith('kibuzzard'):continue
 n=nf[ref];assert pt(f.GetPosition())==pt(n.GetPosition()),ref
 assert f.GetOrientationDegrees()==n.GetOrientationDegrees(),ref
 def pads(fp):return sorted([(x.GetNumber(),pt(x.GetPosition()),pt(x.GetSize()),pt(x.GetDrillSize())) for x in fp.Pads()],key=str)
 assert pads(f)==pads(n),ref
 retained+=1
def sig(t):
 return (t.GetNetname(),t.GetLayer(),tuple(pt(t.GetStart())),tuple(pt(t.GetEnd())),t.GetWidth(p.F_Cu) if isinstance(t,p.PCB_VIA) else t.GetWidth(),t.GetDrill() if isinstance(t,p.PCB_VIA) else None)
original=Counter(sig(t) for t in old.GetTracks() if t.GetNetname() not in ['GND','+12V'])
current=Counter(sig(t) for t in new.GetTracks() if t.GetNetname() not in ['GND','+12V'])
assert not (original-current),'Unrelated original routing changed'
with zipfile.ZipFile(R.parent/'RykerConnect-REV02-Corrected-Prototype.zip') as z:
 manifest=json.loads(z.read('SHA256SUMS.json'))
 for name in ['RykerConnect.kicad_pcb','RykerConnect.kicad_sch','power.kicad_sch','RykerConnect.kicad_pro']:
  assert hashlib.sha256((S/name).read_bytes()).hexdigest()==manifest['Hardware/REV02-Corrected/'+name]
report={'outline_unchanged':True,'retained_nonartwork_footprints_unchanged':retained,'preserved_signal_track_and_via_items':sum(original.values()),'REV02_source_matches_preserved_archive':True,'intentional_exceptions':['Accessory +12V path replaced by new protected path','Conflicting ground stitches replaced by planes/local return vias','Back-side decorative artwork relocated; old REV02 artwork removed']}
(R/'validation/design-integrity.json').write_text(json.dumps(report,indent=2));print(report)
inventory={}
for ref,f in nf.items():
 if f.GetAttributes() & p.FP_EXCLUDE_FROM_POS_FILES or f.IsDNP():continue
 inventory[ref]={'value':f.GetValue(),'side':'top' if f.GetLayer()==p.F_Cu else 'bottom','position_mm':[p.ToMM(v) for v in pt(f.GetPosition())],'rotation':f.GetOrientationDegrees(),'pads':[{'number':pad.GetNumber(),'net':pad.GetNetname(),'position_mm':[p.ToMM(v) for v in pt(pad.GetPosition())]} for pad in f.Pads() if pad.GetNumber()]}
assert len(inventory)==44
(R/'validation/final-board-inventory.json').write_text(json.dumps(inventory,indent=2))

