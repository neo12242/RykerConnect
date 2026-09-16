"""REV04 electrical source of truth. No changes to earlier revisions."""
from pathlib import Path
import json,runpy,xml.etree.ElementTree as ET,uuid
R=Path(__file__).resolve().parent; S=R.parent/'REV03-Protected'
ROOT='b3e6de9e-c7cd-4419-8e94-64236783f104'
def ident(ref):return str(uuid.uuid5(uuid.UUID(ROOT),ref))
old=json.loads((R/'original-nets.json').read_text()); metadata=json.loads((S/'selected-parts.json').read_text())
nl=ET.parse(S/'validation/netlist.xml')
fp={c.get('ref'):c.findtext('footprint') for c in nl.findall('.//components/comp')}
parts=[]
def add(ref,value,mpn,code,footprint,nets,pos,page=1,manufacturer='',pins=None):
 p=dict(ref=ref,value=value,mpn=mpn,code=code,fp=footprint,nets=nets,pos=pos,page=page,manufacturer=manufacturer,pins=pins or {})
 parts.append(p);return p
for ref,pos in [('BT1',[105,87,0]),('J1',[120,94,0]),('J3',[150,72.5,180]),('J4',[163,72.5,180]),('J5',[176,72.5,180]),('U2',[163,94,0])]:
 m=next(p for p in metadata if ref in p['refs'].split(',')); nets={k:(None if v.startswith('unconnected-') else v) for k,v in old[ref].items()}
 add(ref,m.get('value',m['mpn']),m['mpn'],m['code'],fp[ref],nets,pos,1,m['manufacturer'])
for part in runpy.run_path(str(S/'protection_defs.py'))['PARTS']:
 ref=part['refs']; positions={'U6':[137,85,0],'D2':[122,78,0],'D4':[122,84,180],'D3':[148,81,90],'C14':[127,77,90],'C17':[130,77,90],'C15':[149,86,90],'C16':[143,86,0],'R7':[130,81,90],'R8':[130,84,90],'R9':[131,87,90],'R10':[143,89,0]}
 f='RykerConnect:U6_Protection_HTSSOP16_PWP0016A' if ref=='U6' else part['fp']
 pins=runpy.run_path(str(S/'protection_defs.py'))['PINS'] if ref=='U6' else None
 add(ref,part['value'],part['mpn'],part['code'],f,part['nets'],positions[ref],2,part['manufacturer'],pins)
def passive(ref,value,mpn,code,kind,size,n1,n2,pos,page=1,manufacturer='Samsung Electro-Mechanics'):
 return add(ref,value,mpn,code,f'{kind}_SMD:{kind[0]}_{size}',{'1':n1,'2':n2},pos,page,manufacturer)
for ref,pos in [('C3',[171,84,90]),('C4',[160,94,90]),('C6',[156,81,0]),('C10',[156,84,0]),('C11',[161,87,0])]:
 nets=('+12V_PROTECTED','GND') if ref=='C10' else ('BUCK_BST','BUCK_SW') if ref=='C11' else ('+3.3V','GND')
 passive(ref,'100nF 50V','CL10B104KB8NNNC','C1591','Capacitor','0603_1608Metric',*nets,pos,3 if ref in ['C10','C11'] else 1)
passive('C5','10uF 25V','CL10A106MA8NRNC','C96446','Capacitor','0603_1608Metric','+3.3V','GND',[156,78,0])
# Larger input ceramic for effective capacitance at accessory voltage.
passive('C9','10uF 50V','CL31A106KBHNNNE','C13585','Capacitor','1206_3216Metric','+12V_PROTECTED','GND',[155,86,90],3)
for ref,pos in [('C12',[169,89,90]),('C13',[172,89,90])]:
 passive(ref,'22uF 25V','CL21A226MAQNNNE','C45783','Capacitor','0805_2012Metric','+5V_BUCK','GND',pos,3)
for ref,net,pos in [('R3','SCL',[160,91,0]),('R4','SDA',[156,91,0])]:
 passive(ref,'4.7k 1%','0603WAF4701T5E','C23162','Resistor','0603_1608Metric','+3.3V',net,pos,1,'UNI-ROYAL')
passive('R6','10k 1%','0603WAF1002T5E','C25804','Resistor','0603_1608Metric','GPIO7','GND',[167,94,90],1,'UNI-ROYAL')
for p in parts:
 if p['ref']=='R9':p.update(mpn='0603WAF1002T5E',code='C25804',manufacturer='UNI-ROYAL',fp='Resistor_SMD:R_0603_1608Metric')
