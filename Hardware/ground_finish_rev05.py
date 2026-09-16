from pathlib import Path
import sys,pcbnew as p
R=Path(__file__).resolve().parent/('REV05-'+sys.argv[1]);b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'));fps={f.GetReference():f for f in b.GetFootprints()}
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(a):return p.ToMM(a.x),p.ToMM(a.y)
def via(x,y):
 v=p.PCB_VIA(b);v.SetPosition(pt(x,y));v.SetWidth(p.FromMM(.65));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet('GND'));v.SetLocked(True);b.Add(v)
def route(a,c):
 t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*c));t.SetWidth(p.FromMM(.3));t.SetLayer(p.F_Cu);t.SetNet(b.FindNet('GND'));t.SetLocked(True);b.Add(t)
if sys.argv[1]=='USB':
 for t in list(b.GetTracks()):
  if t.GetNetname()=='GND' and ((type(t).__name__=='PCB_VIA' and xy(t.GetPosition())==(136.5,112.5)) or (type(t).__name__=='PCB_TRACK' and xy(t.GetStart())==(136.5,111.0) and xy(t.GetEnd())==(136.5,112.5))):b.Delete(t)
 route((136.5,111),(136.5,111.8));via(136.5,111.8)
for ref,n,end in [('C5','2',(155,87.8)),('C6','2',(165,89.5)),('U7','1',(165.8,85.3))]:
 pd=next(a for a in fps[ref].Pads() if a.GetNumber()==n);pd.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL);route(xy(pd.GetPosition()),end);via(*end)
p.ZONE_FILLER(b).Fill(b.Zones())
# Stitch disconnected pour fragments only at points lying in GND copper on both layers.
zones={z.GetLayer():z for z in b.Zones() if not z.GetIsRuleArea() and z.GetNetname()=='GND'}
polys={layer:z.GetFilledPolysList(layer) for layer,z in zones.items()}
added=[]
for layer,poly in polys.items():
 for i in range(poly.OutlineCount()):
  outline=poly.COutline(i);box=outline.BBox();center=box.GetCenter();cx,cy=xy(center)
  candidates=[]
  for xi in range(int(p.ToMM(box.GetLeft())*2)+1,int(p.ToMM(box.GetRight())*2)):
   for yi in range(int(p.ToMM(box.GetTop())*2)+1,int(p.ToMM(box.GetBottom())*2)):
    x,y=xi/2,yi/2
    if outline.PointInside(pt(x,y)) and all(a.Contains(pt(x,y)) for a in polys.values()):candidates.append((abs(x-cx)+abs(y-cy),x,y))
  for _,x,y in sorted(candidates):
   test=p.BOX2I(pt(x-.7,y-.7),pt(1.4,1.4));blocked=False
   for f in b.GetFootprints():
    for pd in f.Pads():
     if pd.GetBoundingBox().Intersects(test):blocked=True
   for t in b.GetTracks():
    if (t.GetNetname()!='GND' or type(t).__name__=='PCB_VIA') and t.GetBoundingBox().Intersects(test):blocked=True
   if 165<x<172 and 81<y<89:blocked=True
   if x>181.3 and 76.5<y<97:blocked=True
   if not blocked:via(x,y);added.append((x,y));break
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b);print('Fragment stitches',added)
