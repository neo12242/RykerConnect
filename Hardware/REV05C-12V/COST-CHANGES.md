# REV05C-12V: cost reduction with retained functions

This release implements six Basic-library substitutions. The PCB retains every component position and every electrical net of REV05-12V. All 40 components remain top-side; there are still 30 BOM groups. Clock with CR2032 backup, BME280, OLEDs, light sensing, protected switched 12V input, USB service and socketed ESP remain present. Firmware is unchanged.

## Changes
| Positions | Previous code | New Basic code | Conservative quote quantity | Estimated line cost |
|---|---|---|---:|---:|
| R7 | C114607 | [C25808](https://jlcpcb.com/partdetail/C25808) | 20 | $0.0420 |
| R8 | C105575 | [C4184](https://jlcpcb.com/partdetail/C4184) | 20 | $0.0540 |
| R10 | C114659 | [C22790](https://jlcpcb.com/partdetail/C22790) | 20 | $0.0660 |
| C3/C6/C10/C11 | C1591 | [C14663](https://jlcpcb.com/partdetail/C14663) | 30 | $0.3720 |
| C16 | C1589 | [C57112](https://jlcpcb.com/partdetail/C57112) | 20 | $0.2160 |
| X1 | C130253 | [C32346](https://jlcpcb.com/partdetail/C32346) | 5 | $0.8595 |

Resistors keep their 0603 size, resistance, 1% tolerance, 100mW rating, 75V rating and 100ppm/C temperature coefficient. Capacitors retain capacitance, 50V rating, X7R dielectric, 10% tolerance and 0603 size. The Epson Q13FC13500004 crystal retains 32.768kHz, 12.5pF load, nominal 20ppm tolerance and -40 to +85C operation. The existing pad dimensions match Epson's 1.0 x 1.8mm pads at 2.5mm center spacing. Ground pours are excluded beneath the crystal on both layers and the GPIO7 trace is rerouted outside that area. A separate copper-intersection check confirms only the crystal's own terminal traces intersect the body area. See sources/FC-135.pdf. Physical oscillator startup and drift tests remain required, as before.

## Five-board quote estimate
| Charge | Previous quote | Projected |
|---|---:|---:|
| Components | $44.89 | $41.96 |
| Extended loading fees | $58.33 | $39.91 |
| PCB and all other quoted charges | $23.43 | $23.43 |
| Total before shipping/tax | $126.65 | **$105.30** |

Projected saving: **$21.36**. This is an estimate, not a refreshed JLCPCB cart quote. It assumes each of the six replaced Extended SMT types removes a $3.07 loading charge; actual attrition, fee exemptions and rounding can change the result. Conservative component quantities retain the prior order quantities (including extras) for these positions. A fresh quote may require fewer Basic spares. Stock and prices were checked on 2026-09-13. None are reserved. Shipping, tax and separately purchased development board/peripherals are excluded.

## Alternatives not selected
SS14/SS34 and alternate TVS diodes are not fitted in this release: their different surge, forward-drop, temperature or leakage specifications require additional qualification. Third-party Preferred flags were not treated as proof of a waived checkout fee. The cheaper identical tape-and-reel battery holder C20607187 had only three orderable units, insufficient for five boards. The BAV199 parts already share one BOM type; reducing their count would not remove its loading fee, and their series pin arrangement cannot directly provide a two-source common-cathode circuit in one package. No protective function or battery-backed time was removed to force the price below $100.

## Ordering
Upload this release's manufacturing/Gerbers.zip, BOM.csv and CPL.csv together. The PCB changed for crystal clearance, so do not combine this BOM with the earlier Gerbers. Inspect the vendor placement preview and confirm through-hole assembly before paying. No order was placed. This revision gets closer to $100; it does not claim a confirmed sub-$100 order.

## Validation
KiCad ERC, all enabled DRC rules, connectivity and schematic parity: zero issues. Independent critical-net assertions passed. Full electrical net membership is identical to REV05-12V and CPL coordinates are byte-identical. Matching REV05 production firmware and its prior successful build/test evidence are included; no firmware changes required another build. Original REV05 archives remain unchanged. Real prototype, thermal, oscillator and vehicle validation remain pending.
