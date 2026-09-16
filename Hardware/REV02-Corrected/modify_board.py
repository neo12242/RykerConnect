"""Rebuild the corrected board from untouched REV02 input, using KiCad 10 pcbnew.
Run with KiCad's bundled python.exe. Source board is never saved.
"""
from pathlib import Path
import hashlib, json
import pcbnew as p
import gc
gc.disable()  # Keep SWIG proxy cycles alive for this short-lived native generation process.

ROOT=Path(__file__).resolve().parent
SOURCE=(Path(__file__).resolve().parents[1] / 'KiCad/RykerConnect-REV02')
b=p.LoadBoard(str(SOURCE/'RykerConnect.kicad_pcb'))
sensor_footprint=p.FootprintLoad(r'C:\Program Files\KiCad\10.0\share\kicad\footprints\Connector_JST.pretty','JST_SH_SM04B-SRSS-TB_1x04-1MP_P1.00mm_Horizontal')
fps={f.GetReference():f for f in b.GetFootprints()}
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(v):return tuple(round(p.ToMM(a),6) for a in (v.x,v.y))
def route(net,points,layer=p.F_Cu,width=.2):
    for a,z in zip(points,points[1:]):
        t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*z));t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(net));b.Add(t)
def via(net,at):
    v=p.PCB_VIA(b);v.SetPosition(pt(*at));v.SetWidth(p.FromMM(.6));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet(net));b.Add(v)

# Move the existing three connected D- endpoints together, preserving topology.
for t in b.GetTracks():
    if t.GetNetname()=='/D- Con':
        for get,setter in [(t.GetStart,t.SetStart),(t.GetEnd,t.SetEnd)]:
            x,y=xy(get())
            if abs(y-96.2594)<.00001 and 160.8<x<161.7:setter(pt(x,y-.075))
    if t.GetNetname()=='SCL':
        for get,setter in [(t.GetStart,t.SetStart),(t.GetEnd,t.SetEnd)]:
            x,y=xy(get())
            if abs(y-73.9294)<.00001 and 92.8<x<93.7:setter(pt(x,y-.025));t.SetWidth(p.FromMM(.13))

# All four shell tabs are one shield net. JP1 remains OPEN to ground.
shield='Net-(J2-SHIELD)'
route(shield,[(157.2461,97.8794),(157.2461,102.0594),(157.2461,103.9),(165.8861,103.9),(165.8861,102.0594)],p.B_Cu,.25)
route(shield,[(157.2461,97.8794),(157.2461,96.55),(159.55,96.55),(159.75,96.465),(161.6,96.465),(161.9,96.55),(165.8861,96.55),(165.8861,97.8794)],p.B_Cu,.15)

# Replace four oversized probe pads with a keyed, assembled external I2C port.
# Existing SCL/SDA branches terminate at the same electrical source points.
for ref in ('TP1','TP2','TP3','TP4'):b.Delete(fps.pop(ref))
for item in list(b.GetDrawings()):
    if item.m_Uuid.AsString() in ['f9bd844e-cd95-4454-bb05-bcb83eaf2ab6','7f235748-619a-492f-bb12-ca0a03fc0c68','d3172ae2-9fa2-4877-b8a9-4acd5b14d08f','2f3ba889-d784-4604-b63b-8dd134a351ab']:b.Delete(item)
label=p.PCB_TEXT(b);label.SetText('ENV 3V3');label.SetPosition(pt(93.5,95.1));label.SetTextSize(pt(.7,.7));label.SetTextThickness(p.FromMM(.12));label.SetLayer(p.F_SilkS);b.Add(label)
for t in list(b.GetTracks()):
    if t.m_Uuid.AsString() in ['7f0fed41-da22-46e8-95ac-6401a1b720e5','f4f4a66b-6d19-4195-bc5a-4ee0c3e1ce17','01357403-09ae-4457-b634-3ccc59a50e8f','ab87998b-3c01-4fc4-9699-a5faacce6506','56fd1f16-20c8-4399-ac3d-8c15128c34e5']:b.Delete(t)
