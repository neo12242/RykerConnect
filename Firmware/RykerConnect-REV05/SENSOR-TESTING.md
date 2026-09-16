# Sensor test bench

## No hardware: website and Android emulator

Open http://127.0.0.1:8876/ and find **I2C test bench**. This is a Python BLE peripheral/display simulation, not execution of the ESP firmware.

- **BME280 connected:** sends temperature, humidity and pressure to Android over BLE. Temperature uses the existing slider; humidity and pressure use Apply sensor values.
- **Freeze BME280 / stale data:** emits an unavailable/stale measurement. The app must stop presenting a current measurement.
- **Clock connected (legacy simulator label may say DS3231):** simulates clock presence. Phone-supplied time can keep the preview clock available when this is off; the website reports the source. Without phone time or RTC, the preview shows --:--.
- Re-enable BME280 and clear Freeze to recover. Android polls the characteristic every five seconds. BLE pairing remains intact when a virtual I2C device is toggled.

The OLED top bar shows a dash for an unavailable sensor. Android labels valid virtual readings SIMULATED BME280. Forecast weather remains separate.

## Firmware builds

Run from the project Firmware directory:

```powershell
& ./.tools/pio/Scripts/platformio.exe run -d RykerConnect-REV05 -e RykerConnect_REV05
& ./.tools/pio/Scripts/platformio.exe run -d RykerConnect-REV05 -e RykerConnect_REV05_SensorTest
```

Normal `RykerConnect_REV05` remains the default and reads real BME280 data. The explicit `RykerConnect_REV05_SensorTest` build starts with injected BME280 and clock samples; use it only on a test bench. Neither command flashes a device.

## Serial diagnostics (460800 baud, newline-terminated commands)

Both builds support:

| Command | Behavior |
|---|---|
| `SCAN` | Reports 0x51, 0x76 and 0x77 ACK/missing for physical mode; reports virtual presence in injected mode. ACK alone does not verify sensor identity. |
| `STATUS` | Reports source, validity, sequence and sample age. |

The **SensorTest build only** also supports:

| Command | Behavior |
|---|---|
| `BME ON` | Enable fresh injected BME280 readings. |
| `BME OFF` | Inject missing sensor. |
| `BME STALE` | Inject stale/unavailable measurement. |
| `ENV 5 80 995` | Set Celsius, relative humidity percent and pressure in hPa. Does not clear OFF/STALE; use BME ON to recover. |
| `BME REAL` | Return BME280 acquisition to physical I2C. The test clock remains simulated. |
| `RTC OFF` / `RTC ON` | Disable/enable the injected clock; OFF displays --:-- in the firmware test build. |

The test clock accepts phone time updates; turning it back on resumes the running clock. Production firmware does not compile injection commands. Web controls operate the Python simulator only; use Serial for the firmware test build on an actual ESP.

## BLE compatibility

Environment UUID: `cac36b81-1245-4f86-a437-001dc1b86a02`, encrypted read, 20 bytes little-endian:

| Bytes | Meaning |
|---|---|
| 0 | Version 1 |
| 1 | Flags: bit 0 valid, bit 1 simulated; remaining bits reserved |
| 2–3 | Sequence |
| 4–7 | Float Celsius |
| 8–11 | Float humidity % |
| 12–15 | Float pressure hPa |
| 16–19 | Unsigned sample age milliseconds |

Existing physical frames (flags 0/1) are unchanged. Updated Android accepts simulated valid frames (flags 3) and labels them. Older Android versions reject the new simulated flag instead of mislabeling it as physical. Frames older than 15 seconds remain unavailable.

## Validation

Both PlatformIO environments compiled successfully. Normal binary excludes `BME ON`; test binary contains it. BLE disconnect/stale/recovery and numeric values are exercised with simulator tests and the Android emulator. Actual I2C electrical behavior and serial-command execution on physical hardware still require an ESP test bench.
