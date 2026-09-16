"""Build carrier schematic and unrouted board with native KiCad Python."""
from pathlib import Path
import json,copy,uuid,gc,xml.etree.ElementTree as ET
import pcbnew as p
from sexpr import *
R=Path(__file__).resolve().parent;S=R.parent/'REV03-Protected';V=R/'validation';V.mkdir(exist_ok=True)
ROOT='b3e6de9e-c7cd-4419-8e94-64236783f104'
def uid(key):return str(uuid.uuid5(uuid.UUID(ROOT),key))
parts=json.loads((R/'design.json').read_text())
def pt(x,y):return p.VECTOR2I(p.FromMM(x),p.FromMM(y))
def fx(x):return str(round(x,4))
def grid(x):return round(x/1.27)*1.27
def eff(size=.9):return ['effects',['font',['size',fx(size),fx(size)]]]
def prop_(k,v,x,y,hide=False):return ['property',Q(k),Q(v),['at',fx(x),fx(y),'0'],eff()+([['hide','yes']] if hide else [])]
def text(s,t,x,y,size=1.2):s.append(['text',Q(t),['at',fx(x),fx(y),'0'],['effects',['font',['size',fx(size),fx(size)]],['justify','left']],['uuid',Q(uid(t+str(x)+str(y)))]])
def wire(s,x,y,xx,yy,key):s.append(['wire',['pts',['xy',fx(x),fx(y)],['xy',fx(xx),fx(yy)]],['stroke',['width','0'],['type','default']],['uuid',Q(uid(key))]])
def lab(s,net,x,y,key,left=True):s.append(['global_label',Q(net),['shape','bidirectional'],['at',fx(x),fx(y),'180' if left else '0'],eff(.85)+[['justify','right' if left else 'left']],['uuid',Q(uid(key))]])
names={1:'Controller and connectors',2:'Accessory protection',3:'5V regulator and USB isolation',4:'Battery-backed clock and light sensor'}
pages={}
for page,title in names.items():
 sc=['kicad_sch',['version','20250114'],['generator',Q('eeschema')],['uuid',Q(ROOT if page==1 else uid('page'+str(page)))],['paper',Q('A3')],['title_block',['title',Q('RykerConnect - '+title)],['date',Q('2026-09-13')],['rev',Q('REV05-'+json.loads((R/'variant.json').read_text())['variant'])]],['lib_symbols']]
 text(sc,title,20,15,2);pages[page]=sc
 for n in range(2,5):
  if page==1:sc.append(['sheet',['at',fx(20+(n-2)*120),'230'],['size','100','15'],['stroke',['width','0'],['type','default']],['fill',['color','0','0','0','0']],['uuid',Q(uid('sheet'+str(n)))],prop_('Sheetname',names[n],20+(n-2)*120,228),prop_('Sheetfile',f'sheet{n}.kicad_sch',20+(n-2)*120,247),['instances',['project',Q('RykerConnect'),['path',Q('/'+ROOT),['page',Q(str(n))]]]]])
oldnl=ET.parse(S/'validation/netlist.xml');oldtypes={}
for c in oldnl.findall('.//components/comp'):
 src=c.find('libsource');lib=next((l for l in oldnl.findall('.//libparts/libpart') if l.get('lib')==src.get('lib') and l.get('part')==src.get('part')),None)
 if lib is not None:oldtypes[c.get('ref')]={x.get('num'):(x.get('name'),x.get('type')) for x in lib.findall('pins/pin')}
oldb=p.LoadBoard(str(S/'RykerConnect.kicad_pcb')); oldfps={f.GetReference():f for f in oldb.GetFootprints()}
# Fresh copper, same physical outline and original mounting holes.
b=p.LoadBoard(str(S/'RykerConnect.kicad_pcb'))
b.GetDesignSettings().SetAuxOrigin(pt(89.4561,116.7794))
for t in list(b.GetTracks()):b.Delete(t)
for z in list(b.Zones()):b.Delete(z)
for f in list(b.GetFootprints()):
 if f.GetReference() not in ['H1','H2']:b.Delete(f)
