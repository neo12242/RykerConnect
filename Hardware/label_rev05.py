from pathlib import Path
import sys,pcbnew as p
R=Path(__file__).resolve().parent/('REV05-'+sys.argv[1]);b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
for d in b.GetDrawings():
 if isinstance(d,p.PCB_TEXT) and d.GetText() in ['12V+','5V ONLY']:
  d.SetText('J1 1:'+('12V' if sys.argv[1]=='12V' else '5V')+' 2:GND');d.SetTextSize(p.VECTOR2I(p.FromMM(.65),p.FromMM(.65)));d.SetPosition(p.VECTOR2I(p.FromMM(119),p.FromMM(113.6)))
p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
