"""Recreate schematic changes from the untouched source; pin nets remain auditable."""
from pathlib import Path
import json,copy,uuid
from sexpr import *
ROOT=Path(__file__).resolve().parent
SOURCE=(Path(__file__).resolve().parents[1] / 'KiCad/RykerConnect-REV02')
mapping=json.loads((ROOT/'footprint-map.json').read_text())
parts=json.loads((ROOT.parent/'JLCPCB-REV02-review/parts.json').read_text())
for part in parts:
    if part.get('candidate'):
        part['original_mpn']=part['mpn'];part['mpn']=part['candidate'];part['code']=part['candidate_code'];part['source']=part['candidate_source']
        part['status']='Selected under hardware implementation approval; sourcing/model validation pending'
notes={
 'C3,C4,C7':'1uF 16V X7R 10% 0603. Selected C packaging suffix instead of original D; same Samsung electrical family.',
 'J1':'Tin-plated right-angle Micro-Fit 3.0, 2 pins. Drawing confirms 3mm retention holes. Connector moved 0.5mm toward board edge, giving 9.88mm peg-to-edge distance against 10.16mm maximum. Through-hole assembly required; centroid/model preview pending.',
 'J2':'Selected GCT USB4105-GF-A within original footprint family. Copper clearance and all four shell-tab connections corrected. Exact assembly model/mechanical preview remains required; catalog identifies assembly difficulty High.',
 'R1,R2,R3,R4':'Selected KOA RS73 5.1k 0.5% 250mW 0805, 150V maximum working voltage and -55 to +155C operating range. Replaces original RK73 5.1k 1% 250mW. Actual circuit dissipation remains low; stock/sourcing confirmation required.',
 'U4':'Selected N8 variant: 8MB flash, no PSRAM; matching REV02 firmware built successfully. Optional EPAD 41 is unsoldered as permitted by manufacturer; ground pins 1 and 40 remain connected. Thermal and RF operation need physical validation.'}
for part in parts:
    if part['refs'] in notes:part['note']=notes[part['refs']]
parts.append(dict(refs='J5',mpn='SM04B-SRSS-TB(LF)(SN)',manufacturer='JST',code='C160404',status='External 3.3V I2C connector',source='https://lcsc.com/product-detail/Wire-To-Board-Wire-To-Wire-Connector_JST-SM04B-SRSS-TB-LF-SN_C160404.html',note='STEMMA QT/Qwiic order: 1 GND, 2 +3.3V, 3 SDA, 4 SCL. Sensor is an external purchased module.'))
byref={r:part for part in parts for r in part['refs'].split(',')}
local_symbols={}
def uid():return Q(str(uuid.uuid4()))
def setprop(n,key,value):
    v=prop(n,key)
    if v:v[2]=Q(value)
    else:n.append(['property',Q(key),Q(value),['at',*field(n,'at')[1:3],'0'],['effects',['font',['size','1.27','1.27']],['hide','yes']]])
