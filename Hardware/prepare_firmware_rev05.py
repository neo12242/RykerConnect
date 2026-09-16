"""Snapshot current application into a separate hardware-specific firmware target."""
from pathlib import Path
import shutil,hashlib,json,re
ROOT=Path(__file__).resolve().parent.parent;src=ROOT/'Firmware/RykerConnect-REV04';dst=ROOT/'Firmware/RykerConnect-REV05'
if dst.exists():raise SystemExit('Destination exists; refusing to overwrite application changes')
shutil.copytree(src,dst,ignore=shutil.ignore_patterns('.pio','.vscode','__pycache__'))
(dst/'REV04-README.md').unlink(missing_ok=True)
(dst/'baseline-hashes.json').write_text(json.dumps({str(p.relative_to(src)):hashlib.sha256(p.read_bytes()).hexdigest() for sub in ['src','include'] for p in (src/sub).rglob('*') if p.is_file()},indent=2))
s=(dst/'platformio.ini').read_text();s=s.replace('default_envs = RykerConnect_REV04','default_envs = RykerConnect_REV05')
s += '\n[env:RykerConnect_REV05]\nextends = env:RykerConnect_REV04\nlib_deps =\n    olikraus/U8g2@^2.35.15\n    h2zero/NimBLE-Arduino@^2.5.0\n    bakercp/CRC32@^2.0.0\n    adafruit/Adafruit BME280 Library@2.3.0\n\n[env:RykerConnect_REV05_SensorTest]\nextends = env:RykerConnect_REV05\nbuild_flags =\n    ${env:RykerConnect_REV04.build_flags}\n    -D RYKER_SENSOR_TEST=1\n'
(dst/'platformio.ini').write_text(s)
for path in [*dst.joinpath('src').glob('*.cpp'),*dst.joinpath('include').glob('*.h')]:
 s=path.read_text()
 s=re.sub(r'^#include [<"](?:DS3231|mcp9808)\.h[>"]\s*\n','',s,flags=re.M|re.I)
 s=re.sub(r'^extern (?:DS3231 RTC|MCP9808 ts);\s*\n','',s,flags=re.M)
 s=re.sub(r'^(?:DS3231 RTC;|MCP9808 ts\(24\);)\s*\n','',s,flags=re.M)
 if path.name=='global_vars.h':s='#include "rtc_clock.h"\n'+s
 s=s.replace('timeString(RTC.getHour(h12Flag, pmFlag)) + ":" + timeString(RTC.getMinute())','rtcClockString()')
 if path.name=='functions.cpp':
  s=s.replace('void MCP_shutdown_wake(boolean sw);','')
  a=s.index('  #ifndef RYKER_REV02');z=s.index('  #endif',a)+len('  #endif');s=s[:a]+s[z:]
  a=s.index('void MCP_shutdown_wake(');z=s.index('String timeString',a);s=s[:a]+s[z:]
 if path.name=='main.cpp':
  a=s.index('  #ifdef RYKER_REV02\n  global_temp');z=s.index('  #endif',a)+len('  #endif')
  s=s[:a]+'  global_temp = NAN;\n  pollEnvironment();\n'+s[z:]
 if path.name=='ble_functions.cpp':
  s=s.replace('RTC.setSecond((uint8_t)data[2]);','').replace('RTC.setHour((uint8_t)data[0]);','').replace('RTC.setMinute((uint8_t)data[1]);','if (!rtcSetTime(data[0], data[1], data[2])) Serial.println("RTC: rejected or failed time update");')
 if path.name=='environment_sensor.cpp':s=s.replace('0x68','0x51')
 path.write_text(s)
print('Created isolated REV05 firmware snapshot')
