"""Import ONLY new protection-net routing from a local Freerouting SES result.
Never import component movements or edits to existing signal/power nets.
"""
from pathlib import Path
import re,json
import pcbnew as p
from sexpr import read,field,fields
R=Path(__file__).resolve().parent
b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
s=read(R/'validation/protection.ses');routes=field(s,'routes');network=field(routes,'network_out')
scale=1000*float(field(routes,'resolution')[2])
allowed={'+12V','+12V_PROTECTED','TVS_MID','PROT_ILIM','PROT_RTN','PROT_OV','PROT_UV','PROT_DVDT'}
def pt(x,y):return p.VECTOR2I(p.FromMM(float(x)/scale),p.FromMM(-float(y)/scale))
# Idempotent: these nets have no original retained copper in the protected base.
for t in list(b.GetTracks()):
 if t.GetNetname() in allowed:b.Delete(t)
counts={}
for net in fields(network,'net'):
 name=str(net[1])
 if name not in allowed:continue
 counts[name]=0
 for wire in fields(net,'wire'):
  path=field(wire,'path');layer=p.F_Cu if path[1]=='F.Cu' else p.B_Cu;width=float(path[2])/scale
  points=[pt(*path[i:i+2]) for i in range(3,len(path),2)]
  for a,z in zip(points,points[1:]):
   if a==z:continue
   t=p.PCB_TRACK(b);t.SetStart(a);t.SetEnd(z);t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(name));b.Add(t);counts[name]+=1
 for via in fields(net,'via'):
  diameter,drill=map(float,re.search(r'_(\d+):(\d+)_um',str(via[1])).groups())
  v=p.PCB_VIA(b);v.SetPosition(pt(via[2],via[3]));v.SetWidth(p.FromMM(diameter/1000));v.SetDrill(p.FromMM(drill/1000));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet(name));b.Add(v)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
(R/'validation/imported-routing.json').write_text(json.dumps(counts,indent=2));print(counts)
