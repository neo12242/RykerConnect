from pathlib import Path
import sys,pcbnew as p,json
R=Path(__file__).resolve().parent/('REV05-'+sys.argv[1]);b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(a):return round(p.ToMM(a.x),4),round(p.ToMM(a.y),4)
mapping={(175.8,92.9):(175.8,91.1)}
if sys.argv[1]=='USB':mapping[(164.8,86.1)]=(164.3,85.8)
for t in b.GetTracks():
 if t.GetNetname()!='GND':continue
 if type(t).__name__=='PCB_VIA' and xy(t.GetPosition()) in mapping:t.SetPosition(pt(*mapping[xy(t.GetPosition())]))
 if type(t).__name__=='PCB_TRACK':
  if xy(t.GetStart()) in mapping:t.SetStart(pt(*mapping[xy(t.GetStart())]))
  if xy(t.GetEnd()) in mapping:t.SetEnd(pt(*mapping[xy(t.GetEnd())]))
positions={'C19':(170.3,79.5),'C18':(169.6,82.2),'C6':(162.4,91),'D5':(175,81.5)}
for f in b.GetFootprints():
 if f.GetReference() in positions:f.Reference().SetPosition(pt(*positions[f.GetReference()]))
 p.PCB_IO_KICAD_SEXPR().FootprintSave(str(R/'RykerConnect.pretty'),f)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
