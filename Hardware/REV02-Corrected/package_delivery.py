"""Package corrected sources, manufacturing inputs and locally built software."""
from pathlib import Path
import hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
R=Path(__file__).resolve().parent; W=R.parent.parent
F=W/'Firmware/RykerConnect-REV02'; B=F/'.pio/build/RykerConnect_REV02'
D=R/'software';D.mkdir(exist_ok=True)
for name in ['bootloader.bin','partitions.bin','firmware.bin']:
    shutil.copy2(B/name,D/name)
shutil.copy2(W/'Android/app/build/outputs/apk/debug/app-debug.apk',D/'RykerConnect-REV02-debug.apk')
shutil.copy2(W/'Firmware/REV02-build.log',R/'validation/firmware-build.log')
results=[ET.parse(f).getroot() for f in (W/'Android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')]
report={k:sum(int(x.get(k,0)) for x in results) for k in ['tests','failures','errors','skipped']}
assert report==dict(tests=32,failures=0,errors=0,skipped=0),report
report['suites']=[dict(name=x.get('name'),tests=int(x.get('tests',0))) for x in results]
(R/'validation/android-test-summary.json').write_text(json.dumps(report,indent=2))
deps=[]
for f in (F/'.pio/libdeps/RykerConnect_REV02').glob('*/.piopm'):
    deps.append(json.loads(f.read_text()))
(R/'validation/firmware-library-resolution.json').write_text(json.dumps(deps,indent=2))
(D/'README.txt').write_text('Firmware is for the corrected REV02 ESP32-S3-WROOM-1-N8 target only. Use PlatformIO upload for the complete blank-chip flash sequence; firmware.bin is an application image. No device was flashed. APK is a debug build, not installed by this delivery. Read ../README.md for release gates.\n')
files={}
for f in R.rglob('*'):
    if f.is_file() and not any(x in f.parts for x in ['__pycache__','sources']) and f.name not in ['before.pdf','before.png','drc.json']:
        files['Hardware/REV02-Corrected/'+f.relative_to(R).as_posix()]=f
files['README.md']=R/'README.md'
files['Hardware/PRD-hardware-approved.md']=R.parent/'PRD-hardware-approved.md'
for f in F.rglob('*'):
    if f.is_file() and not any(x in f.relative_to(F).parts for x in ['.pio','.vscode','pathlib','__pycache__']):
        files['Firmware/RykerConnect-REV02/'+f.relative_to(F).as_posix()]=f
for rel in ['ride/EnvironmentState.kt','ride/EnvironmentCard.kt','ride/RidingDashboard.kt','logic/BLEDeviceConnection.kt']:
    files['Android-changes/main/'+rel]=W/'Android/app/src/main/java/de/chaostheorybot/rykerconnect'/rel
files['Android-changes/test/EnvironmentStateTest.kt']=W/'Android/app/src/test/java/de/chaostheorybot/rykerconnect/ride/EnvironmentStateTest.kt'
archive=R.parent/'RykerConnect-REV02-Corrected-Prototype.zip'
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
    for name,f in sorted(files.items()):z.write(f,name)
    z.writestr('SHA256SUMS.json',json.dumps({name:hashlib.sha256(f.read_bytes()).hexdigest() for name,f in sorted(files.items())},indent=2))
with zipfile.ZipFile(archive) as z:assert z.testzip() is None
print(f'Validated {archive.name}: {archive.stat().st_size:,} bytes, {len(files)} files')
