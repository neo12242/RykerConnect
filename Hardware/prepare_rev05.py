"""Create independent REV05 work areas, preserving all earlier revisions."""
from pathlib import Path
import shutil,json,hashlib,urllib.request
R=Path(__file__).resolve().parent; old=R/'REV04-Carrier'
for variant in ['12V','USB']:
 d=R/('REV05-'+variant);d.mkdir(exist_ok=True)
 for folder in ['validation','manufacturing','sources']: (d/folder).mkdir(exist_ok=True)
 for name in ['build_design.py','mechanical_finish.py','sexpr.py','import_routes.py']:
  shutil.copy2(old/name,d/name)
 shutil.copy2(old/'RykerConnect.kicad_pro',d/'RykerConnect.kicad_pro')
 shutil.copy2(R/'REV05-PRD.md',d/'PRD.md')
 for f in (old/'sources').glob('*'):
  if f.is_file():shutil.copy2(f,d/'sources'/f.name)
 (d/'variant.json').write_text(json.dumps({'variant':variant,'revision':'REV05-'+variant}))
 (d/'validation'/'baseline-hashes.json').write_text(json.dumps({str(p.relative_to(R.parent)):hashlib.sha256(p.read_bytes()).hexdigest() for p in old.rglob('*') if p.is_file()},indent=2))
 sources={'pcf8563.pdf':'https://www.nxp.com/docs/en/data-sheet/PCF8563.pdf','tlv1117lv.pdf':'https://www.ti.com/lit/ds/symlink/tlv1117lv.pdf','abs07.pdf':'https://abracon.com/Resonators/ABS07.pdf','inductor.pdf':'https://jlc-prod-smt.oss-eu-central-1.aliyuncs.com/smtDataManualFile/8755182240320241664-C408447.pdf','bav199.pdf':'https://jlc-prod-smt.oss-eu-central-1.aliyuncs.com/smtDataManualFile/8590232994758750208-C3019921.pdf'}
 for name,url in sources.items():
  target=d/'sources'/name
  if not target.exists():
   try:urllib.request.urlretrieve(url,target)
   except Exception as e:print(name,str(e))
 (d/'sources'/'new-source-links.json').write_text(json.dumps(sources,indent=2))
 s=(d/'build_design.py').read_text()
 s=s.replace("'REV04'","'REV05-'+json.loads((R/'variant.json').read_text())['variant']")
 s=s.replace("x,y=coords[ref]","x,y=coords.get(ref,(345,185))")
 start=s.index("  if ref=='U3':nm,ty=");end=s.index('\n',start)
 s=s[:start]+"  if part.get('pins') and num in part['pins']:nm,ty=part['pins'][num][:2]"+s[end:]
 s=s.replace("if ref in ['U3','U5']", "if ref in ['U3','U5','U7']")
 s=s.replace("f=p.FootprintLoad(str(S/'RykerConnect.pretty'),part['fp'].split(':')[1])", "f=p.FootprintLoad(str(R/'RykerConnect.pretty' if (R/'RykerConnect.pretty'/(part['fp'].split(':')[1]+'.kicad_mod')).exists() else S/'RykerConnect.pretty'),part['fp'].split(':')[1])")
 a=s.index("text(pages[1],");z=s.index('# Power flags',a)
 s=s[:a]+'''text(pages[1],'Waveshare 28836 socketed module. ESP 3.3V header pins intentionally isolated.\\nPeripherals use U7. Disconnect USB before closing J8 for external power.',20,210)
text(pages[2],'12V variant only: fused accessory input. RTN is isolated from GND.\\nUSB variant: vehicle protection is external to this board.',20,235)
text(pages[3],'J8 open for USB. USB unplugged before closing J8.\\nExternal power and USB must not be connected simultaneously.\\nU7 supplies peripheral 3.3V; never bridge it to the ESP 3.3V output.',20,235)
text(pages[4],'PCF8563 at I2C 0x51. D5/D6 isolate the non-rechargeable CR2032.\\nX1 plus C18 form the 32.768kHz oscillator; verify drift on prototype.\\nRemote BME280 provides temperature/humidity/pressure; no local MCP9808.',20,235)
''' +s[z:]
 a=s.index("for net,page,x,y in [");z=s.index(':\n',a)
 s=s[:a]+"for net,page,x,y in [('+3.3V',1,365,210),('GND',2,360,235),('VBAT',4,330,235),('RTC_VDD',4,365,235),('+5V_MODULE',3,330,235)] + ([('+12V',2,330,235),('+5V_BUCK',3,360,235)] if json.loads((R/'variant.json').read_text())['variant']=='12V' else [('+5V_EXTERNAL',2,330,235)])"+s[z:]
 s=s.replace("'Clock and local sensors'","'Battery-backed clock and light sensor'")
 (d/'build_design.py').write_text(s)
 s=(d/'mechanical_finish.py').read_text().replace('RykerConnect REV04','RykerConnect REV05 '+variant)
 s=s.replace("label('12V+',122,111.5)","label('"+('12V+' if variant=='12V' else '5V ONLY')+"',121,111.5)")
 (d/'mechanical_finish.py').write_text(s)
print('Created independent REV05 variant work areas')
