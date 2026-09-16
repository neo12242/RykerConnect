from pathlib import Path
import sys,pcbnew as p
R=Path(__file__).resolve().parent/('REV05-'+sys.argv[1]);b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(a):return round(p.ToMM(a.x),4),round(p.ToMM(a.y),4)
mapping={(165.8,85.3):(164.8,86.1)}
if sys.argv[1]=='USB':mapping[(165.0,89.5)]=(164.8,89.2)
for t in b.GetTracks():
 if t.GetNetname()!='GND':continue
 if type(t).__name__=='PCB_VIA' and xy(t.GetPosition()) in mapping:t.SetPosition(pt(*mapping[xy(t.GetPosition())]))
 if type(t).__name__=='PCB_TRACK':
  if xy(t.GetStart()) in mapping:t.SetStart(pt(*mapping[xy(t.GetStart())]))
  if xy(t.GetEnd()) in mapping:t.SetEnd(pt(*mapping[xy(t.GetEnd())]))
t=p.PCB_TRACK(b);t.SetStart(pt(175.775,92));t.SetEnd(pt(175.8,92.9));t.SetWidth(p.FromMM(.3));t.SetLayer(p.F_Cu);t.SetNet(b.FindNet('GND'));b.Add(t)
v=p.PCB_VIA(b);v.SetPosition(pt(175.8,92.9));v.SetWidth(p.FromMM(.65));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet('GND'));b.Add(v)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
