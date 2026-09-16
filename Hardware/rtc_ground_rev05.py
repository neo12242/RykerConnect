from pathlib import Path
import sys,pcbnew as p
R=Path(__file__).resolve().parent/('REV05-'+sys.argv[1]);b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
old=p.VECTOR2I(p.FromMM(175.8),p.FromMM(91.1));new=p.VECTOR2I(p.FromMM(176.8),p.FromMM(92))
for t in b.GetTracks():
 if t.GetNetname()!='GND':continue
 if type(t).__name__=='PCB_VIA' and t.GetPosition()==old:t.SetPosition(new)
 if type(t).__name__=='PCB_TRACK' and t.GetEnd()==old:t.SetEnd(new)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
