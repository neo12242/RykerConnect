# REV04 firmware

Target: Waveshare ESP32-S3-DEV-KIT-N16R8-M, SKU 28836. PlatformIO environment: `RykerConnect_REV04` (default).

This inherits the working REV02 GPIO/sensor implementation and selects 16MB flash, QIO flash mode and 8MB OPI PSRAM support (`qio_opi`, `BOARD_HAS_PSRAM`). GPIO35-37 are unused. The retained `min_spiffs.csv` partition layout fits within 16MB; it does not allocate all 16MB. The isolated sensor-test environment is not the default or delivered application build.

Build from this folder with `pio run -e RykerConnect_REV04`. For a complete first-board upload, remove the carrier J8 shunt and disconnect accessory power, connect the module USB, then run `pio run -e RykerConnect_REV04 -t upload --upload-port COMx` with the actual port. PlatformIO writes the matching bootloader, partition table, OTA initialization and application. Use the module's BOOT/RESET buttons if needed. Disconnect USB before replacing J8.

The delivery `software` folder includes the built images. Confirmed build offsets are bootloader 0x0000, partitions 0x8000, boot_app0 0xE000 and application 0x10000. `firmware.bin` is only the application; do not flash it at zero. Prefer the PlatformIO upload command, which also applies the configured flash options. Do not upload a test-environment binary to a vehicle prototype.

Build succeeded on 2026-09-13 with PlatformIO espressif32 6.12.0. Compiler environment confirms `qio_opi` and `BOARD_HAS_PSRAM`; no actual module has been flashed or boot-tested. Verify memory detection, OLED reset, BLE, RTC retention and real BME280 readings on the prototype. This change does not update the Android app or add media-remote functionality.
