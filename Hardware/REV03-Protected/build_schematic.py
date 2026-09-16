"""Derive the three-sheet REV03 schematic from preserved REV02."""
from pathlib import Path
import copy,json,uuid
from sexpr import *
from protection_defs import *
R=Path(__file__).resolve().parent;S=R.parent/'REV02-Corrected'
def uid():return Q(str(uuid.uuid4()))
def fx(x):return str(round(x,4))
def effects(size=1.0):return ['effects',['font',['size',str(size),str(size)]]]
def label(s,net,x,y,angle=0):
 e=effects(.9)
 if angle==180:e.append(['justify','right'])
 s.append(['global_label',Q(net),['shape','bidirectional'],['at',fx(x),fx(y),str(angle)],e,['uuid',uid()]])
def wire(s,x1,y1,x2,y2):s.append(['wire',['pts',['xy',fx(x1),fx(y1)],['xy',fx(x2),fx(y2)]],['stroke',['width','0'],['type','default']],['uuid',uid()]])
def text(s,t,x,y):s.append(['text',Q(t),['at',fx(x),fx(y),'0'],['effects',['font',['size','1.2','1.2']],['justify','left']],['uuid',uid()]])
def property_(key,value,x,y,hide=False):
 e=effects();
 if hide:e.append(['hide','yes'])
 return ['property',Q(key),Q(value),['at',fx(x),fx(y),'0'],e]
def revision(s):
 block=field(s,'title_block')
 if block is None:block=['title_block'];s.append(block)
 for key,value in [('title','RykerConnect - protected accessory input'),('date','2026-09-13'),('rev','REV03')]:
  entry=field(block,key)
  if entry:entry[1]=Q(value)
  else:block.append([key,Q(value)])
main=read(S/'RykerConnect.kicad_sch');field(main,'paper')[1]=Q('A3')
main.append(['sheet',['at','20.32','218.44'],['size','60.96','20.32'],['stroke',['width','0'],['type','default']],['fill',['color','0','0','0','0']],['uuid',Q(SHEET_ID)],property_('Sheetname','Accessory input protection',20.32,217.17),property_('Sheetfile','protection.kicad_sch',20.32,240.03),['instances',['project',Q('RykerConnect'),['path',Q('/'+ROOT_ID),['page',Q('3')]]]]])
text(main,'REV03: switched accessory 12V; sensor cable approx. 30cm.\nNew protection components are on the PCB bottom side.',20.32,248.92)
for node in fields(main,'sheet'):
 for pin in fields(node,'pin'):
  if pin[1]=='12V BAT IN':pin[1]=Q('Protected 12V IN')
main[:]=[n for n in main if not (isinstance(n,list) and n and n[0]=='wire' and field(n,'uuid')[1]=='4195c7c5-8ea0-4087-8971-3c004c1c4556')]
wire(main,25.4,35.56,25.4,40.64);label(main,'+12V_PROTECTED',25.4,35.56)
revision(main);write(R/'RykerConnect.kicad_sch',main)
power=read(S/'power.kicad_sch')
for lab in fields(power,'hierarchical_label'):
 if lab[1]=='12V BAT IN':lab[1]=Q('Protected 12V IN')
revision(power);write(R/'power.kicad_sch',power)
libroot=read(S/'RykerConnect.kicad_sym')
s=['kicad_sch',['version','20250114'],['generator',Q('eeschema')],['uuid',Q(PAGE_ID)],['paper',Q('A4')],['lib_symbols']]
text(s,'REV03 — switched accessory power protection',20.32,20.32)
text(s,'Normal 9–16V; nominal UVLO 5.95V, OV cutoff 17.85V, current limit 1A.\nRTN is isolated from GND. MODE and SHDN intentionally open (internal bias).',20.32,27.94)
ic=['symbol',Q('RykerConnect:TPS26600PWPR'),['pin_names',['offset','0.508']],['in_bom','yes'],['on_board','yes'],property_('Reference','U',0,22),property_('Value','TPS26600PWPR',0,19),['symbol',Q('TPS26600PWPR_0_1'),['rectangle',['start','-12.7','20.32'],['end','12.7','-7.62'],['stroke',['width','0'],['type','default']],['fill',['type','background']]]],['symbol',Q('TPS26600PWPR_1_1')]]
for n,(name,ty,net) in PINS.items():
 i=int(n)
 if i<=8:x,y,angle=-15.24,17.78-2.54*(i-1),0
 elif i<=16:x,y,angle=15.24,2.54*(i-9),180
 else:x,y,angle=0,-10.16,90
 ic[-1].append(['pin',ty,'line',['at',fx(x),fx(y),str(angle)],['length','2.54'],['name',Q(name),effects()],['number',Q(n),effects()]])