for d in list(b.GetDrawings()):
 b.Delete(d)
for a,z in [((89.4561,70.7794),(191.4561,70.7794)),((191.4561,70.7794),(191.4561,116.7794)),((191.4561,116.7794),(89.4561,116.7794)),((89.4561,116.7794),(89.4561,70.7794))]:
 sh=p.PCB_SHAPE(b);sh.SetShape(p.SHAPE_T_SEGMENT);sh.SetStart(pt(*a));sh.SetEnd(pt(*z));sh.SetWidth(p.FromMM(.05));sh.SetLayer(p.Edge_Cuts);b.Add(sh)
library=R/'RykerConnect.pretty';library.mkdir(exist_ok=True)
nlibrary=['kicad_symbol_lib',['version','20241209'],['generator',Q('kicad_symbol_editor')]]
indices={k:0 for k in names};fps={}
for part in parts:
 ref=part['ref'];page=part['page'];sc=pages[page];i=indices[page];indices[page]+=1
 x=50+95*(i%4);y=50+60*(i//4)
 if page==1:
  coords={'J6':(80,65),'J7':(245,65),'J3':(60,160),'J5':(160,160),'R3':(250,150),'R4':(345,150),'J1':(345,185)}
  x,y=coords.get(ref,(345,185))
 x,y=grid(x),grid(y)
 # Derive electrical pin functions independently from original/vendor definitions.
 pinitems=[]
 for num,net in part['nets'].items():
  nm,ty=oldtypes.get(ref,{}).get(num,(num,'passive')) if ref not in ['U3','U5'] else (num,'passive')
  if ref in ['J6','J7','J8']:nm,ty=(net or ('NC_'+num),'passive')
  if ref=='U6':nm,ty=part['pins'][num][:2]
  if ref=='U5':nm,ty=part['pins'][num][:2]
  if part.get('pins') and num in part['pins']:nm,ty=part['pins'][num][:2]
  if ref.startswith(('C','R','L','D')) and not part.get('pins'):nm,ty=(num,'passive')
  pinitems.append((num,nm,ty,net))
 pinitems.sort(key=lambda item:(not item[0].isdigit(),int(item[0]) if item[0].isdigit() else 999))
 part['resolved_pins']={n:[nm,ty] for n,nm,ty,net in pinitems}
 count=len(pinitems);half=(count+1)//2;h=max(5.08,half*2.54/2+2.54)
 sym=['symbol',Q('RykerConnect:'+ref),['pin_names',['offset','0.5']],['in_bom','yes'],['on_board','yes'],prop_('Reference',ref,0,h+5),prop_('Value',part['value'],0,h+2),['symbol',Q(ref+'_0_1'),['rectangle',['start','-10.16',fx(h)],['end','10.16',fx(-h)],['stroke',['width','0'],['type','default']],['fill',['type','background']]]],['symbol',Q(ref+'_1_1')]]
 localfp=ref+'_'+part['fp'].split(':')[-1]
 inst=['symbol',['lib_id',Q('RykerConnect:'+ref)],['at',fx(x),fx(y),'0'],['unit','1'],['in_bom','yes'],['on_board','yes'],['dnp','no'],['uuid',Q(uid(ref))],prop_('Reference',ref,x,y-h-5),prop_('Value',part['value'],x,y-h-2),prop_('Footprint','RykerConnect:'+localfp,x,y,True),prop_('MPN',part['mpn'],x,y,True),prop_('LCSC',part['code'],x,y,True),['instances',['project',Q('RykerConnect'),['path',Q('/'+ROOT+('' if page==1 else '/'+uid('sheet'+str(page)))),['reference',Q(ref)],['unit','1']]]]]
 for j,(n,nm,ty,net) in enumerate(pinitems):
  left=j<half;xx=-12.7 if left else 12.7; yy=(half-1)*1.27-2.54*(j if left else j-half);ang=0 if left else 180
  sym[-1].append(['pin',ty,'line',['at',fx(xx),fx(yy),str(ang)],['length','2.54'],['name',Q(nm),eff(.8)],['number',Q(n),eff(.8)]])
  px,py=x+xx,y-yy
  if net is None:sc.append(['no_connect',['at',fx(px),fx(py)],['uuid',Q(uid(ref+'nc'+n))]])
  else:
   ex=px+(-7.62 if left else 7.62);wire(sc,px,py,ex,py,ref+'wire'+n);lab(sc,net,ex,py,ref+'label'+n,left)
 field(sc,'lib_symbols').append(sym);libsym=copy.deepcopy(sym);libsym[1]=Q(ref);nlibrary.append(libsym);sc.append(inst)
 if part['fp'].startswith('RykerConnect:'):
  f=p.FootprintLoad(str(R/'RykerConnect.pretty' if (R/'RykerConnect.pretty'/(part['fp'].split(':')[1]+'.kicad_mod')).exists() else S/'RykerConnect.pretty'),part['fp'].split(':')[1])
 else:
  lib,item=part['fp'].split(':');f=p.FootprintLoad(str(Path(r'C:\Program Files\KiCad\10.0\share\kicad\footprints')/(lib+'.pretty')),item)
 assert f is not None,(ref,part['fp'])
 b.Add(f)
 if f.GetLayer()==p.B_Cu:f.Flip(f.GetPosition(),False)
 f.SetReference(ref);f.SetValue(part['value']);f.SetFPID(p.LIB_ID('RykerConnect',localfp));f.SetPath(p.KIID_PATH('/'+ROOT+('' if page==1 else '/'+uid('sheet'+str(page)))+'/'+uid(ref)))
 f.SetField('Datasheet','');f.SetField('Description','')
 f.SetPosition(pt(0,0));f.SetOrientationDegrees(0)
 for pad in f.Pads():
  n=pad.GetNumber()
  if not n:continue
  nm,ty=part['resolved_pins'].get(n,(n,'passive'));net=part['nets'].get(n)
  if net is None:net=f'unconnected-({ref}-{nm}-Pad{n})'
  if b.FindNet(net) is None or b.FindNet(net).GetNetCode()<0:b.Add(p.NETINFO_ITEM(b,net))
  pad.SetNet(b.FindNet(net));pad.SetPinFunction(nm);pad.SetPinType(ty)
 for key,value in [('MPN',part['mpn']),('LCSC',part['code']),('Manufacturer',part['manufacturer'])]:f.SetField(key,value);f.GetField(key).SetVisible(False)
 f.Reference().SetTextSize(pt(.7,.7));f.Reference().SetTextThickness(p.FromMM(.12));f.Value().SetVisible(False)
 p.PCB_IO_KICAD_SEXPR().FootprintSave(str(library),f)
 f.SetPosition(pt(*part['pos'][:2]));f.SetOrientationDegrees(part['pos'][2]);fps[ref]=f
 # Reference labels are moved to clear space during final visual review.
 f.Reference().SetPosition(pt(part['pos'][0],part['pos'][1]-3));f.Reference().SetVisible(True)
text(pages[1],'Waveshare 28836 socketed module. ESP 3.3V header pins intentionally isolated.\nPeripherals use U7. Disconnect USB before closing J8 for external power.',20,210)
text(pages[2],'12V variant only: fused accessory input. RTN is isolated from GND.\nUSB variant: vehicle protection is external to this board.',20,235)
text(pages[3],'J8 open for USB. USB unplugged before closing J8.\nExternal power and USB must not be connected simultaneously.\nU7 supplies peripheral 3.3V; never bridge it to the ESP 3.3V output.',20,235)
text(pages[4],'PCF8563 at I2C 0x51. D5/D6 isolate the non-rechargeable CR2032.\nX1 plus C18 form the 32.768kHz oscillator; verify drift on prototype.\nRemote BME280 provides temperature/humidity/pressure; no local MCP9808.',20,235)
# Power flags describe external supply sources, not additional physical components.
for net,page,x,y in [('GND',2,360,235),('VBAT',4,330,235),('RTC_VDD',4,365,235),('+5V_MODULE',3,330,235)] + ([('+12V',2,330,235),('+5V_BUCK',3,360,235)] if json.loads((R/'variant.json').read_text())['variant']=='12V' else [('+5V_EXTERNAL',2,330,235)]):
 x,y=grid(x),grid(y)
 sc=pages[page];key='flag'+net
 flag=['symbol',Q('power:PWR_FLAG'),['power'],['pin_names',['offset','0']],['in_bom','no'],['on_board','yes'],prop_('Reference','#FLG',0,2,True),prop_('Value','PWR_FLAG',0,3,True),['symbol',Q('PWR_FLAG_1_1'),['pin','power_out','line',['at','0','0','90'],['length','0'],['name',Q('pwr'),eff()],['number',Q('1'),eff()]]]]
 flag=copy.deepcopy(next(z for z in fields(read(Path(r'C:\Program Files\KiCad\10.0\share\kicad\symbols\power.kicad_sym')),'symbol') if z[1]=='PWR_FLAG'));flag[1]=Q('power:PWR_FLAG')
 if not any(z[1]=='power:PWR_FLAG' for z in fields(field(sc,'lib_symbols'),'symbol')):field(sc,'lib_symbols').append(flag)
 sc.append(['symbol',['lib_id',Q('power:PWR_FLAG')],['at',fx(x),fx(y),'0'],['unit','1'],['in_bom','no'],['on_board','yes'],['dnp','no'],['uuid',Q(uid(key))],prop_('Reference','#FLG'+str(page)+str(x),x,y,True),prop_('Value','PWR_FLAG',x,y,True),['instances',['project',Q('RykerConnect'),['path',Q('/'+ROOT+('' if page==1 else '/'+uid('sheet'+str(page)))),['reference',Q('#FLG'+str(page)+str(x))],['unit','1']]]]])
 lab(sc,net,x,y,key+'lab')
for ref in ['H1','H2']:
 f=next(f for f in b.GetFootprints() if f.GetReference()==ref)
 f.SetAttributes(f.GetAttributes() | p.FP_BOARD_ONLY | p.FP_EXCLUDE_FROM_BOM | p.FP_EXCLUDE_FROM_POS_FILES)
 p.PCB_IO_KICAD_SEXPR().FootprintSave(str(library),f)
for page,sc in pages.items():write(R/('RykerConnect.kicad_sch' if page==1 else f'sheet{page}.kicad_sch'),sc)
write(R/'RykerConnect.kicad_sym',nlibrary)
(R/'sym-lib-table').write_text('(sym_lib_table (lib (name "RykerConnect")(type "KiCad")(uri "${KIPRJMOD}/RykerConnect.kicad_sym")(options "")(descr "")))')
(R/'fp-lib-table').write_text('(fp_lib_table (lib (name "RykerConnect")(type "KiCad")(uri "${KIPRJMOD}/RykerConnect.pretty")(options "")(descr "")))')
p.SaveBoard(str(R/'RykerConnect.kicad_pcb'),b)
(R/'design-resolved.json').write_text(json.dumps(parts,indent=2))
(V/'pads.json').write_text(json.dumps({ref:{pad.GetNumber():[p.ToMM(pad.GetPosition().x),p.ToMM(pad.GetPosition().y)] for pad in f.Pads() if pad.GetNumber()} for ref,f in fps.items()},indent=2))
print('Built REV04 schematic and unrouted top-side carrier; routing/validation pending.')

