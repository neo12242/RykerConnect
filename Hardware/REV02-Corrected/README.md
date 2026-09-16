> Historical revision: see [the hardware index](../README.md) for current options. Physical testing is pending.

# RykerConnect REV02 — corrected prototype package

Built 2026-09-12 under the hardware implementation plan. **Prepared for fabrication/assembly quoting and prototype review; not yet released for vehicle installation.** Original Downloads files and the earlier review archive are unchanged (SHA-256 checked).

## Files to use at JLCPCB

1. PCB fabrication: `manufacturing/Gerbers.zip` (new copper, masks, silkscreen, paste, outline and separate plated/non-plated drills).
2. Assembly BOM: `manufacturing/BOM.csv`.
3. Component placement: `manufacturing/CPL.csv`.

These three files belong together. Do not mix them with the original production.zip or earlier review BOM/CPL. There are **32 fitted components, 21 BOM groups and 32 catalog-linked placements**, all on top. The full delivery archive contains sources and documentation and is not the Gerber upload file.

Request assembly including the **through-hole J1** and the USB connector's mechanical tabs. Confirm that JLCPCB accepts each process before ordering. C1, C2, JP1 and off-board Q1 are not populated. Test pads, mounting holes and artwork are not purchased parts. The CR2032 battery, OLED module, cables, media remote, sensor module and enclosure are separate items; the BOM includes the battery holder only. Turnkey PCB assembly does not imply complete product assembly, cable installation, battery insertion or firmware programming.

## Implemented corrections

- Fixed USB D-minus clearance, SCL clearance at the board edge, and electrical continuity between all four USB shell tabs. JP1 remains open: the shield is not intentionally shorted to ground.
- Retained the original 0.15 mm copper clearance and 0.5 mm copper-to-edge rules. The short SCL section is 0.13 mm wide. Removed stale generated teardrops and refilled copper zones.
- Verified the Molex drawing: J1's 3 mm retention hole was already correct. Moved the connector 0.5 mm toward the board edge to meet its maximum 10.16 mm peg-to-edge spacing (now 9.88 mm).
- Replaced four large I2C test pads with keyed **J5**, a JST SH four-pin connector. Added appropriate routing and removed misleading old pin labels.
- Corrected regulator symbol pin types and stale diode identity; supplied project-local symbols and footprints so the design no longer depends on the author's missing libraries.
- Selected the ESP32-S3-WROOM-1-N8 (8 MB flash, no PSRAM) and built a matching firmware target. Its optional center EPAD is unsoldered, as permitted by Espressif; ground pins 1 and 40 remain connected. Thermal/RF behavior still requires physical testing.
- Populated purchasing identities for every fitted group. Explicit changes include Samsung C packaging suffix, Molex tin-plated 436500200, GCT USB4105-GF-A, KOA RS73 5.1k 0.5% 250 mW resistors and the N8 ESP32 module. See `selected-parts.json` for per-part sources, original identities and sourcing notes.

## External environmental sensor