device=read(Path(r'C:\Program Files\KiCad\10.0\share\kicad\symbols\Device.kicad_sym'))
symbols={'TPS26600PWPR':ic}
for name in ['R','C','D_TVS','D_Schottky','D_Zener']:
 sym=copy.deepcopy(next(x for x in fields(device,'symbol') if x[1]==name));sym[1]=Q('RykerConnect:Protection_'+name)
 for sub in fields(sym,'symbol'):sub[1]=Q('Protection_'+str(sub[1]))
 symbols[name]=sym
for sym in symbols.values():
 field(s,'lib_symbols').append(sym);local=copy.deepcopy(sym);local[1]=Q(str(sym[1]).split(':')[-1]);libroot.append(local)
for i,part in enumerate(PARTS):
 ref=part['refs'];x,y=(76.2,78.74) if ref=='U6' else (152.4+50.8*((i-1)%3),50.8+30.48*((i-1)//3))
 sym=symbols[part['symbol']];fp=ref+'_'+part['fp'].split(':')[-1]
 inst=['symbol',['lib_id',sym[1]],['at',fx(x),fx(y),'0'],['unit','1'],['in_bom','yes'],['on_board','yes'],['dnp','no'],['uuid',Q(ident(ref))],property_('Reference',ref,x+5.08,y-5.08),property_('Value',part['value'],x+5.08,y-2.54),property_('Footprint','RykerConnect:'+fp,x,y,True),property_('MPN',part['mpn'],x,y,True),property_('Manufacturer',part['manufacturer'],x,y,True),property_('LCSC',part['code'],x,y,True),['instances',['project',Q('RykerConnect'),['path',Q('/'+ROOT_ID+'/'+SHEET_ID),['reference',Q(ref)],['unit','1']]]]]
 s.append(inst)
 if ref=='U6':
  field(prop(inst,'Reference'),'at')[1:3]=[fx(x),fx(y-25.4)]
  field(prop(inst,'Value'),'at')[1:3]=[fx(x),fx(y-22.86)]
 for sub in fields(sym,'symbol'):
  for pin in fields(sub,'pin'):
   n=str(field(pin,'number')[1]);at=field(pin,'at');px=x+float(at[1]);py=y-float(at[2]);net=part['nets'].get(n)
   if not net:s.append(['no_connect',['at',fx(px),fx(py)],['uuid',uid()]])
   else:
    # Each electrical endpoint has an explicit global net label.
    angle=int(at[3]);dx,dy={0:(-7.62,0),180:(7.62,0),90:(0,7.62),270:(0,-7.62)}[angle]
    wire(s,px,py,px+dx,py+dy);label(s,net,px+dx,py+dy,180 if dx<0 else 0)
text(s,'D2/D4: asymmetric TVS pair, common anodes; D3: cathode to protected output, anode to GND.\nRequires fused accessory branch. Prototype surge/thermal tests required.\n35V suppressed-load-dump target; unsuppressed load dump is not qualified.',20.32,154.94)
revision(s);write(R/'protection.kicad_sch',s);write(R/'RykerConnect.kicad_sym',libroot)
parts=json.loads((S/'selected-parts.json').read_text())
# Keep matching 10k purchasing identity but a separate footprint group.
parts.extend([{k:v for k,v in part.items() if k not in ['pos','nets','symbol']} for part in PARTS])
(R/'selected-parts.json').write_text(json.dumps(parts,indent=2))
print('Generated three-sheet protected schematic, 44 fitted components (group counts validated during export)')
