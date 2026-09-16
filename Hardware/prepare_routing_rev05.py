from pathlib import Path
import sys
R=Path(__file__).resolve().parent
source=(R/'REV04-Carrier/route_board.py').read_text()
for v in ['12V','USB']:
 d=R/('REV05-'+v);s=source
 a=s.index("route('VBAT'");z=s.index("route('+12V',[(134.1",a)
 s=s[:a]+s[z:]
 a=s.index('# Keep all signal tracks');z=s.index('p.ZONE_FILLER',a)
 s=s[:a]+s[z:]
 if v=='USB':
  a=s.index("route('BUCK_SW'");z=s.index('# Planes stop',a)
  s=s[:a]+s[z:]
  s=s.replace("pad('C10',2).SetLocalZoneConnection(p.ZONE_CONNECTION_FULL)","")
 # Add short, fixed crystal and LDO connections before the ground pours.
 i=s.index('# Planes stop')
 s=s[:i]+'''route('RTC_OSCI',[xy('U3',1),(169,84.095),xy('X1',1)],.25)
route('RTC_OSCI',[xy('X1',1),(166.225,83.975),xy('C18',1)],.25)
route('RTC_OSCO',[xy('U3',2),(170,85.365),(168.115,87.25),xy('X1',2)],.25)
route('+3.3V',[xy('U7',2),(155,83),xy('C5',1)],.6)
route('+5V_MODULE',[xy('U7',3),(164.15,79.625),xy('C19',1)],.6)
''' + s[i:]
 # Wider routes for both the ESP and peripheral regulator supply.
 s=s.replace("power={'+12V','+12V_PROTECTED','+3.3V','+5V_MODULE'}", "power={'+12V','+12V_PROTECTED','+3.3V','+5V_MODULE','+5V_EXTERNAL'}")
 s=s.replace('carrier_power +12V +12V_PROTECTED +3.3V +5V_MODULE','carrier_power '+ ('+12V +12V_PROTECTED ' if v=='12V' else '+5V_EXTERNAL ')+'+3.3V +5V_MODULE')
 (d/'route_board.py').write_text(s)
print('Routing preparation scripts created')
