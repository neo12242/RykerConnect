"""Authoritative new component identities and net assignments for REV03."""
import uuid
ROOT_ID='0f8d191d-1efb-46e1-a4c8-e725950f66f5'
SHEET_ID='556f6e13-eacd-4d85-92c1-c1de2c1c3013'
PAGE_ID='9c231c3d-d9d2-4a08-83ae-d65d8e766423'
def ident(ref):return str(uuid.uuid5(uuid.UUID(SHEET_ID),ref))
PINS={
'1':('IN','power_in','+12V'),'2':('IN','passive','+12V'),
'3':('UVLO','input','PROT_UV'),'4':('NC','no_connect',None),
'5':('OVP','input','PROT_OV'),'6':('MODE','input',None),'7':('SHDN','input',None),
'8':('RTN','power_out','PROT_RTN'),'9':('GND','power_in','GND'),
'10':('IMON','output',None),'11':('ILIM','passive','PROT_ILIM'),
'12':('dVdT','passive','PROT_DVDT'),'13':('NC','no_connect',None),
'14':('FLT','open_collector',None),'15':('OUT','power_out','+12V_PROTECTED'),
'16':('OUT','passive','+12V_PROTECTED'),'17':('EP_RTN','passive','PROT_RTN')}
# All new components on bottom; coordinate system remains the original KiCad board.
PARTS=[
dict(refs='U6',mpn='TPS26600PWPR',manufacturer='Texas Instruments',code='C544399',value='TPS26600PWPR',fp='Protection_HTSSOP16_PWP0016A',pos=[141,81,0],nets={n:v[2] for n,v in PINS.items()},symbol='TPS26600PWPR',note='Adjustable OV cutoff variant; do not substitute TPS26602 fixed 38V clamp. RTN/EP isolated from system ground.'),
dict(refs='D2',mpn='SMBJ33A',manufacturer='Littelfuse',code='C224019',value='SMBJ33A',fp='Diode_SMD:D_SMB',pos=[151,88.5,0],nets={'1':'+12V','2':'TVS_MID'},symbol='D_Zener',note='Positive TVS: 33V standoff, 53.3V clamp at specified 11.3A pulse; anode joins D4 anode. Not an unsuppressed-load-dump energy rating.'),
dict(refs='D3',mpn='B140-13-F',manufacturer='Diodes Incorporated',code='C15759',value='B140-13-F',fp='Diode_SMD:D_SMA',pos=[132,81,90],nets={'1':'+12V_PROTECTED','2':'GND'},symbol='D_Schottky',note='1A 40V negative-output transient clamp; cathode to protected rail.'),
dict(refs='C14',mpn='CL31B105KCHNNNE',manufacturer='Samsung Electro-Mechanics',code='C13832',value='1uF 100V',fp='Capacitor_SMD:C_1206_3216Metric',pos=[149,76.5,0],nets={'1':'+12V','2':'GND'},symbol='C',note='Input X7R 100V ceramic; effective capacitance under DC bias must be validated for fast negative transients.'),
dict(refs='C15',mpn='CL21B105KBFNNNE',manufacturer='Samsung Electro-Mechanics',code='C28323',value='1uF 50V',fp='Capacitor_SMD:C_0805_2012Metric',pos=[135,77,90],nets={'1':'+12V_PROTECTED','2':'GND'},symbol='C',note='Local output decoupling, X7R 10%.'),
dict(refs='C16',mpn='CL10B103KB8NNNC',manufacturer='Samsung Electro-Mechanics',code='C1589',value='10nF 50V',fp='Capacitor_SMD:C_0603_1608Metric',pos=[135.5,82.5,0],nets={'1':'PROT_DVDT','2':'PROT_RTN'},symbol='C',note='Output ramp control capacitor to isolated RTN, about 1ms ramp at 12V.'),
dict(refs='R7',mpn='RC0603FR-07120KL',manufacturer='Yageo',code='C114607',value='120k 1%',fp='Resistor_SMD:R_0603_1608Metric',pos=[147,80.5,0],nets={'1':'+12V','2':'PROT_UV'},symbol='R',note='Upper leg of 120k/20k/10k divider; 75V, 100mW.'),
dict(refs='R8',mpn='RC0603FR-0720KL',manufacturer='Yageo',code='C105575',value='20k 1%',fp='Resistor_SMD:R_0603_1608Metric',pos=[146.5,83,0],nets={'1':'PROT_UV','2':'PROT_OV'},symbol='R',note='Middle divider leg.'),
dict(refs='R9',mpn='CRGP0805F10K',manufacturer='TE Connectivity',code='C2074940',value='10k',fp='Resistor_SMD:R_0805_2012Metric',pos=[144.5,86,0],nets={'1':'PROT_OV','2':'PROT_RTN'},symbol='R',note='Bottom divider leg, matching original R5/R6 purchasing identity.'),
dict(refs='R10',mpn='RC0603FR-0712KL',manufacturer='Yageo',code='C114659',value='12k 1%',fp='Resistor_SMD:R_0603_1608Metric',pos=[135.5,85,0],nets={'1':'PROT_ILIM','2':'PROT_RTN'},symbol='R',note='Nominal 1A current limit; returns only to PROT_RTN.'),
]
PARTS.append(dict(refs='D4',mpn='SMBJ18A',manufacturer='Littelfuse',code='C151256',value='SMBJ18A',fp='Diode_SMD:D_SMB',pos=[151,94,0],nets={'1':'GND','2':'TVS_MID'},symbol='D_Zener',note='Negative TVS: cathode GND, anode joins D2 anode. 18V standoff, 29.2V clamp at 20.6A. Asymmetric pair protects input-output differential during negative pulses.'))
PARTS.append(dict(refs='C17',mpn='CL31B105KCHNNNE',manufacturer='Samsung Electro-Mechanics',code='C13832',value='1uF 100V',fp='Capacitor_SMD:C_1206_3216Metric',pos=[153.5,76.5,0],nets={'1':'+12V','2':'GND'},symbol='C',note='Second parallel input capacitor; 2uF nominal total before DC-bias derating.'))
for part in PARTS:
 part['source']='https://www.lcsc.com/product-detail/'+part['code']+'.html'
 part['status']='Selected for REV03 prototype; JLCPCB stock and model acceptance pending'
