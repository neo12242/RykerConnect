"""Derive protected prototype from preserved REV02; run with KiCad Python."""
from pathlib import Path
import json, gc
import pcbnew as p
from protection_defs import *
gc.disable()
R=Path(__file__).resolve().parent; S=R.parent/'REV02-Corrected'
b=p.LoadBoard(str(S/'RykerConnect.kicad_pcb'))
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def xy(v):return tuple(round(p.ToMM(a),6) for a in (v.x,v.y))
for net in {v for part in PARTS for v in part['nets'].values() if v}:
 if b.FindNet(net) is None or b.FindNet(net).GetNetCode()<0:b.Add(p.NETINFO_ITEM(b,net))
fps={f.GetReference():f for f in b.GetFootprints()}
for n,(name,ty,net) in PINS.items():
 if net is None:b.Add(p.NETINFO_ITEM(b,f'unconnected-(U6-{name}-Pad{n})'))
for t in list(b.GetTracks()):
 if t.GetNetname()=='+12V':b.Delete(t)
for pad in fps['D1'].Pads():
 if pad.GetNumber()=='1':pad.SetNet(b.FindNet('+12V_PROTECTED'))
def layers(items):
 ls=p.LSET()
 for layer in items:ls.AddLayer(layer)
 return ls
def custom_ic():
 f=p.FOOTPRINT(b)
 f.SetAttributes(p.FP_SMD)
 for n in range(1,17):
  x=-2.9 if n<=8 else 2.9;y=-2.275+.65*(n-1 if n<=8 else 16-n)
  pad=p.PAD(f);pad.SetNumber(str(n));pad.SetAttribute(p.PAD_ATTRIB_SMD);pad.SetShape(p.PAD_SHAPE_RECT);pad.SetPosition(pt(x,y));pad.SetSize(pt(1.5,.45));pad.SetLayerSet(layers([p.F_Cu,p.F_Mask,p.F_Paste]));f.Add(pad)
 ep=p.PAD(f);ep.SetNumber('17');ep.SetAttribute(p.PAD_ATTRIB_SMD);ep.SetShape(p.PAD_SHAPE_RECT);ep.SetSize(pt(3.4,5));ep.SetLayerSet(layers([p.F_Cu]));f.Add(ep)
 mask=p.PAD(f);mask.SetNumber('');mask.SetAttribute(p.PAD_ATTRIB_SMD);mask.SetShape(p.PAD_SHAPE_RECT);mask.SetSize(pt(3.3,3.3));mask.SetLayerSet(layers([p.F_Mask]));f.Add(mask)
 for x in [-.85,.85]:
  for y in [-.85,.85]:
   paste=p.PAD(f);paste.SetNumber('');paste.SetAttribute(p.PAD_ATTRIB_SMD);paste.SetShape(p.PAD_SHAPE_RECT);paste.SetSize(pt(1.25,1.25));paste.SetPosition(pt(x,y));paste.SetLayerSet(layers([p.F_Paste]));f.Add(paste)
 for layer,xx,yy in [(p.F_CrtYd,3.9,2.8),(p.F_Fab,2.2,2.5)]:
  sh=p.PCB_SHAPE(f);sh.SetShape(p.SHAPE_T_RECT);sh.SetStart(pt(-xx,-yy));sh.SetEnd(pt(xx,yy));sh.SetLayer(layer);sh.SetWidth(p.FromMM(.05 if layer==p.F_CrtYd else .1));f.Add(sh)
 mark=p.PCB_SHAPE(f);mark.SetShape(p.SHAPE_T_CIRCLE);mark.SetCenter(pt(-3.7,-2.8));mark.SetEnd(pt(-3.55,-2.8));mark.SetLayer(p.F_SilkS);mark.SetWidth(p.FromMM(.12));f.Add(mark)
 return f
