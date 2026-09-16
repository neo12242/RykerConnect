> Historical revision: see [the hardware index](../README.md) for current options. Physical testing is pending.

# RykerConnect REV04 - socketed ESP32 carrier

**Completed prototype design, 2026-09-13.** This is the revised single-side assembly set. Use these files together; do not mix them with REV02, REV03 or the original production.zip. No boards have been ordered, assembled or physically tested.

## Files for JLCPCB

1. `manufacturing/Gerbers.zip` - two copper layers, masks, paste, silkscreen, outline and plated/non-plated drills.
2. `manufacturing/BOM.csv` - 36 components in 27 groups, every group has an explicit LCSC Part Number and manufacturer part number.
3. `manufacturing/CPL.csv` - all 36 placements are **Top**.

Start with a 102 x 46mm, two-layer FR-4, 1.6mm, 1oz copper prototype quote. The old board was 102 x 34mm. The two original mounting-hole positions remain; the enclosure needs more width and clearance above the plug-in module. Standard routing minimum clearance is 0.15mm. Do not select thicker copper without rechecking the fabrication rules.

Request top-side SMT **and through-hole assembly**. J1, J6, J7 and J8 are through-hole parts with their bodies on top; their solder joints protrude below. A single populated side does not guarantee the lowest price or eligibility for an economy SMT-only service. Check JLCPCB's process acceptance and final placement preview before paying. This task did not upload the design or obtain a quote.

The catalog stock observation on September 13 covered all 27 identities and enough available quantity for five boards. It is recorded in `validation/stock-check.json`; stock is not reserved. The socket was the smallest supply pool (52 pieces, two per board). The RTC and inductor are significant cost items; the stocked RTC is approximately $9.22 at the public single-piece tier. A final total price cannot be inferred from component prices alone.

## What changed

| Earlier shortage | REV04 treatment |
|---|---|
| C5/C9, TDK C2168289 | C5: Samsung 10uF/25V C96446; C9: larger 1206 10uF/50V C13585 |
| C6/C10, KEMET C1882722 | Samsung 100nF/50V C1591; standardized other 100nF positions too |
| D1, Toshiba C5609371 | Removed with old USB/vehicle diode OR-ing; J8 isolates the new 5V supply during USB service |
| R1-R4, KOA C5890991 | USB resistors R1/R2 removed; R3/R4 are stocked 4.7k I2C pullups C23162 |
| S1/S2, Wurth C7464054 | Carrier buttons removed; use the development board's buttons |
| U3, DS3231MZ/V+T C3304429 | DS3231SN#T&R C9866, with a new 16-pin SOIC footprint and corrected pin mapping |

The old JLCPCB spreadsheet also lacked most REV03 protection groups. The new BOM includes the entire carrier, including the retained accessory protection. U5 is now a fixed 5V AP63205WU-7 (C2071056), with its matching inductor, input/output capacitors and bootstrap circuit. The development board supplies the carrier peripherals with 3.3V. C12/C13 provide 44uF nominal at 5V. R9 moved to a stocked 0603 10k part without changing the divider ratio.

## Purchase the matching plug-in module

The socket geometry is for **Waveshare ESP32-S3-DEV-KIT-N16R8-M, SKU 28836**, the pre-soldered-header variant: 16MB flash and 8MB octal PSRAM. This documented module was selected because the exact Amazon listing's dimensions and pin mapping were not established. Do not assume another board labeled N16R8 is interchangeable.

