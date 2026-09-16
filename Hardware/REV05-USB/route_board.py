"""Critical local loops fixed before routing remaining nets with Freerouting."""
from pathlib import Path
import json,re
import pcbnew as p
R=Path(__file__).resolve().parent;V=R/'validation'
b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'));fps={f.GetReference():f for f in b.GetFootprints()}
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def pad(ref,n):return next(v for v in fps[ref].Pads() if v.GetNumber()==str(n))
def xy(ref,n):a=pad(ref,n).GetPosition();return (p.ToMM(a.x),p.ToMM(a.y))
def route(net,points,width=.4,layer=p.F_Cu):
 for a,z in zip(points,points[1:]):
  t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*z));t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(net));t.SetLocked(True);b.Add(t)
def via(net,at):
 v=p.PCB_VIA(b);v.SetPosition(pt(*at));v.SetWidth(p.FromMM(.65));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet(net));v.SetLocked(True);b.Add(v)
route('RTC_OSCI',[xy('U3',1),(169,84.095),xy('X1',1)],.25)
route('RTC_OSCI',[xy('X1',1),(166.225,83.975),xy('C18',1)],.25)
route('RTC_OSCO',[xy('U3',2),(170,85.365),(168.115,87.25),xy('X1',2)],.25)
route('+3.3V',[xy('U7',2),(155,83),xy('C5',1)],.6)
route('+5V_MODULE',[xy('U7',3),(164.15,79.625),xy('C19',1)],.6)
# Planes stop short of the module antenna projection; no metal under antenna.
route('DSP_RES',[xy('J3',5),(104.5,112.5)],.25);via('DSP_RES',(104.5,112.5))

for layer in [p.F_Cu,p.B_Cu]:
 zone=p.ZONE(b);zone.SetLayer(layer);zone.SetNet(b.FindNet('GND'));zone.SetLocalClearance(p.FromMM(.2));zone.SetThermalReliefGap(p.FromMM(.25));zone.SetThermalReliefSpokeWidth(p.FromMM(.3));zone.SetPadConnection(p.ZONE_CONNECTION_THERMAL)
 out=zone.Outline();out.NewOutline()
 for x,y in [(90,71.5),(191,71.5),(191,116.2),(90,116.2)]:out.Append(p.FromMM(x),p.FromMM(y))
 b.Add(zone)
 zone.SetIslandRemovalMode(p.ISLAND_REMOVAL_MODE_ALWAYS)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
assert p.ExportSpecctraDSN(b,str(V/'carrier.dsn'))
s=(V/'carrier.dsn').read_text();s=s.replace('(width 150)','(width 250)').replace('(clearance 37.5 (type smd_smd))','(clearance 150 (type smd_smd))')
power={'+12V','+12V_PROTECTED','+3.3V','+5V_MODULE','+5V_EXTERNAL'}
match=re.search(r'\(class kicad_default[\s\S]*?\(circuit',s); old=match.group();new=old
for net in power:new=new.replace(' '+net+' ',' ')
s=s.replace(old,new)
s=s.replace('    )\n  )\n  (wiring','    )\n    (class carrier_power +5V_EXTERNAL +3.3V +5V_MODULE (circuit (use_via "Via[0-1]_650:300_um")) (rule (width 500) (clearance 150)))\n  )\n  (wiring')
(V/'carrier.dsn').write_text(s)
print('Critical loops and ground returns fixed; DSN exported.')
