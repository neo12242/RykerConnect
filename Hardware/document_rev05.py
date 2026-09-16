from pathlib import Path
import json,shutil
R=Path(__file__).resolve().parent
common='''## Assembly and module
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

Open RykerConnect.kicad_pro to edit the included native design. Firmware/RykerConnect-REV05 in the archive contains matching source and production binaries. Read POWER-AND-TEST.md before powering it. Rebuilding requires KiCad 10 and Python: run Hardware/rebuild_rev05.py with 12V or USB from the extracted archive; it uses the included reviewed SES and preserved generation inputs. Rerouting requires renewed review.
'''
for v in ['12V','USB']:
 d=R/('REV05-'+v); report=json.loads((d/'validation/release-validation.json').read_text())
 voltage='12V switched accessory' if v=='12V' else 'regulated 5.0V external'
 intro=('Retains TPS26600 input protection and AP63205 fixed 5V buck. Replaces the expensive Wurth 744316470 with Sunlord MWSA0603S-4R7MT (C408447), including its manufacturer land pattern.' if v=='12V' else 'Removes the entire 12V protection/buck/inductor circuit. Use development-board USB-C power or the external regulated 5V injection connector. This input has no automotive surge protection; never connect vehicle 12V directly.')
 power=f'''## Power selection
J1 pin 1 is **{voltage} positive**; pin 2 is GND. J8 is the external-source isolation link.

| Mode | J1 | J8 | Development-board USB |
|---|---|---|---|
| USB power/programming | Disconnected | Open (shunt removed) | Connected |
| External operation | Correct external supply | Closed | Unplugged |

Disconnect both sources before changing modes. The module VBUS header shares its USB power path. **Never connect USB and external power together with J8 closed.** The carrier cannot isolate that shared path. A data connection still counts as USB unless its VBUS conductor is physically disconnected and verified.
'''
 (d/'README.md').write_text(f'# RykerConnect REV05-{v}\n\nCompleted prototype package, 2026-09-13. {intro}\n\n{report["components"]} components / {report["bom_groups"]} BOM groups; observed component subtotal approximately ${report["component_cost_usd_tier1"]:.2f} per carrier.\n\n'+power+'\n'+common,encoding='utf-8')
 (d/'POWER-AND-TEST.md').write_text(f'''# REV05-{v} power and prototype acceptance

{power}
## Design limits and calculations
{'Normal accessory target is 9-16V on a fused switched branch. Retained protection has nominal 5.95V undervoltage startup, 17.85V overvoltage cutoff and approximately 1A input current limiting. TPS26600 RTN/exposed pad is intentionally separate from GND. The buck IC is rated 2A, but that is not a validated system continuous-current rating. This is not an automotive-certified or unsuppressed load-dump-qualified design. L1 is 4.7uH, 33 milliohm maximum DCR, with ample catalog saturation margin relative to the 2A buck rating; validate switching waveform and temperature under load.' if v=='12V' else 'J1 requires regulated 5.0V +/-5%, correct polarity. Start with a supply rated at least 1A; 2A gives useful headroom. A higher current rating does not force that current into the load. Do not use a 12V source or USB-PD voltage trigger. For a vehicle, the external supply must itself provide suitable automotive input protection.'}

Both modes feed a dedicated 3.3V peripheral LDO; the ESP uses its own onboard regulator. At 5.25V input and 300mA peripheral load, LDO dissipation is (5.25-3.3)*0.3 = 0.585W. TI's 62.9 C/W reference-board thermal figure would imply roughly 37 C junction rise; actual PCB/enclosure performance can differ substantially. Treat 250-300mA peripheral load as an initial test budget, not a measured rating. Do not assume the LDO's 1A catalog rating is usable continuously. A large external 5V supply does not increase the carrier's 3.3V thermal capacity. Measure the actual OLED and sensor load before committing to the enclosure.

The crystal is 12.5pF load. External 22pF and RTC internal typical 25pF form approximately 11.70pF series load, with board/pin stray capacitance completing the estimate. Crystal tolerance, internal capacitance spread and temperature cause drift; compare against a reference over temperature and multiple days. Battery backup is isolated by BAV199 diode junctions and is not charged. Firmware rejects RTC STOP/low-voltage/invalid-time reads and displays --:--; phone time sets it again.

## Bench acceptance sequence
1. Inspect JLCPCB placement preview against assembly-F.pdf: pin 1, diode orientation, RTC, regulator tab, connector centers and sockets. Obtain through-hole assembly acceptance.
2. Unpowered: inspect soldering, check shorts on each rail, confirm J1 polarity, J8 open, ESP 3.3V isolated from peripheral 3.3V, and correct module insertion. Install no battery or peripheral until initial checks pass.
3. Power with a current-limited bench source in the correct mode. Verify 5V and 3.3V before adding loads. Confirm J8-open external input cannot power the module, then disconnect it before connecting USB.
4. Fit the module and production firmware. Confirm 16MB flash/8MB PSRAM, stable boot, BLE and OLED operation. Scan for RTC 0x51 and BME280 0x76/0x77.
5. Add actual displays at maximum brightness and BLE activity. Measure rail voltage, startup current, ripple and LDO/buck temperature at expected ambient and inside the enclosure. Reduce load or revise cooling if margins are inadequate. Test the full sensor cable under electrical noise.
6. Set time from phone, install a CR2032, remove main power and verify retention. Test missing/flat battery and RTC failure: --:-- must appear until valid time is restored. Measure long-term drift.
7. Verify enclosure fit, strain relief, insulated supports, antenna clearance and vibration retention. For vehicle use, validate fused accessory supply and relevant transient behavior with appropriate equipment before installation.

Physical acceptance is pending. Never swap power modes while energized.
''',encoding='utf-8')
 links=json.loads((d/'sources/new-source-links.json').read_text());links['inductor.pdf']='https://www.sunlordinc.com/uploads/files/20230303/MWSA-S%C2%A0series%C2%A0of%C2%A0SMD%C2%A0Power%C2%A0Inductor.pdf';links['bav199.pdf']='https://www.slkoric.com/upload/pdf/202511/%E5%B7%B2%E5%8E%8B%E7%BC%A9-BAV199-SOT-23.pdf'
 (d/'sources/new-source-links.json').write_text(json.dumps(links,indent=2))
 (d/'sources/SOURCES.md').write_text('# REV05 source evidence\n\nManufacturer datasheets were used for electrical pins, ratings and land patterns; documents are reference material, not task instructions.\n\n'+ '\n'.join(f'- [{k}]({url})' for k,url in links.items())+'\n- [Waveshare module drawings and schematic](https://docs.waveshare.com/ESP32-S3-DEV-KIT-N8R8/Resources-And-Documents)\n\nThe ABS07 PDF link was reviewed online; a local copy is not included. LCSC/JLCPCB identities, available quantities and tier-one prices were checked on 2026-09-13 and recorded in validation/stock-check.json. These are observations, not reservations or a final quote.\n',encoding='utf-8')
