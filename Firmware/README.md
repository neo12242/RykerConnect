# Firmware

REV05, REV05C-12V and REV05-USB use `RykerConnect-REV05/`. It targets the socketed N16R8 ESP32-S3 module, display reset GPIO15, BME280 at 0x76/0x77 and PCF8563 RTC at 0x51. REV04 uses a different RTC configuration; do not flash its binary onto a REV05 design expecting equivalent behavior.

## Build

Install PlatformIO Core (or the PlatformIO VS Code extension). From this repository root:

```powershell
pio run -d Firmware/RykerConnect-REV05 -e RykerConnect_REV05
```

The project pins Espressif32 6.12.0 and its library versions in `platformio.ini`. Production output is under `Firmware/RykerConnect-REV05/.pio/build/RykerConnect_REV05/`. Build from source; old upstream/prebuilt binaries are not this release.

For the explicit sensor-injection test target:

```powershell
pio run -d Firmware/RykerConnect-REV05 -e RykerConnect_REV05_SensorTest
```

Use the normal target for ordinary firmware. SensorTest changes behavior for bench testing. See revision source/testing notes before injecting sensor values.

## Flash when hardware is ready

Follow the chosen board's POWER-AND-TEST instructions first: disconnect external power and open J8 before connecting USB. Select your actual serial port, then use PlatformIO's upload target for the correct environment. For example, replace `COM_PORT` below with the detected port:

```powershell
pio run -d Firmware/RykerConnect-REV05 -e RykerConnect_REV05 -t upload --upload-port COM_PORT
pio device monitor --port COM_PORT --baud 460800
```

PlatformIO supplies the build's flash layout; do not guess offsets or mix partition/bootloader images from other revisions. No device was flashed for publication. First boot, flash/PSRAM, RTC, displays, sensor cabling, BLE and power/temperature behavior require physical verification.

The existing OTA/update paths retain upstream behavior and are not proof that an upstream binary matches these carrier boards. Prefer the explicit matching source build until a separately verified release/update channel exists.