for name in ['RykerConnect.kicad_sch','power.kicad_sch']:
    s=read(SOURCE/name)
    # Drop the old four probe symbols and their local wiring/labels/power marks.
    # The whole isolated test-point block is replaced by J5 below.
    def old_probe_block(n):
        at=field(n,'at')
        if at and n[0] in ('symbol','global_label','label','junction'):
            x,y=map(float,at[1:3]);return 10<x<45 and 160<y<185
        if n[0]=='wire':
            pts=fields(field(n,'pts'),'xy');return all(10<float(v[1])<45 and 160<float(v[2])<185 for v in pts)
        return False
    if name=='RykerConnect.kicad_sch':
        s[:]=[n for n in s if not isinstance(n,list) or not old_probe_block(n)]
        lib=read(Path(r'C:\Program Files\KiCad\10.0\share\kicad\symbols\Connector_Generic.kicad_sym'))
        sym=copy.deepcopy(next(v for v in fields(lib,'symbol') if v[1]=='Conn_01x04'))
        # Both metal anchors are grounded on this connector. Represent MP as a
        # hidden passive pin stacked on signal pin 1 (GND), matching the PCB.
        pinsub=next(v for v in fields(sym,'symbol') if fields(v,'pin'))
        mp=copy.deepcopy(fields(pinsub,'pin')[0]);field(mp,'number')[1]=Q('MP')
        field(mp,'name')[1]=Q('Mounting anchors');mp.append(['hide','yes']);pinsub.append(mp)
        sym[1]=Q('Connector_Generic:Conn_01x04');field(s,'lib_symbols').append(sym)
        # Standard connector symbol pins at x=-5.08, y=0,-2.54,-5.08,-7.62.
        j=loads('''(symbol (lib_id "Connector_Generic:Conn_01x04") (at 33.02 167.64 0) (unit 1)
          (in_bom yes) (on_board yes) (dnp no)
          (uuid "b7a356b0-6575-4fca-9a0d-5b80de5f6094")
          (property "Reference" "J5" (at 35.56 165.1 0) (effects (font (size 1.27 1.27))))
          (property "Value" "External BME280" (at 43.18 167.64 0) (effects (font (size 1.27 1.27))))
          (property "Footprint" "" (at 33.02 167.64 0) (effects (font (size 1.27 1.27)) (hide yes)))
          (instances (project "RykerConnect" (path "/0f8d191d-1efb-46e1-a4c8-e725950f66f5" (reference "J5") (unit 1)))))''')
        s.append(j)
        for i,net in enumerate(['GND','+3.3V','SDA','SCL']):
            y=str(round(165.1+2.54*i,2))
            s.append(['wire',['pts',['xy','27.94',y],['xy','22.86',y]],['stroke',['width','0'],['type','default']],['uuid',uid()]])
            s.append(['global_label',Q(net),['shape','bidirectional'],['at','22.86',y,'180'],['effects',['font',['size','1.27','1.27']],['justify','right']],['uuid',uid()]])
        s.append(['text',Q('J5: 3.3V ONLY. External BME280 0x76/0x77.\nOptional external bus extenders; no raw 12V.'),['at','17.78','184.15','0'],['effects',['font',['size','1.0','1.0']],['justify','left']],['uuid',uid()]])
    for n in fields(s,'symbol'):
        r=prop(n,'Reference')[2]
        if r in mapping:setprop(n,'Footprint',mapping[r])
        if r.startswith(('TP','H')):
            field(n,'in_bom')[1]='no'
            if field(n,'in_pos_files'):field(n,'in_pos_files')[1]='no'
        if r in byref:
            part=byref[r]
            setprop(n,'MPN',part['mpn']);setprop(n,'Manufacturer',part['manufacturer']);setprop(n,'LCSC',part['code'])
            setprop(n,'PROD_ID',part['mpn'])
            if r in ['J1','J2','J5','U4']:setprop(n,'Value',part['mpn'])
            if r=='D1':
                setprop(n,'Value','CVJ10F30,LF');setprop(n,'Datasheet','https://toshiba.semicon-storage.com/info/docget.jsp?did=13914&prodName=CVJ10F30');setprop(n,'Description','Dual independent Schottky: 1 A1, 2 NC, 3 A2, 4 K2, 5 K1; UFV')
    # Correct the imported regulator symbol's electrical pin types.
    for sy in fields(field(s,'lib_symbols'),'symbol'):
        if 'ESP32-S3-WROOM-1' in sy[1]:
            # Espressif permits the exposed thermal pad to remain unsoldered.
            # This assembly variant retains GND pins 1/40 and omits only EPAD41.
            for sub in fields(sy,'symbol'):
                sub[:]=[v for v in sub if not (isinstance(v,list) and v and v[0]=='pin' and field(v,'number')[1]=='41')]
        if 'LMR50410' in sy[1]:
            for sub in fields(sy,'symbol'):
                for pin in fields(sub,'pin'):
                    pin[1]={'1':'passive','2':'power_in','3':'input','4':'input','5':'power_in','6':'power_out'}[field(pin,'number')[1]]
    # Power flags describe known supply sources, including passive diode/inductor
    # paths that ERC cannot infer. They do not change physical connectivity.
    flaglib=read(Path(r'C:\Program Files\KiCad\10.0\share\kicad\symbols\power.kicad_sym'))
    flag=copy.deepcopy(next(v for v in fields(flaglib,'symbol') if v[1]=='PWR_FLAG'))
    flag[1]=Q('power:PWR_FLAG');field(s,'lib_symbols').append(flag)
    flagpoints=[('501',22.86,20.32),('502',27.94,29.21),('503',48.26,20.32)] if name=='RykerConnect.kicad_sch' else [('504',110.49,46.99)]
    for ref,x,y in flagpoints:
        rootpath='/'+str(field(s,'uuid')[1])
        if name=='power.kicad_sch':
            example=next(v for v in fields(s,'symbol') if prop(v,'Reference')[2]=='U5')
            rootpath=field(field(field(example,'instances'),'project'),'path')[1]
        s.append(loads(f'''(symbol (lib_id "power:PWR_FLAG") (at {x} {y} 0) (unit 1) (in_bom no) (on_board yes) (dnp no)
          (uuid "{uid()}") (property "Reference" "#FLG{ref}" (at {x} {y} 0) (effects (font (size 1.27 1.27)) (hide yes)))
          (property "Value" "PWR_FLAG" (at {x} {y-2.54} 0) (effects (font (size 1.27 1.27))))
          (instances (project "RykerConnect" (path "{rootpath}" (reference "#FLG{ref}") (unit 1)))))'''))
    # Bundle embedded symbols to make this project independent of missing author libraries.
    renames={}
    for sy in fields(field(s,'lib_symbols'),'symbol'):
        old=str(sy[1]);short=old.replace(':','_').replace('/','_')
        if old=='Diode:BAT160C':short='CVJ10F30'
        if old=='RF_Module:ESP32-S3-WROOM-1':short='ESP32_S3_WROOM_1_Unbonded_EPAD'
        if old=='Connector_Generic:Conn_01x04':short='JST_SH_04_Grounded_Anchors'
        oldbase=old.split(':')[-1]
        for child in fields(sy,'symbol'):
            if child[1].startswith(oldbase+'_'):child[1]=Q(short+child[1][len(oldbase):])
        sy[1]=Q('RykerConnect:'+short);renames[old]=str(sy[1])
        local=copy.deepcopy(sy);local[1]=Q(short);local_symbols[short]=local
    for n in fields(s,'symbol'):
        key=field(n,'lib_id');key[1]=Q(renames[str(key[1])])
    write(ROOT/name,s)
write(ROOT/'RykerConnect.kicad_sym',['kicad_symbol_lib',['version','20241209'],['generator',Q('kicad_symbol_editor')],*local_symbols.values()])
(ROOT/'sym-lib-table').write_text('(sym_lib_table (version 7) (lib (name "RykerConnect")(type "KiCad")(uri "${KIPRJMOD}/RykerConnect.kicad_sym")(options "")(descr "Embedded source symbols with corrected regulator pin types")))\n')
(ROOT/'selected-parts.json').write_text(json.dumps(parts,indent=2))
print('Wrote both schematics and selected purchasing identities')