for part in PARTS:
 ref=part['refs'];lib,item=part['fp'].split(':') if ':' in part['fp'] else ('RykerConnect',part['fp'])
 f=custom_ic() if ref=='U6' else p.FootprintLoad(str(Path(r'C:\Program Files\KiCad\10.0\share\kicad\footprints')/(lib+'.pretty')),item)
 f.SetReference(ref);f.SetValue(part['value']);f.SetFPID(p.LIB_ID('RykerConnect',ref+'_'+item))
 b.Add(f)
 f.SetPosition(pt(*part['pos'][:2]));f.SetOrientationDegrees(part['pos'][2]);f.Flip(f.GetPosition(),False)
 f.SetPath(p.KIID_PATH('/'+ROOT_ID+'/'+SHEET_ID+'/'+ident(ref)))
 fps[ref]=f
 f.Reference().SetTextSize(pt(.7,.7));f.Reference().SetTextThickness(p.FromMM(.12));f.Reference().SetVisible(False);f.Value().SetVisible(False)
 for pad in f.Pads():
  n=pad.GetNumber()
  if n and part['nets'].get(n):pad.SetNet(b.FindNet(part['nets'][n]))
  elif n and ref=='U6':pad.SetNet(b.FindNet(f'unconnected-(U6-{PINS[n][0]}-Pad{n})'))
  if n and ref=='U6':pad.SetPinFunction(PINS[n][0]);pad.SetPinType(PINS[n][1])
  if n and n=='17':pad.SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)
 for k,key in [('MPN','mpn'),('LCSC','code'),('Manufacturer','manufacturer')]:f.SetField(k,part[key]);f.GetField(k).SetVisible(False)
 # Save canonical front-facing footprint library; board instance remains bottom.
 f.Flip(f.GetPosition(),False);original_rotation=f.GetOrientationDegrees();f.SetOrientationDegrees(0)
 p.PCB_IO_KICAD_SEXPR().FootprintSave(str(R/'RykerConnect.pretty'),f)
 f.SetOrientationDegrees(original_rotation);f.Flip(f.GetPosition(),False)
# Remove only ground stitching that physically conflicts with new non-ground
# bottom copper; preserve converter return vias and all unrelated routing.
for t in list(b.GetTracks()):
 if t.GetNetname()!='GND' or not (isinstance(t,p.PCB_VIA) or t.GetLayer()==p.B_Cu):continue
 conflict=False
 for part in PARTS:
  for pad in fps[part['refs']].Pads():
   if not pad.IsOnLayer(p.B_Cu) or pad.GetNetname()=='GND':continue
   box=pad.GetBoundingBox();box.Inflate(p.FromMM(.16))
   if box.Intersects(t.GetBoundingBox()):conflict=True
 if conflict:b.Delete(t)
def route(net,points,layer=p.B_Cu,width=.25):
 for a,z in zip(points,points[1:]):
  t=p.PCB_TRACK(b);t.SetStart(pt(*a));t.SetEnd(pt(*z));t.SetWidth(p.FromMM(width));t.SetLayer(layer);t.SetNet(b.FindNet(net));b.Add(t)
def padxy(ref,num):return xy(next(x for x in fps[ref].Pads() if x.GetNumber()==str(num)).GetPosition())
def via(net,at):
 v=p.PCB_VIA(b);v.SetPosition(pt(*at));v.SetWidth(p.FromMM(.6));v.SetDrill(p.FromMM(.3));v.SetViaType(p.VIATYPE_THROUGH);v.SetLayerPair(p.F_Cu,p.B_Cu);v.SetNet(b.FindNet(net));b.Add(v)
# Hand-routed local protection connections are in a separate auditable data file.
path=R/'routes.json'
if path.exists():
 for r in json.loads(path.read_text()):
  points=[padxy(*x) if isinstance(x[0],str) else tuple(x) for x in r['points']]
  route(r['net'],points,p.F_Cu if r.get('layer')=='F' else p.B_Cu,r.get('width',.25))
  for at in r.get('vias',[]):via(r['net'],at)
# Mark the new revision on the back, without moving original attribution.
txt=p.PCB_TEXT(b);txt.SetText('REV03 PROTECTED');txt.SetPosition(pt(122,100));txt.SetTextSize(pt(.8,.8));txt.SetTextThickness(p.FromMM(.12));txt.SetLayer(p.B_SilkS);txt.SetMirrored(True);b.Add(txt)
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
positions={part['refs']:{pad.GetNumber():xy(pad.GetPosition()) for pad in fps[part['refs']].Pads() if pad.GetNumber()} for part in PARTS}
(R/'validation/new-pad-positions.json').write_text(json.dumps(positions,indent=2))
print(json.dumps(positions))