Use the assembled [Adafruit BME280 STEMMA QT module, product 2652](https://www.adafruit.com/product/2652). It measures temperature, relative humidity and absolute barometric pressure. A BMP280 is not equivalent: it lacks humidity and the firmware rejects its chip ID.

| J5 pin | Signal |
|---|---|
| 1 | Ground |
| 2 | +3.3 V |
| 3 | SDA (ESP32 GPIO8) |
| 4 | SCL (ESP32 GPIO18) |

Use a correctly wired four-conductor JST SH/STEMMA QT cable; verify pin numbering rather than wire colors. Do not apply 5 V to this port or power the sensor from a second supply through the same cable. Power off before connecting. Firmware uses 100 kHz I2C, detects addresses 0x76/0x77, samples every five seconds and marks data older than 15 seconds unavailable. Startup may take one polling cycle before the first reading.

The existing temperature calibration offset is subtracted from the BME280 temperature. Pressure is local absolute pressure, not a sea-level weather forecast value. Place the sensor away from engine heat, sunlight and electronics heat, inside a ventilated splash-protected enclosure. It is not a waterproof bare module.

the project owner specified approximately **one foot (30cm)** of sensor cable. The initial configuration is direct 3.3V I2C at 100kHz using J5, without an extender. This is a design starting point, not a cable-length guarantee: verify total bus capacitance, pull-ups, rise time and operation with the engine/electrical loads running. [NXP's I2C specification](https://www.nxp.com/docs/en/user-guide/UM10204.pdf) defines electrical/timing limits rather than a universal cable-length limit.

The main-board interface can stay the same with an external extender if testing requires one. A differential extension normally needs an adapter at **each end**, with the first close to J5. Cable routing, supply drop, ESD and noise must be qualified together. This revision adds no dedicated external-port ESD or vehicle surge-protection circuit.

## Firmware and Android

`Firmware/RykerConnect-REV02` in the project contains the source. Build with `platformio run -e RykerConnect_REV02`; flash a first board using `platformio run -e RykerConnect_REV02 -t upload --upload-port COMx` with the actual port. PlatformIO supplies the complete bootloader/partition upload sequence. The copied firmware.bin alone is an application image, not a complete blank-chip image.

The target retains the original min_spiffs OTA partition layout while configuring 8 MB physical flash. It pulses shared OLED reset GPIO15 and initializes both SSD1320 controllers at 8 MHz. The exact SOG320132A module variant and display operation remain untested. Its distinct hardware-version string prevents offering the original REV01 OTA image; no hosted REV02 OTA release has been published.

The Android debug APK adds an **Outside sensor** card with temperature, humidity, pressure and freshness. It reads an optional encrypted BLE characteristic; older firmware reports unsupported/unavailable. GPS weather remains a separate forecast. Simulator data and board/RTC temperature never fill in for missing outside measurements. The APK was built but was not installed on a physical phone in this task. Prior simulator-only navigation/trip/weather display extensions have not been ported to the physical firmware by this change.

BLE contract: UUID `cac36b81-1245-4f86-a437-001dc1b86a02`; 20 bytes, little endian: version u8=1, valid u8, sequence u16, temperature float32 Celsius, humidity float32 percent, pressure float32 hPa, sample age u32 milliseconds. Invalid frames must not display their numeric fields.

## Validation and remaining gates

- KiCad 10.0.6: **0 configured DRC violations, 0 unconnected items, 0 schematic parity issues, 0 ERC violations**. These results are not a claim that every optional/ignored KiCad rule was enabled.
- Native placements and BOM reference sets match exactly. Original source hashes match the prior audit. Reports and board/schematic PDF plots are in `validation`.
- ESP32 REV02 firmware build succeeded: RAM 59,604 / 327,680 bytes; application flash 1,175,701 / 1,966,080 bytes.
- Android build succeeded; **32 unit tests passed**, including malformed sensor frames, bad values, age boundaries and clearing stale readings.
- No physical PCB, sensor, OLED, RF, power transient or environmental test has occurred.

Before ordering, confirm exact JLCPCB model alignment, polarity, pin 1 and mechanical fit, especially J1/J2/U1/U2/U4/J5. CPL preserves the earlier assembler's U1 +180-degree and U2 -90-degree adjustments; J1 uses a -1.5 mm X/-2.53 mm Y centroid offset applied to its **new** position. These are explicit preview candidates, not verified JLCPCB model rotations. The raw KiCad placement file is retained for comparison.

Catalog matches do not reserve stock. Several exact parts may require preorder/Global Sourcing, particularly C5/C9 and D1; confirm every BOM line and mixed assembly process in the quote. Do not let the uploader silently replace package variants or voltage ratings.

**Updated installation requirement:** the project owner specified 12V and approximately one foot of sensor cable. Treat the input as vehicle 12V until an upstream protected supply is established. Still needed: ignition-switched accessory feed versus direct battery, existing fuse/protection details, and the exact OLED module variant. The current input includes a 25 V capacitor and a 30 V diode; it is not a completed vehicle-transient protection design. Input protection remains a fabrication gate for vehicle use. A fuse addresses fault current; coordinated reverse-polarity and overvoltage/load-dump protection must also be designed. [TI's automotive protection reference](https://www.ti.com/product/LM7480-Q1) is a candidate design basis, not a selected or added BOM part.

## Prototype acceptance checklist

1. Inspect assembly/model orientation and continuity with power off; leave JP1 open.
2. Use a current-limited, protected bench supply at the intended input voltage; verify 3.3 V before connecting peripherals.
3. Flash the REV02 target; confirm USB, startup, both halves of the OLED, buttons, RTC and Bluetooth.
4. Connect BME280 with power off. Compare all three measurements with a reference after stabilization.
5. Disconnect/reconnect with power off between changes; verify unavailable status and recovery. Test stale BLE readings and phone reconnect.
6. Qualify the actual cable/extenders, enclosure temperature bias and electrical noise. Validate vehicle power protection separately before installation.

## Rebuild and provenance

Run `modify_board.py` using KiCad's Python, then `update_schematics.py` using Python with this directory on its module path, then `sync_board_fields.py` using KiCad's Python, and finally `export_release.py`. The first two scripts read the upstream Hardware/KiCad/RykerConnect-REV02 project and write only this corrected directory. Export uses KiCad CLI and aborts on configured violations or mismatched BOM/placement sets. Save your own changes before rerunning: rebuilding intentionally replaces the generated corrected files.

Manufacturer references: [Molex mechanical drawing](https://www.molex.com/content/dam/molex/molex-dot-com/products/automated/en-us/salesdrawingpdf/436/43650/436500200_sd.pdf), [GCT USB4105](https://gct.co/connector/usb4105), [Espressif module datasheet](https://documentation.espressif.com/esp32-s3-wroom-1_wroom-1u_datasheet_en.pdf), [Bosch BME280](https://www.bosch-sensortec.com/en/products/environmental-sensors/humidity-sensors-bme280). Per-part catalog sources are in `selected-parts.json`.
