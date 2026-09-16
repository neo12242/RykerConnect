"""Final copper, ground stitching and labels, after import of retained routing."""
from pathlib import Path
import pcbnew as p
import sys,json
R=Path(__file__).resolve().parent/('REV05-'+sys.argv[1]);b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'));fps={f.GetReference():f for f in b.GetFootprints()}
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(v):return p.ToMM(v.x),p.ToMM(v.y)
def via(x,y,net='GND'):
 v=p.PCB_VIA(b);v.SetPosition(pt(x,y));v.SetWidth(p.FromMM(.65));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet(net));v.SetLocked(True);b.Add(v)
def route(net,points,width=.35,layer=p.F_Cu):
 for a,z in zip(points,points[1:]):
  t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*z));t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(net));t.SetLocked(True);b.Add(t)
if sys.argv[1]=='12V':
 for t in list(b.GetTracks()):
  if type(t).__name__=='PCB_TRACK' and t.GetNetname()=='+5V_BUCK' and xy(t.GetStart())==(168.0,101.0) and xy(t.GetEnd())==(168.0,107.95):b.Delete(t)
 route('+5V_BUCK',[(168,101),(170,103),(170,107.95),(168,107.95)],.25)
for ref,n in [('C3','2'),('J5','1'),('J6','22')]:
 for pad in fps[ref].Pads():
  if pad.GetNumber()==n:pad.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
if sys.argv[1]=='USB':
 route('GND',[(136.5,111),(136.5,112.5)],.3);via(136.5,112.5)
for x in [93,101,113,123,133,145,151,155,161,165,173,179,183]:
 for y in [73,79,91,95,103,114]:
  box=p.BOX2I(pt(x-.7,y-.7),pt(1.4,1.4));blocked=False
  for f in b.GetFootprints():
   for pd in f.Pads():
    if (pd.GetNetname()!='GND' or pd.GetDrillSize().x>0) and pd.GetBoundingBox().Intersects(box):blocked=True
  for t in b.GetTracks():
   if t.GetNetname()!='GND' and t.GetBoundingBox().Intersects(box):blocked=True
  if 165<x<172 and 81<y<89:blocked=True
  if x>181.3 and 76.5<y<97:blocked=True
  if not blocked:via(x,y)
# Local heat-spreading copper on the regulator output tab, separated from all other rails.
zone=p.ZONE(b);zone.SetLayer(p.F_Cu);zone.SetNet(b.FindNet('+3.3V'));zone.SetAssignedPriority(2);zone.SetLocalClearance(p.FromMM(.25));zone.SetPadConnection(p.ZONE_CONNECTION_FULL);zone.SetIslandRemovalMode(p.ISLAND_REMOVAL_MODE_ALWAYS)
o=zone.Outline();o.NewOutline()
for x,y in [(152,78),(165,78),(165,90),(152,90)]:o.Append(p.FromMM(x),p.FromMM(y))
b.Add(zone)
for f in b.GetFootprints():
 for field in f.GetFields():
  if field.GetName() not in ['Reference','Value']:field.SetVisible(False)
 if f.GetReference() in ['U7','C19','X1','C18','D5','D6','C3','C5','C6','C11','L1','C12','C13']:
  x,y=xy(f.GetPosition());f.Reference().SetPosition(pt(x,y-2.5 if f.GetReference() not in ['L1','U7'] else y-4));f.Reference().SetTextSize(pt(.65,.65))
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
print('Final copper and ground stitching applied')
