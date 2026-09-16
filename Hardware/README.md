# Hardware and JLCPCB files

> Physical hardware has not been tested. These are prototype designs with documented software/design checks, not proven vehicle hardware.

## Choose one revision

| Directory | Intended use | Matching firmware |
|---|---|---|
| `REV05C-12V` | Latest cost-reduced switched 12V accessory prototype | `Firmware/RykerConnect-REV05` |
| `REV05-USB` | USB or regulated external 5V; never vehicle 12V | `Firmware/RykerConnect-REV05` |
| `REV05-12V` | Earlier 12V REV05 before cost substitutions | `Firmware/RykerConnect-REV05` |
| `REV04-Carrier` | Earlier carrier with different RTC/components | `Firmware/RykerConnect-REV04` |
| `REV03-Protected`, `REV02-Corrected`, `KiCad/` | Historical sources/generation inputs | See revision-specific documentation |

Start with [REV05C-12V README](REV05C-12V/README.md) and [POWER-AND-TEST](REV05C-12V/POWER-AND-TEST.md), or the corresponding [USB README](REV05-USB/README.md). Older revision-specific recommendations are historical, not instructions to prefer them over REV05C.

## Upload a matched manufacturing set

Within your chosen current revision, `manufacturing/` contains:

1. `Gerbers.zip`: copper, mask, paste, silkscreen, board outline and drills.
2. `BOM.csv`: grouped component references and LCSC part identifiers.
3. `CPL.csv`: placement coordinates, top layer and rotation.

Upload those three files from the **same directory/revision**. The native board is 102 × 46 mm, two-layer FR-4, 1.6 mm, 1 oz copper. REV05C-12V has 40 populated components in 30 BOM groups; REV05-USB has 21 in 16 groups. All populated bodies are on top; sockets/connectors still need through-hole operations. Confirm the selected JLCPCB service accepts them and inspect every placement before ordering. Current stock, part substitutions, final assembly acceptance and pricing must be checked with JLCPCB at order time.

In particular, D5/D6's missing vendor preview models do not prove correct assembly orientation. Use the [placement and cable guide](REV05-Cost-Review/ORDER-2-PLACEMENT-AND-CABLES.md) and revision assembly drawings; obtain confirmation from the assembler when uncertain. No parts were silently substituted for publication.

## Separate purchases and power

The carrier sockets fit **Waveshare ESP32-S3-DEV-KIT-N16R8-M, SKU 28836**, with pre-soldered headers. Buy/install that module separately. Also needed: OLED assembly, Adafruit BME280 STEMMA QT breakout 2652, light sensor, CR2032 cell, J8 shunt, mating cables, supports and an enclosure. Board connectors do not include their mating wire plugs. Read each revision's pin table; wire color is not a pinout.

USB/programming mode: external input disconnected, J8 open. External operation: correct supply connected, USB unplugged, J8 closed. Disconnect all sources before changing modes. **Never connect USB and external power together with J8 closed.** REV05-USB's J1 takes regulated 5V, not 12V. There is no rechargeable-cell charger.

## Editing, regeneration and checks

Open `RykerConnect.kicad_pro` with KiCad 10. Local symbol/footprint libraries are included. For REV05C, `python Hardware/REV05C-12V/export_cost.py` rechecks ERC/DRC, schematic parity, critical nets and BOM/CPL, then regenerates outputs. The script currently expects KiCad at `C:\Program Files\KiCad\10.0`; adjust its CLI path for another installation. It also checks a historical stock snapshot, not live availability.

Run exporters only on a disposable copy or after saving your design: they overwrite generated files. Generation helpers retain Windows/KiCad 10 assumptions. Source-level regeneration and rerouting require renewed engineering/placement review; do not replace an approved manufacturing set merely because a regenerated file differs.

Firmware must be built for the matching revision. See [firmware instructions](../Firmware/README.md). Prior design validation reports and dated catalog observations are included as historical evidence. Publication checks are recorded in [validation notes](../docs/VALIDATION.md); real electrical, temperature, clock-drift, RF, enclosure, vibration and vehicle tests remain outstanding.