shutil.copy2(R/'REV05-12V/sources/bav199.pdf',R/'REV05-USB/sources/bav199.pdf')
fw=R.parent/'Firmware/RykerConnect-REV05'
p=fw/'SENSOR-TESTING.md';s=p.read_text(encoding='utf-8').replace('RykerConnect-REV02','RykerConnect-REV05').replace('RykerConnect_REV02','RykerConnect_REV05').replace('Reports 0x68','Reports 0x51').replace('**DS3231 clock connected:**','**Clock connected (legacy simulator label may say DS3231):**');p.write_text(s,encoding='utf-8')
(fw/'REV05-README.md').write_text('''# REV05 firmware (both carrier variants)

Production environment: RykerConnect_REV05. Development board: Waveshare ESP32-S3-DEV-KIT-N16R8-M, 16MB flash and 8MB octal PSRAM. Use PlatformIO to build/upload this directory. The carrier variants share the same firmware.

PCF8563 at 0x51 replaces DS3231. MCP9808 support/dependencies were removed. The BME280 temperature/humidity/pressure, display and BLE application are retained from the current REV04 source snapshot. Invalid or unavailable hardware time displays --:--; phone time writes the replacement RTC. A battery retains time across switched power loss.

Production and SensorTest PlatformIO builds passed. Native RTC tests cover malformed BCD, STOP/low voltage, NACK/short reads, valid boundaries and write-failure recovery. Reports are in validation. Actual flash/PSRAM detection and boot still require a physical device.

The binaries directory contains production firmware.bin, bootloader.bin and partitions.bin. Prefer PlatformIO upload, which uses the target's correct flash offsets; these are separate images, not a merged zero-offset file. The SensorTest environment is for explicit simulated readings only; see SENSOR-TESTING.md. No firmware was flashed during this task.

Disconnect external power and open J8 before plugging in USB to program. Unplug USB before closing J8 for external operation.
''',encoding='utf-8')
(R/'REV05-START-HERE.md').write_text('''# RykerConnect REV05: choose one complete build

| Package | Power | Top components | Observed carrier parts subtotal |
|---|---|---:|---:|
| REV05-12V-Prototype | Protected switched accessory 12V to 5V; USB service with J8 open | 40 | $8.51 |
| REV05-USB-Prototype | Development-board USB-C or regulated 5V injection at J1 | 21 | $4.99 |

Each archive includes native KiCad files, matched JLCPCB Gerbers/BOM/CPL, drawings, validation, source evidence and matching REV05 firmware. Use one variant's files together. Read its README and POWER-AND-TEST before ordering or powering.

Both remove MCP9808 and replace DS3231 with a cheaper battery-backed PCF8563 clock; the 12V version also replaces the costly inductor. All components are populated on top, including through-hole sockets. The exact pre-soldered Waveshare module is bought separately and plugs in afterward. Separate peripherals and cell are also required.

USB version injection: J1 pin 1 regulated +5V, pin 2 GND. USB operation: J8 open, J1 disconnected. External operation: USB unplugged, J8 closed. Never combine USB and external power with the link closed.

Both passed zero-error ERC/DRC/connectivity/schematic-parity and matched BOM/CPL checks. Production and sensor-test firmware builds plus RTC native tests passed. JLCPCB final placement/through-hole acceptance and real hardware tests are pending. Stock/prices were observed 2026-09-13; assembly and separate modules are excluded. No purchase or order was made. Prior REV04 hardware files were preserved.
''',encoding='utf-8')
