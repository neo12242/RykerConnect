"""Ground stitching and final reset connection preparation."""
from pathlib import Path
import pcbnew as p
R=Path(__file__).resolve().parent;b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
fps={f.GetReference():f for f in b.GetFootprints()}
for pad in fps['C3'].Pads():
 if pad.GetNumber()=='2':pad.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
for ref,num in [('U2','4'),('J6','22')]:
 for pd in fps[ref].Pads():
  if pd.GetNumber()==num:pd.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
def via(x,y,net='GND'):
 v=p.PCB_VIA(b);v.SetPosition(pt(x,y));v.SetWidth(p.FromMM(.65));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet(net));v.SetLocked(True);b.Add(v)
def route(net,points,width=.35,layer=p.F_Cu):
 for a,z in zip(points,points[1:]):
  t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*z));t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(net));t.SetLocked(True);b.Add(t)
for pd in fps['J5'].Pads():
 if pd.GetNumber()=='1':pd.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
# Only stitch where no other-net copper or drilled hole is nearby.
for x in [93,101,113,123,133,145,155,165,173,183]:
 for y in [73,79,91,95,103,114]:
  box=p.BOX2I(pt(x-.6,y-.6),pt(1.2,1.2));blocked=False
  for f in b.GetFootprints():
   for pd in f.Pads():
    if (pd.GetNetname()!='GND' or pd.GetDrillSize().x>0) and pd.GetBoundingBox().Intersects(box):blocked=True
  for t in b.GetTracks():
   if t.GetNetname()!='GND' and t.GetBoundingBox().Intersects(box):blocked=True
  if 169.5<x<178.5 and 79.5<y<91.5:blocked=True
  if x>181.4 and 76.6<y<96.8:blocked=True
  if not blocked:via(x,y)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
for t in b.GetTracks():t.SetLocked(True)
p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
p.ExportSpecctraDSN(b,str(R/'validation/reset.dsn'))
print('Final ground stitching complete.')

