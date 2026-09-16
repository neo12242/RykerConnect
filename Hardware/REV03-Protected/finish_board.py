"""Apply reviewed finishing changes after importing protection-only routes."""
from pathlib import Path
import pcbnew as p
R=Path(__file__).resolve().parent
b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'));fps={f.GetReference():f for f in b.GetFootprints()}
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(v):return tuple(round(p.ToMM(x),6) for x in (v.x,v.y))
def via(at):
 v=p.PCB_VIA(b);v.SetPosition(pt(*at));v.SetWidth(p.FromMM(.6));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet('GND'));b.Add(v)
def track(a,z,layer,width):
 t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*z));t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet('GND'));b.Add(t)
# Separate capacitor courtyards by 0.2mm; move routed pad endpoints with C17.
if abs(p.ToMM(fps['C17'].GetPosition().x)-153.5)<.0001:
 for t in b.GetTracks():
  for getter,setter in [(t.GetStart,t.SetStart),(t.GetEnd,t.SetEnd)]:
   x,y=xy(getter())
   if (x,y)==(154.975,76.5):setter(pt(x+.3,y))
 fps['C17'].SetPosition(pt(153.8,76.5))
# Return U6 ground to the planes without joining isolated RTN.
track((138.1,83.275),(138.1,83.85),p.B_Cu,.3);via((138.1,83.85))
# Reposition the displaced converter return via, keeping its short local return.
for t in b.GetTracks():
 if t.m_Uuid.AsString()=='f08c6748-26ba-4565-86ae-302369ebcdd8':
  t.SetEnd(pt(152.6,87.5794))
track((152.6,87.5794),(152.6,86.5),p.F_Cu,.3);via((152.6,86.5))
ref_positions={'U6':(141,77.3),'D2':(151,85.5),'D4':(151,97),'D3':(130,81),'C14':(149,74.3),'C17':(153.8,74.3),'C15':(135,74.8),'C16':(135.5,81),'R7':(147,79.1),'R8':(147.2,84.3),'R9':(144.5,87.3),'R10':(135.5,86.4)}
for ref,at in ref_positions.items():
 t=fps[ref].Reference();t.SetPosition(pt(*at));t.SetTextAngle(p.EDA_ANGLE(0,p.DEGREES_T));t.SetMirrored(True);t.SetVisible(True)
# Move decorative back-side labels clear of the added circuit and retire the
# old REV02 artwork. Original author attribution remains on the board.
fps['kibuzzard-66281DA5'].SetPosition(pt(112,86))
fps['kibuzzard-66281FA1'].SetPosition(pt(112,90.5))
old_revision=fps['kibuzzard-6647CFBD'];assert not list(old_revision.Pads());b.Delete(old_revision)
for item in b.GetDrawings():
 if isinstance(item,p.PCB_TEXT) and item.GetText()=='REV03 PROTECTED':item.SetPosition(pt(112,95))
b.GetTitleBlock().SetRevision('REV03');b.GetTitleBlock().SetDate('2026-09-13')
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
print('Applied courtyard separation, explicit ground returns and bottom references.')

