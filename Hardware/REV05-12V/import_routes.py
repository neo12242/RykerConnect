"""Import routing only. Never import router placement changes."""
from pathlib import Path
import re,json
import pcbnew as p
from sexpr import read,field,fields
R=Path(__file__).resolve().parent; b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
s=read(R/'validation/carrier.ses');routes=field(s,'routes');network=field(routes,'network_out');scale=1000*float(field(routes,'resolution')[2])
def pt(x,y):return p.VECTOR2I(p.FromMM(float(x)/scale),p.FromMM(-float(y)/scale))
# KiCad DSN fixed tracks are omitted from SES; retain those original local loops.
for t in list(b.GetTracks()):
 if not t.IsLocked():b.Delete(t)
counts={}
for net in fields(network,'net'):
 name=str(net[1]);counts[name]=0
 for w in fields(net,'wire'):
  path=field(w,'path');layer=p.F_Cu if path[1]=='F.Cu' else p.B_Cu;width=float(path[2])/scale;points=[pt(*path[i:i+2]) for i in range(3,len(path),2)]
  for a,z in zip(points,points[1:]):
   if a==z:continue
   t=p.PCB_TRACK(b);t.SetStart(a);t.SetEnd(z);t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(name));b.Add(t);counts[name]+=1
 for v in fields(net,'via'):
  diam,drill=map(float,re.search(r'_(\d+):(\d+)_um',str(v[1])).groups());o=p.PCB_VIA(b);o.SetPosition(pt(v[2],v[3]));o.SetWidth(p.FromMM(diam/1000));o.SetDrill(p.FromMM(drill/1000));o.SetViaType(p.VIATYPE_THROUGH);o.SetLayerPair(p.F_Cu,p.B_Cu);o.SetNet(b.FindNet(name));b.Add(o)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
(R/'validation/imported-routes.json').write_text(json.dumps(counts,indent=2));print(counts)