Manufacturer references: [module overview and variants](https://docs.waveshare.com/ESP32-S3-DEV-KIT-N8R8), [drawings and schematic](https://docs.waveshare.com/ESP32-S3-DEV-KIT-N8R8/Resources-And-Documents). Source drawings are included under `sources`.

Both rows have 22 pins at 2.54mm pitch, 22.86mm apart. Module body: 63.30 x 25.40mm. The carrier drawing shows its projected outline. With the carrier text upright, the module USB connector points **left**, and its antenna points **right**. P1 plugs into J6 (upper row); P2 plugs into J7 (lower row). Pin 1 is at the antenna end of both rows. Do not shift either row by one position.

Four 2mm non-plated holes H3-H6 match the module drawing: 59.70 x 21.80mm spacing, 1.80mm from its edges. Use insulated supports and hardware appropriate to those holes; the manufacturer schematic notes M1.4 screws. Measure the fully seated module-to-carrier gap before choosing support lengths. The 8.5mm socket body plus the module header insulator makes the stack appreciably taller than a bare ESP32 module. Supports must not lift the pins out of their sockets or touch components. Keep the antenna area clear of metal, cables and a metal enclosure. RF range and vibration retention need testing with the finished assembly.

JLCPCB assembles the carrier sockets. You plug in and secure the separately purchased module afterward. That step is solderless if the specified pre-soldered module is used. The separate module, CR2032 cell, J8 shunt, remote sensor, OLED, cables, supports and enclosure are not included in the PCB BOM.

## Power and USB service

Normal operation: fused, switched accessory 12V -> protected input -> 5V buck -> **closed J8 shunt** -> development board -> 3.3V peripherals. Keep J8 closed only when USB is disconnected.

**Before connecting USB: turn off/disconnect accessory power and remove the J8 shunt.** Ignition off alone is insufficient: USB could backfeed the converter through a closed J8. After service, disconnect USB before reinstalling J8. Use a removable 2.54mm two-contact shunt; do not permanently solder J8 closed. A shunt is a separate accessory, not a missing PCB component.

J1 pin 1 is accessory +12V; pin 2 is GND. Never feed 12V into J8, the ESP headers, or J5. The buck feedback remains connected before J8, so removing the service link does not open the regulator's feedback loop. No carrier USB connector or carrier BOOT/RESET buttons remain.

The retained TPS26600 protection has nominal 17.85V overvoltage cutoff, 5.95V undervoltage startup and approximately 1A input current limiting. Its RTN and exposed pad are intentionally separate from GND. The asymmetric TVS pair has common anodes. A properly fused vehicle branch is still required. This is a prototype for normal 9-16V operation; it has no full unsuppressed load-dump or automotive certification claim. See `POWER-AND-TEST.md` for calculations and acceptance tests.

## Remote sensor and connectors

Keep the separate [Adafruit BME280 STEMMA QT module, product 2652](https://www.adafruit.com/product/2652), at about one foot / 30cm of cable. It measures temperature, relative humidity and absolute air pressure; a BMP280 cannot substitute for humidity measurement. Firmware uses 100kHz I2C. No extender is required in the initial design, but cable/noise operation must be measured.

| J5 pin | Signal |
|---|---|
| 1 | GND |
| 2 | 3.3V |
| 3 | SDA / ESP GPIO8 |
| 4 | SCL / ESP GPIO18 |

Use a correctly pinned JST SH/STEMMA QT cable and disconnect power before plugging it in. Keep the sensor away from electronics/engine heat and sun, ventilated and splash protected. Account for the breakout's parallel pullups when checking I2C edges. Board-local MCP9808 temperature remains separate from the remote BME280 reading. RTC address is 0x68; BME280 may be 0x76 or 0x77; the local MCP9808 is 0x18.

J3 OLED: pins 1 GND, 2 3.3V, 3 clock/GPIO12, 4 MOSI/GPIO11, 5 display reset/GPIO15, 6 DC/GPIO13, 7 CS1/GPIO14, 8 CS2/GPIO10. J4 light sensor: pin 1 3.3V, pin 2 GPIO7 with a 10k pull-down. Connector pin numbers, not cable colors, control wiring.

## Validation and remaining acceptance

KiCad 10.0.6 reports **zero ERC, zero DRC violations, zero unconnected items and zero schematic parity issues**. All listed DRC rule severities are enabled; no per-item exclusions are present. Silkscreen clearance and library-footprint checks were enabled and corrected, including the inherited U2 reference on the paste layer. Schematic PDFs and top/bottom board drawings were visually reviewed.

Independent net assertions check power isolation, RTC pins, display/ESP mapping, remote I2C, TVS polarity and the separate RTN network. BOM and CPL cover the same 36 references. Socket/header placement coordinates use their geometric centers, not pin 1. J1 retains its reviewed centroid adjustment; U2 retains its catalog rotation adjustment. Other rotations are native KiCad starting values. **Final JLCPCB model alignment and process acceptance remain unverified**, especially connector pin 1, diode cathodes, U3/U5/U6 orientation and the long sockets.

The N16R8 firmware target built successfully. Hardware boot, flash/PSRAM detection, electrical load/temperature, enclosure fit, vibration and vehicle transients cannot be validated without an assembled prototype. Follow `POWER-AND-TEST.md` before vehicle installation. REV03 hardware and original Downloads inputs match their prior hashes. REV04 firmware copies the current workspace REV02 source, which already differs from the older REV03 delivery; this task adds the N16R8 configuration in a separate folder and does not edit the prior firmware.

## Firmware and rebuilding

Use `Firmware/RykerConnect-REV04` in the delivery archive and the `RykerConnect_REV04` PlatformIO environment. See its `REV04-README.md`. The source preserves existing display, RTC and remote-sensor behavior; this revision changes the flash/PSRAM configuration. The Android application was not changed or rebuilt in this task.

Open `RykerConnect.kicad_pro` to edit. The local symbol and footprint libraries are included. Generation scripts require KiCad 10's bundled Python; `export_release.py`, `check_calculations.py`, and `render_previews.py` use ordinary Python. `rebuild.py` reproduces the reviewed routes from `validation/carrier.ses`; no autorouter download is needed. Its small preserved REV03 input set is included alongside REV04 in the archive. Intentional rerouting invalidates the final manual routing adjustments and requires another complete review.

Original RykerConnect attribution and license are retained in the delivery archive. No original manufacturer source document was treated as an instruction to take action.

