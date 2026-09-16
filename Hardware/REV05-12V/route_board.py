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
route('BUCK_SW',[xy('U5',5),xy('L1',1)],.5)
route('BUCK_SW',[xy('C11',2),(152,107)],.25)
route('BUCK_BST',[xy('U5',6),(149.8,105.225),xy('C11',1)],.25)
route('+12V_PROTECTED',[xy('C9',1),(145.5,108.475),xy('U5',3)],.5)
route('+12V_PROTECTED',[xy('U5',3),xy('U5',2)],.35)
route('+12V_PROTECTED',[xy('C9',1),(140.7,108.475),(140.7,103),xy('C10',1)],.5)
route('+5V_BUCK',[xy('L1',2),(161,107.95),xy('C12',1),xy('C13',1)],.8)
route('+5V_BUCK',[xy('U5',1),(146.8625,101),(168,101),(168,107.95),xy('C13',1)],.25)
route('+5V_BUCK',[xy('C13',1),(170,107.95),xy('J8',1)],.6)
# Fast input and TVS loops close to J1, with wide common-anode copper.
route('+12V',[xy('J1',1),(120,97),(103.85,97),xy('D2',1)],.8)
route('TVS_MID',[xy('D2',2),(111,100),(111,109),(103.85,109),xy('D4',2)],.8)
route('GND',[xy('D4',1),(108.15,107.7)],.8);via('GND',(108.15,107.7))
route('+12V',[xy('U6',1),xy('U6',2)],.35)
route('+12V_PROTECTED',[xy('U6',15),xy('U6',16)],.35)
route('+12V_PROTECTED',[xy('U6',15),(147.5,83.375),xy('D3',1)],.5)
route('GND',[xy('J5',1),(136.5,112.5)],.3);via('GND',(136.5,112.5))
via('GND',(160,94.2))
route('+12V',[(134.1,82.725),(132,82.4)],.35);via('+12V',(132,82.4));via('+12V',(128.5,85.2))
route('+12V',[(132,82.4),(128.5,85.2)],.5,p.B_Cu)
route('+12V',[(128.5,85.2),(125,85.475)],.5)
route('PROT_ILIM',[(139.9,85.975),(141.15,85.975)],.25);via('PROT_ILIM',(141.15,85.975));via('PROT_ILIM',(141.7,90))
route('PROT_ILIM',[(141.15,85.975),(141.7,90)],.25,p.B_Cu)
route('PROT_ILIM',[(141.7,90),(142.175,89)],.25)
# Ground vias beside local capacitor return pads, never through solder paste.
for ref,n,at in [('C9',2,(142,105.525)),('C10',2,(145.775,102)),('U5',4,(149.5,109)),('C12',2,(163,104.8)),('C13',2,(166,104.8)),('C14',2,(124,77.525)),('C17',2,(124,82.525)),('C15',2,(150,86.05)),('D3',2,(150,78.8)),('U6',9,(140.7,88.2))]:
 route('GND',[xy(ref,n),at],.35);via('GND',at)
route('RTC_OSCI',[xy('U3',1),(169,84.095),xy('X1',1)],.25)
route('RTC_OSCI',[xy('X1',1),(166.225,83.975),xy('C18',1)],.25)
route('RTC_OSCO',[xy('U3',2),(170,85.365),(168.115,87.25),xy('X1',2)],.25)
route('+3.3V',[xy('U7',2),(155,83),xy('C5',1)],.6)
route('+5V_MODULE',[xy('U7',3),(164.15,79.625),xy('C19',1)],.6)
# Planes stop short of the module antenna projection; no metal under antenna.
route('DSP_RES',[xy('J3',5),(104.5,112.5)],.25);via('DSP_RES',(104.5,112.5))
pad('C10',2).SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
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
s=s.replace('    )\n  )\n  (wiring','    )\n    (class carrier_power +12V +12V_PROTECTED +3.3V +5V_MODULE (circuit (use_via "Via[0-1]_650:300_um")) (rule (width 500) (clearance 150)))\n  )\n  (wiring')
(V/'carrier.dsn').write_text(s)
print('Critical loops and ground returns fixed; DSN exported.')
