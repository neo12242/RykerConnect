from pathlib import Path
import subprocess,sys,shutil,json
R=Path(__file__).resolve().parent;F=R.parent/'Firmware/RykerConnect-REV05';V=F/'validation';V.mkdir(exist_ok=True)
# Isolated target: no unused MCP9808/DS3231 dependency or obsolete board environments.
(F/'platformio.ini').write_text('''[platformio]
default_envs = RykerConnect_REV05
[env:RykerConnect_REV05]
platform = espressif32@6.12.0
board = esp32-s3-devkitc-1
framework = arduino
monitor_speed = 460800
upload_speed = 921600
board_build.mcu = esp32s3
board_build.flash_size = 16MB
board_upload.flash_size = 16MB
board_build.partitions = min_spiffs.csv
board_build.arduino.memory_type = qio_opi
board_build.flash_mode = qio
board_build.psram_type = opi
build_flags =
    -std=c++17
    -D ARDUINO_USB_MODE=1
    -D ARDUINO_USB_CDC_ON_BOOT=1
    -D CONFIG_BT_NIMBLE_ROLE_CENTRAL_DISABLED
    -D CONFIG_BT_NIMBLE_ROLE_OBSERVER_DISABLED
    -D CONFIG_BT_NIMBLE_MAX_CONNECTIONS=1
    -D RYKER_REV02=1
    -D RYKER_REV04=1
    -D RYKER_REV05=1
    -D BOARD_HAS_PSRAM
lib_deps =
    olikraus/U8g2@2.36.18
    h2zero/NimBLE-Arduino@2.5.1
    bakercp/CRC32@2.0.1
    adafruit/Adafruit BME280 Library@2.3.0
    adafruit/Adafruit BusIO@1.17.4
    adafruit/Adafruit Unified Sensor@1.1.15
[env:RykerConnect_REV05_SensorTest]
extends = env:RykerConnect_REV05
build_flags =
    ${env:RykerConnect_REV05.build_flags}
    -D RYKER_SENSOR_TEST=1
''')
for env in ['RykerConnect_REV05','RykerConnect_REV05_SensorTest']:
 with (V/(env+'-build.log')).open('w') as f:subprocess.run([sys.executable,'-m','platformio','run','-d',str(F),'-e',env],stdout=f,stderr=subprocess.STDOUT,check=True)
 print(env,'build passed',flush=True)
with (V/'rtc-native-test.log').open('w') as f:subprocess.run(['cmd','/c',str(F/'test/run-native.cmd')],stdout=f,stderr=subprocess.STDOUT,check=True)
print('Native driver tests passed',flush=True)
out=F/'binaries';out.mkdir(exist_ok=True)
for name in ['firmware.bin','bootloader.bin','partitions.bin']:shutil.copy2(F/'.pio/build/RykerConnect_REV05'/name,out/name)
(V/'validation.json').write_text(json.dumps({'production_build':'passed','bench_build':'passed','rtc_native_tests':'passed','physical_boot':'not tested','flash_mb':16,'psram_mb':8,'rtc_address':'0x51'},indent=2))
