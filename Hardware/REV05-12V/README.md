> Historical revision: see [the hardware index](../README.md) for current options. Physical testing is pending.

# RykerConnect REV05-12V

Completed prototype package, 2026-09-13. Retains TPS26600 input protection and AP63205 fixed 5V buck. Replaces the expensive Wurth 744316470 with Sunlord MWSA0603S-4R7MT (C408447), including its manufacturer land pattern.

40 components / 30 BOM groups; observed component subtotal approximately $8.51 per carrier.

## Power selection
J1 pin 1 is **12V switched accessory positive**; pin 2 is GND. J8 is the external-source isolation link.

| Mode | J1 | J8 | Development-board USB |
|---|---|---|---|
| USB power/programming | Disconnected | Open (shunt removed) | Connected |
| External operation | Correct external supply | Closed | Unplugged |

Disconnect both sources before changing modes. The module VBUS header shares its USB power path. **Never connect USB and external power together with J8 closed.** The carrier cannot isolate that shared path. A data connection still counts as USB unless its VBUS conductor is physically disconnected and verified.

## Assembly and module
Use the three files in manufacturing together: Gerbers.zip, BOM.csv and CPL.csv. Quote 102 x 46mm, two-layer FR-4, 1.6mm, 1oz copper. All populated bodies are on Top. J1, J6, J7 and J8 require through-hole assembly; their solder joints extend below. Confirm JLCPCB accepts these operations and inspect every placement model before paying. Top-only population does not guarantee economy service eligibility.

The sockets fit Waveshare ESP32-S3-DEV-KIT-N16R8-M, SKU 28836, with pre-soldered headers. Do not substitute another N16R8 board without checking dimensions and pin mapping. USB points left, antenna right; upper socket J6 takes P1, lower J7 takes P2; pin 1 is at the antenna end. Retain insulated supports, antenna clearance and enclosure clearance for the 102 x 46mm carrier and tall module stack. The module is installed after carrier assembly.

Buy separately: development board, CR2032 cell, 2.54mm J8 shunt, Adafruit BME280 STEMMA QT breakout 2652, OLED assembly, light sensor, matching cables, supports and enclosure. JLCPCB BOM includes the battery holder and sockets, not these separate items. Do not use a rechargeable cell: there is no charger.

## Shared changes
Removed MCP9808 U2/C4. Replaced DS3231 with PCF8563T/5,518 (C7440), ABS07-32.768KHZ-T crystal (C130253), 22pF C0G load capacitor and two BAV199 isolation diodes. The cheaper RTC is not temperature compensated; verify clock drift in the finished unit. Added TLV1117LV33DCYR (C15578) and input/output capacitors for peripheral 3.3V. ESP 3.3V output pins are unconnected to the peripheral rail. No carrier USB connector or buttons are needed; use the development board's connectors/buttons.

## Connections
Connector pin numbers control wiring, not wire colors.

| Connector | Pin mapping |
|---|---|
| J5 remote BME280 | 1 GND, 2 peripheral 3.3V, 3 SDA/GPIO8, 4 SCL/GPIO18 |
| J3 OLED | 1 GND, 2 3.3V, 3 SCK/GPIO12, 4 MOSI/GPIO11, 5 reset/GPIO15, 6 DC/GPIO13, 7 CS1/GPIO14, 8 CS2/GPIO10 |
| J4 light sensor | 1 3.3V, 2 GPIO7 with 10k pulldown |

Use approximately 30cm/one foot of correctly pinned sensor cable; I2C runs at 100kHz. No extender is fitted. Verify rise times and reliability with the actual cable and breakout pullups. RTC address is 0x51; BME280 is 0x76 or 0x77. BMP280 lacks humidity and is not a substitute. Keep the sensor ventilated and away from engine/electronics heat, sun and water.

## Validation and delivery
Native KiCad ERC, all enabled DRC rules, connectivity and schematic parity passed with zero issues. Independent net assertions check power isolation and critical pin mappings. BOM and CPL references match; every placement is Top. The final reports, drawings and observed stock are under validation. Stock was sufficient for five of each build when checked on 2026-09-13, but is not reserved. Prices exclude PCB, assembly, attrition, setup, tax, freight and separately purchased items.

These are prototype manufacturing packages. Final JLCPCB orientation/model alignment and assembly-process acceptance remain pending, especially polarized parts and long sockets. Electrical load, boot, temperature, clock drift, RF, vibration and vehicle transient tests require real hardware. No order was placed.

Open RykerConnect.kicad_pro to edit the included native design. The repository Firmware/RykerConnect-REV05 directory contains matching source; build instructions are in Firmware/README.md. Prebuilt binaries are not included in this publication. Read POWER-AND-TEST.md before powering it. Rebuilding requires KiCad 10 and Python: run Hardware/rebuild_rev05.py with 12V or USB from the extracted archive; it uses the included reviewed SES and preserved generation inputs. Rerouting requires renewed review.
