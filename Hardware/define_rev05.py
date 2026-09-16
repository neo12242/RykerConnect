"""Electrical source for the approved two REV05 variants (KiCad Python)."""
from pathlib import Path
import json,copy,sys
import pcbnew as p
BASE=Path(__file__).resolve().parent
R=BASE/('REV05-'+sys.argv[1]); variant=sys.argv[1]
parts=copy.deepcopy(json.loads((BASE/'REV04-Carrier/design.json').read_text()))
remove={'U2','C4'}
if variant=='USB':remove.update(['U6','D2','D3','D4','C14','C15','C16','C17','R7','R8','R9','R10','U5','L1','C9','C10','C11','C12','C13'])
parts=[a for a in parts if a['ref'] not in remove]
by={a['ref']:a for a in parts}
by['J6']['nets']['1']=None;by['J6']['nets']['2']=None
by['C3']['nets']['1']='RTC_VDD';by['C3']['pos']=[175,92,0]
by['C5']['pos']=[155,86,270];by['C6']['pos']=[163,89,0]
by['J8']['nets']={'1':'+5V_BUCK' if variant=='12V' else '+5V_EXTERNAL','2':'+5V_MODULE'}
if variant=='USB':by['J1']['nets']['1']='+5V_EXTERNAL';by['J1']['value']='5V ONLY regulated input'
by['U3'].update(value='PCF8563T/5,518',mpn='PCF8563T/5,518',manufacturer='NXP',code='C7440',fp='Package_SO:SOIC-8_3.9x4.9mm_P1.27mm',pos=[174,86,0],nets={'1':'RTC_OSCI','2':'RTC_OSCO','3':None,'4':'GND','5':'SDA','6':'SCL','7':None,'8':'RTC_VDD'},pins={'1':['OSCI','input'],'2':['OSCO','output'],'3':['INT','open_collector'],'4':['VSS','power_in'],'5':['SDA','bidirectional'],'6':['SCL','input'],'7':['CLKOUT','open_collector'],'8':['VDD','power_in']})
def add(ref,value,mpn,code,fp,nets,pos,page=4,maker='',pins=None):
 parts.append(dict(ref=ref,value=value,mpn=mpn,code=code,fp=fp,nets=nets,pos=pos,page=page,manufacturer=maker,pins=pins or {},source='https://jlcpcb.com/partdetail/'+code))
add('U7','3.3V peripherals','TLV1117LV33DCYR','C15578','Package_TO_SOT_SMD:SOT-223-3_TabPin2',{'1':'GND','2':'+3.3V','3':'+5V_MODULE'},[161,83,180],3,'Texas Instruments',{'1':['GND','power_in'],'2':['OUT','power_out'],'3':['IN','power_in']})
add('C19','10uF 25V','CL10A106MA8NRNC','C96446','Capacitor_SMD:C_0603_1608Metric',{'1':'+5V_MODULE','2':'GND'},[168,79.5,0],3,'Samsung Electro-Mechanics')
add('X1','32.768kHz 12.5pF','ABS07-32.768KHZ-T','C130253','Crystal:Crystal_SMD_3215-2Pin_3.2x1.5mm',{'1':'RTC_OSCI','2':'RTC_OSCO'},[167,86,90],4,'Abracon')
add('C18','22pF C0G','CL10C220JB8NNNC','C1653','Capacitor_SMD:C_0603_1608Metric',{'1':'RTC_OSCI','2':'GND'},[167,82.5,0],4,'Samsung Electro-Mechanics')
for ref,net,pos in [('D5','+3.3V',[175,79,0]),('D6','VBAT',[179,93,0])]:
 add(ref,'BAV199 backup isolation','BAV199','C3019921','Package_TO_SOT_SMD:SOT-23',{'1':net,'2':None,'3':'RTC_VDD'},pos,4,'Slkor',{'1':['A1','passive'],'2':['K2_UNUSED','passive'],'3':['K1_A2','passive']})
# Explicit footprint from Sunlord recommended land pattern; polarityless inductor.
if variant=='12V':
 by['C11']['pos'][0]=151.3
 by['C12']['pos'][0]=164
 by['C13']['pos'][0]=168
 by['L1'].update(mpn='MWSA0603S-4R7MT',code='C408447',manufacturer='Sunlord',fp='RykerConnect:L1_MWSA0603S',pos=[157,107,0])
 lib=R/'RykerConnect.pretty';lib.mkdir(exist_ok=True)
 f=p.FOOTPRINT(None);f.SetReference('L1');f.SetValue('4.7uH');f.SetFPID(p.LIB_ID('RykerConnect','L1_MWSA0603S'));f.SetAttributes(p.FP_SMD)
 for n,x in [('1',-3.025),('2',3.025)]:
  layers=p.LSET()
  for layer in [p.F_Cu,p.F_Paste,p.F_Mask]:layers.AddLayer(layer)
  pad=p.PAD(f);pad.SetNumber(n);pad.SetAttribute(p.PAD_ATTRIB_SMD);pad.SetShape(p.PAD_SHAPE_RECT);pad.SetSize(p.VECTOR2I(p.FromMM(2.35),p.FromMM(3.5)));pad.SetPosition(p.VECTOR2I(p.FromMM(x),0));pad.SetLayerSet(layers);f.Add(pad)
 for layer,w,h in [(p.F_Fab,7,6.6),(p.F_CrtYd,8.9,7.2)]:
  for a,z in [((-w/2,-h/2),(w/2,-h/2)),((w/2,-h/2),(w/2,h/2)),((w/2,h/2),(-w/2,h/2)),((-w/2,h/2),(-w/2,-h/2))]:
   sh=p.PCB_SHAPE(f);sh.SetShape(p.SHAPE_T_SEGMENT);sh.SetStart(p.VECTOR2I(p.FromMM(a[0]),p.FromMM(a[1])));sh.SetEnd(p.VECTOR2I(p.FromMM(z[0]),p.FromMM(z[1])));sh.SetLayer(layer);sh.SetWidth(p.FromMM(.05));f.Add(sh)
 p.PCB_IO_KICAD_SEXPR().FootprintSave(str(lib),f)
for a in parts:a['source']='https://jlcpcb.com/partdetail/'+a['code']
(R/'design.json').write_text(json.dumps(parts,indent=2))
print(variant,len(parts),'components')
