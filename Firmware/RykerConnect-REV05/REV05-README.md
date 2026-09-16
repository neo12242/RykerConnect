# REV05 firmware (both carrier variants)

Production environment: RykerConnect_REV05. Development board: Waveshare ESP32-S3-DEV-KIT-N16R8-M, 16MB flash and 8MB octal PSRAM. Use PlatformIO to build/upload this directory. The carrier variants share the same firmware.

PCF8563 at 0x51 replaces DS3231. MCP9808 support/dependencies were removed. The BME280 temperature/humidity/pressure, display and BLE application are retained from the current REV04 source snapshot. Invalid or unavailable hardware time displays --:--; phone time writes the replacement RTC. A battery retains time across switched power loss.

Production and SensorTest PlatformIO builds passed. Native RTC tests cover malformed BCD, STOP/low voltage, NACK/short reads, valid boundaries and write-failure recovery. Reports are in validation. Actual flash/PSRAM detection and boot still require a physical device.

Build with PlatformIO to produce firmware.bin, bootloader.bin and partitions.bin in .pio/build/RykerConnect_REV05; prebuilt binaries are not shipped in this publication. Prefer PlatformIO upload, which uses the target's correct flash offsets; these are separate images, not a merged zero-offset file. The SensorTest environment is for explicit simulated readings only; see SENSOR-TESTING.md. No firmware was flashed during this task.

Disconnect external power and open J8 before plugging in USB to program. Unplug USB before closing J8 for external operation.
