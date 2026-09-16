"""Apply module retention, RF keepout and assembly documentation to routed board."""
from pathlib import Path
import pcbnew as p
R=Path(__file__).resolve().parent;b=p.LoadBoard(str(R/'RykerConnect.kicad_pcb'))
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
for ref,x,y in [('H3',127.8,75.8),('H4',187.5,75.8),('H5',127.8,97.6),('H6',187.5,97.6)]:
 f=p.FOOTPRINT(b);b.Add(f);f.SetReference(ref);f.SetValue('Module retention - 2mm NPTH');f.SetPosition(pt(x,y))
 f.SetAttributes(p.FP_BOARD_ONLY|p.FP_EXCLUDE_FROM_BOM|p.FP_EXCLUDE_FROM_POS_FILES)
 f.Reference().SetVisible(False);f.Value().SetVisible(False)
 pad=p.PAD(f);pad.SetAttribute(p.PAD_ATTRIB_NPTH);pad.SetShape(p.PAD_SHAPE_CIRCLE);pad.SetSize(pt(2,2));pad.SetDrillSize(pt(2,2));pad.SetLayerSet(p.LSET.AllCuMask());pad.SetPosition(pt(x,y));f.Add(pad)
z=p.ZONE(b);z.SetIsRuleArea(True);z.SetLayerSet(p.LSET.AllCuMask());z.SetDoNotAllowTracks(True);z.SetDoNotAllowVias(True);z.SetDoNotAllowPads(True);z.SetDoNotAllowFootprints(False);z.SetDoNotAllowZoneFills(True);z.SetZoneName('Module antenna - copper free')
o=z.Outline();o.NewOutline()
for x,y in [(182,77.2),(191.4561,77.2),(191.4561,96.2),(182,96.2)]:o.Append(int(p.FromMM(x)),int(p.FromMM(y)))
b.Add(z)
def line(a,c,layer):
 s=p.PCB_SHAPE(b);s.SetShape(p.SHAPE_T_SEGMENT);s.SetStart(pt(*a));s.SetEnd(pt(*c));s.SetWidth(p.FromMM(.15));s.SetLayer(layer);b.Add(s)
for a,c in [((126,74),(189.3,74)),((189.3,74),(189.3,99.4)),((189.3,99.4),(126,99.4)),((126,99.4),(126,74))]:line(a,c,p.Dwgs_User)
def label(text,x,y,size=.8,layer=p.F_SilkS):
 t=p.PCB_TEXT(b);t.SetText(text);t.SetPosition(pt(x,y));t.SetTextSize(pt(size,size));t.SetTextThickness(p.FromMM(.13));t.SetLayer(layer);b.Add(t)
label('RykerConnect REV05 USB',105,73,1)
label('5V ONLY',121,111.5);label('OLED',105,115.6);label('LIGHT',130.5,115.6);label('SENSOR',139,115.6)
label('J8: REMOVE FOR USB',174,113.8,.8)
label('USB <  N16R8-M  > ANT',157,72.2,.8)
label('Waveshare 28836 - MODULE ABOVE CARRIER',156,88,1,p.Dwgs_User)
for f in b.GetFootprints():
 for g in list(f.GraphicalItems()):
  if type(g).__name__=='PCB_TEXT':g.SetText('')
 if f.GetReference().startswith('H'):
  f.Reference().SetVisible(False);f.Value().SetVisible(False)
 else:
  f.Reference().SetLayer(p.F_SilkS)
  f.Reference().SetTextAngle(p.EDA_ANGLE(0,p.DEGREES_T))
  positions={'J6':(157.7,76.9),'J7':(157.7,96.4),'C14':(123,79),'C17':(123,84),'R7':(132,80),'R8':(132,83.4),'R9':(132.7,87),'D3':(151,81),'C15':(151.5,87),'C16':(143,84.5),'R10':(143,90.5),'C5':(159,78),'C6':(159,81),'R4':(154,91),'R3':(158,94.7),'U2':(163,89.5),'C4':(163,96.6),'R6':(169.8,93),'C3':(165,82),'J3':(104,110),'J4':(130,110),'J5':(139,110),'C9':(141,107),'C10':(145,101.5),'U5':(149,110),'C11':(153,104),'L1':(157,103.5),'C12':(163,110),'C13':(167,110),'J8':(175,107)}
  if f.GetReference() in positions:f.Reference().SetPosition(pt(*positions[f.GetReference()]))
p.ZONE_FILLER(b).Fill(b.Zones());p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
print('Module outline, retention holes and RF keepout applied.')