f=sensor_footprint
f.SetReference('J5');f.SetValue('SM04B-SRSS-TB(LF)(SN)');f.SetPosition(pt(92.8,89));f.SetOrientationDegrees(-90)
f.SetPath(p.KIID_PATH('/0f8d191d-1efb-46e1-a4c8-e725950f66f5/b7a356b0-6575-4fca-9a0d-5b80de5f6094'))
b.Add(f);fps['J5']=f
for pad in f.Pads():
    net={'1':'GND','2':'+3.3V','3':'SDA','4':'SCL','MP':'GND'}[pad.GetNumber()];pad.SetNet(b.FindNet(net))
f.Reference().SetPosition(pt(94,94));f.Reference().SetTextAngle(p.EDA_ANGLE(0,p.DEGREES_T));f.Value().SetVisible(False)
# Remove obsolete ground stitches/links inside the connector routing corridor;
# all retained ground pads remain connected by the filled top/bottom planes.
for t in list(b.GetTracks()):
    if t.GetNetname()=='GND':
        x,y=xy(t.GetStart())
        if (isinstance(t,p.PCB_VIA) and 94<x<97 and 85<y<93) or (t.GetLayer()==p.B_Cu and 94<x<99 and abs(y-92.2594)<.001):b.Delete(t)
route('+3.3V',[(94.8,88.5),(95.6,88.5)],width=.3);via('+3.3V',(95.6,88.5))
route('+3.3V',[(95.6,88.5),(95.6,93.7794),(98.6561,93.7794)],p.B_Cu,.3);via('+3.3V',(98.6561,93.7794))
route('SDA',[(94.8,89.5),(97.1,89.5)],width=.2);via('SDA',(97.1,89.5))
route('SDA',[(97.1,89.5),(97.1,83.3255),(99.0861,81.3394)],p.B_Cu);via('SDA',(99.0861,81.3394))
route('SCL',[(94.8,90.5),(96.2,90.5)],width=.2);via('SCL',(96.2,90.5))
route('SCL',[(96.2,90.5),(96.2,85.6633),(95.0161,84.4794)],p.B_Cu);via('SCL',(95.0161,84.4794))

for pad in fps['J5'].Pads():
    if pad.GetNumber()=='MP':pad.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
for t in list(b.GetTracks()):
    if isinstance(t,p.PCB_VIA) and t.GetNetname()=='GND' and xy(t.GetPosition()) in [(164.7561,95.8544),(164.7561,96.5794)]:b.Delete(t)

# Connector drawing limits the PCB edge distance from the retention peg to 10.16mm.
# Original distance was 10.38mm; shift the complete connector 0.5mm toward the edge.
j1=fps['J1'];j1.SetPosition(pt(145.1561,90.5794))
route('+12V',[(145.1561,90.0794),(145.1561,90.5794)],width=.5)

# Bare features never belong in the assembly files.
for ref,f in fps.items():
    if ref.startswith(('TP','H','kibuzzard')):f.SetAttributes(f.GetAttributes()|p.FP_EXCLUDE_FROM_BOM|p.FP_EXCLUDE_FROM_POS_FILES)

# Remove stale teardrops. These are generated copper, not hand-routed topology;
# zone filler will create normal clearances around the corrected geometry.
for z in list(b.Zones()):
    if z.IsTeardropArea():b.Delete(z)

lib=ROOT/'RykerConnect.pretty';lib.mkdir(exist_ok=True)
mapping={}
for ref,f in fps.items():
    if ref.startswith('kibuzzard'):continue
    old=f.GetFPID().GetLibItemName();name=str(old)
    # Name by original library and footprint, preserving distinct embedded copies.
    name=ref+'_'+name
    f.SetFPID(p.LIB_ID('RykerConnect',name));p.PCB_IO_KICAD_SEXPR().FootprintSave(str(lib),f);mapping[ref]='RykerConnect:'+name
(ROOT/'footprint-map.json').write_text(json.dumps(mapping,indent=2))
(ROOT/'fp-lib-table').write_text('(fp_lib_table (version 7) (lib (name "RykerConnect")(type "KiCad")(uri "${KIPRJMOD}/RykerConnect.pretty")(options "")(descr "Verified embedded REV02 footprints")))\n')
p.ZONE_FILLER(b).Fill(b.Zones())
p.SaveBoard(str(ROOT/'RykerConnect.kicad_pcb'),b)
print('Saved corrected board',len(fps),'footprints')




