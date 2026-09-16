"""Final idempotent artwork and local-library synchronization."""
from pathlib import Path
import pcbnew as p
import json
R=Path(__file__).resolve().parent;b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
positions={'BT1':(105,96),'J6':(172,72.2),'J7':(157.7,95.7),'C4':(166,95.2),'D3':(152,81),'J3':(104,109.3),'J4':(130,109.3),'J5':(138,109.3)}
for f in b.GetFootprints():
 ref=f.GetReference()
 if ref in positions:f.Reference().SetPosition(pt(*positions[ref]))
 if ref in ['H3','H4','H5','H6'] and not any(g.GetLayer()==p.F_CrtYd for g in f.GraphicalItems()):
  s=p.PCB_SHAPE(f);s.SetShape(p.SHAPE_T_CIRCLE);s.SetCenter(f.GetPosition());s.SetEnd(f.GetPosition()+pt(1.3,0));s.SetWidth(p.FromMM(.05));s.SetLayer(p.F_CrtYd);f.Add(s)
 if ref.startswith('H') and ref not in ['H1','H2']:f.SetFPID(p.LIB_ID('RykerConnect',ref+'_ModuleRetention'))
 p.PCB_IO_KICAD_SEXPR().FootprintSave(str(R/'RykerConnect.pretty'),f)
for d in b.GetDrawings():
 if isinstance(d,p.PCB_TEXT) and d.GetText().startswith('Waveshare 28836'):d.SetText('')
 if isinstance(d,p.PCB_TEXT) and d.GetText() in ['OLED','LIGHT','SENSOR']:
  d.SetPosition(pt(*{'OLED':(95.5,111.5),'LIGHT':(130,107),'SENSOR':(137,107)}[d.GetText()]))
p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
project=R/'RykerConnect.kicad_pro';data=json.loads(project.read_text())
rules=data['board']['design_settings']['rule_severities']
data['board']['design_settings']['rule_severities']={k:('warning' if v=='ignore' else v) for k,v in rules.items()}
project.write_text(json.dumps(data,indent=2))
print('Silkscreen and local footprint library updated.')
