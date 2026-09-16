# REV05C-12V placement and cable guide

## D5 and D6 orientation

Current part: Slkor BAV199, JLCPCB C3019921, SOT-23, top marking JY. The manufacturer's diagram and the actual KiCad pad/net assignments agree. A missing JLCPCB preview model does not establish either correct or incorrect vendor placement. Vendor-side orientation remains unconfirmed.

View the TOP of the carrier with the board title upright, USB end left and antenna end right. BOTH D5 and D6 have this orientation:

```text
         two-lead side        one-lead side

  pin 1 [ supply ]  ┌─────┐
                   │body │  [ pin 3: RTC_VDD ]
  pin 2 [ unused ] └─────┘
```

Pin 1 is upper-left, pin 2 lower-left, pin 3 at the right. The used diode conducts from pin 1 toward pin 3. D5 pin 1 receives peripheral 3.3V; D6 pin 1 receives battery positive. Pin 3 of both connects to RTC_VDD. The second internal diode ends at unused pin 2.

| Reference | Native board center X/Y, mm | CPL center X/Y, mm | CPL layer / rotation |
|---|---|---|---|
| D5 | 175 / 79 | 85.5439 / 37.7794 | Top / 0 degrees |
| D6 | 179 / 93 | 89.5439 / 23.7794 | Top / 0 degrees |

These angles use the exported KiCad footprint convention. JLCPCB's model convention can differ: verify the physical pin mapping, not the number zero alone. Do not rotate a part solely because its 3D body fails to render.

Suggested note to JLCPCB (not sent):

> D5 and D6, C3019921 Slkor BAV199 SOT-23, do not render in the placement preview. Please confirm placement using the datasheet and attached assembly drawing. Viewed from the top with the carrier title upright, both parts have pin 1 upper-left, pin 2 lower-left and pin 3 right. D5 pin 1 is +3.3V; D6 pin 1 is VBAT; both pin 3 pads connect to RTC_VDD; pin 2 is unused. Please provide a visible 2D pin/model confirmation before assembly.

Existing drawing: ../REV05C-12V/validation/assembly-F.pdf

Manufacturer source: https://www.slkoric.com/upload/pdf/202511/%E5%B7%B2%E5%8E%8B%E7%BC%A9-BAV199-SOT-23.pdf

## Replacement findings



Sources:
- https://jlcpcb.com/partdetail/C40919
- https://jlcpcb.com/partdetail/C549304
- https://jlcpcb.com/partdetail/70066-BAS116/C68953
- https://assets.nexperia.com/documents/data-sheet/BAV199.pdf

Remaining Extended groups reviewed:

| Group | Disposition |
|---|---|
| BT1 battery holder | Retain. Previously found cheaper identical tape-and-reel variant had insufficient five-board stock; a generic holder needs mechanical qualification. |
| C14/C17, 1uF 100V | Retain voltage rating. No verified Basic equivalent found in this review. |
| D2/D4 TVS protection | Retain. Alternate nominal part names alone do not establish equivalent surge behavior or a waived fee. |
| D3 B140 | Basic SS14/SS34 candidates exist, but different surge/forward-drop/temperature ratings need qualification. Not substituted. |
| D5/D6 | Retain low-leakage BAV199; vendor orientation confirmation needed. |
| J1/J3/J4/J5/J6/J7/J8 | Retain mating compatibility, socket height and turnkey assembly. Generic connectors require fit/contact/retention verification. |
| U3 RTC | Retain firmware-compatible PCF8563 and battery-backed time. |
| U5 buck, U6 input protection | Retain fixed 5V operation and vehicle-input protection. |
| U7 peripheral LDO | Retain ceramic-capacitor-compatible regulator. A generic 1117 is not automatically equivalent. |


## Separate mating plugs and cables

The JLCPCB BOM purchases board-mounted connectors only. It contains no mating wire housings, crimp contacts or harnesses. For each assembled carrier:

| Position | Separate wire-side item | Notes |
|---|---|---|
| J1, 12V accessory | One compatible Molex Micro-Fit 3.0, single-row 2-circuit female cable assembly/pigtail | 43645-series housing family; 436450200 is the 2-circuit housing candidate. Specify compatibility with header 436500200 and contacts matched to wire gauge. Pin 1 positive, pin 2 ground. Housing alone has no wires/contacts. |
| J3, OLED | One JST SH 1.0mm 8-pin female pigtail | SHR-08V-S or handled SHR-08V-S-B housing with contacts; display-end termination depends on the actual OLED assembly. |
| J4, light sensor | One JST SH 1.0mm 2-pin female pigtail | SHR-02V-S / SHR-02V-S-B. Sensor-end termination depends on the selected sensor. |
| J5, BME280 | One JST SH 1.0mm 4-pin STEMMA QT/Qwiic cable, approximately 30cm | Must be correctly wired pin-to-pin: 1 GND, 2 3.3V, 3 SDA, 4 SCL. SHR-04V-S / SHR-04V-S-B family. |
| J8 | One 2.54mm jumper shunt | Header is assembled; removable shunt is separate. Open for USB, unplug USB before external-power operation with shunt closed. |
| J6/J7 | Matching pre-headered Waveshare development board | No wire harness between the module and carrier sockets. |

Choose pre-crimped pigtails to avoid buying a precision JST SH crimp tool. Extra crimping/cable work and separately purchased modules/cell are outside the PCBA quote. A harness supplier can assemble custom cables after endpoints and lengths are specified; no such service has been quoted or ordered here.

Primary connector references:
- Molex board header: https://www.molex.com/en-us/products/part-detail/436500200
- Molex 2-circuit housing: https://www.molex.com/en-us/products/part-detail/436450200
- JST SH housing/contact table: https://www.jst-mfg.com/product/pdf/eng/eSH.pdf
- JST SH product family: https://www.jst.com/products/crimp-style-connectors-wire-to-board-type/sh-connector

JST SH and similarly named JST families are not interchangeable. The JST SH contact family is SSH-003T-P0.2-H; select wire gauge and assembly according to the manufacturer's specification. Verify continuity and polarity before connection; cable colors alone are not proof of pin order.