add('U3','DS3231SN#T&R','DS3231SN#T&R','C9866','Package_SO:SOIC-16W_7.5x10.3mm_P1.27mm',{'1':None,'2':'+3.3V','3':None,'4':None,**{str(i):'GND' for i in range(5,14)},'14':'Net-(BT1-+)','15':'SDA','16':'SCL'},[179,85.5,0],1,'Analog Devices')
add('U5','AP63205WU-7','AP63205WU-7','C2071056','Package_TO_SOT_SMD:TSOT-23-6',{'1':'+5V_BUCK','2':'+12V_PROTECTED','3':'+12V_PROTECTED','4':'GND','5':'BUCK_SW','6':'BUCK_BST'},[159,85.5,0],3,'Diodes Incorporated',{'1':('FB','input',None),'2':('EN','input',None),'3':('VIN','power_in',None),'4':('GND','power_in',None),'5':('SW','output',None),'6':('BST','passive',None)})
m=next(p for p in metadata if p['refs']=='L1')
add('L1','4.7uH',m['mpn'],m['code'],fp['L1'],{'1':'BUCK_SW','2':'+5V_BUCK'},[165,85,0],3,m['manufacturer'])
# P1/P2 numbering follows the module schematic, pin 1 at its antenna end.
p1={str(i):None for i in range(1,23)}
p1.update({'1':'+3.3V','2':'+3.3V','7':'GPIO7','8':'DSP_RES','11':'SCL','12':'SDA','16':'CS2','17':'SDA/SDIN/MOSI','18':'SCL/SCLK/SCK','19':'DC/MISO','20':'CS1','21':'+5V_MODULE','22':'GND'})
p2={str(i):None for i in range(1,23)};p2.update({'1':'GND','21':'GND','22':'GND'})
for ref,nets,y in [('J6',p1,75.27),('J7',p2,98.13)]:
 add(ref,'ESP32 socket '+('P1' if ref=='J6' else 'P2'),'FH2.54-09-22PZD','C7500786','Connector_PinSocket_2.54mm:PinSocket_1x22_P2.54mm_Vertical',nets,[184.39,y,-90],1,'XUNPU')
# Link is removed before USB programming. Shunt is a separate user-installed item.
add('J8','REMOVE LINK FOR USB','5AML4-11560-102-W0','C29780898', 'Connector_PinHeader_2.54mm:PinHeader_1x02_P2.54mm_Vertical',{'1':'+5V_BUCK','2':'+5V_MODULE'},[169,101.5,90],3,'SAMZO')
for p in parts:
 if p['ref'] in ['BT1','U2','U3','C3','C4','C5','C6','R6','J4']:p['page']=4
positions={'D2':[106,100,0],'D4':[106,106,180],'BT1':[105,85,0],'J1':[120,100,0],'J3':[104,113,0],'J4':[130,113,0],'J5':[138,113,0],'C14':[125,79,90],'C17':[125,84,90],'D3':[149,81,90],'C15':[149,87,90],'U5':[148,107,0],'C9':[143,107,90],'C10':[145,103,0],'C11':[152,106,90],'L1':[157,107,0],'C12':[163,107,90],'C13':[166,107,90],'J8':[173,109,90],'U2':[163,92,0],'C4':[163,95.2,0],'R6':[168,93,90]}
renames={'SCL/SCLK/SCK':'SPI_SCK','SDA/SDIN/MOSI':'SPI_MOSI','DC/MISO':'DSP_DC','Net-(BT1-+)':'VBAT'}
for p in parts:
 if p['ref'] in positions:p['pos']=positions[p['ref']]
 if p['ref']=='R3':p['pos']=[158,93,0]
 if p['ref']=='C11':p['pos'][2]=270
 if p['ref'] in ['U3','C3']:p['pos'][0]-=5
 p['nets']={k:renames.get(v,v) for k,v in p['nets'].items()}
 if p['ref'] in ['J3','J4','J5']:p['nets']['MP']='GND'
for p in parts:
 p['source']='https://jlcpcb.com/partdetail/'+p['code'] if p['code'] else ''
(R/'design.json').write_text(json.dumps(parts,indent=2))
print(f'{len(parts)} carrier components defined; all purchasing identities populated')

