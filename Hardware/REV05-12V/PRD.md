# REV05 dual carrier update

Approved by the project owner: "proceed with both" on 2026-09-13.

## Problem
REV04 retains an unused local temperature sensor and costly RTC/inductor. Two power options are required.
## Goals
Deliver separate protected 12V and USB/external regulated 5V KiCad and JLCPCB packages. Retain socketed Waveshare 28836, OLEDs, auto brightness, battery-backed time and remote BME280. Top-side population only.
## Scope
Remove U2/C4 MCP9808 circuit. Replace DS3231 with PCF8563, crystal and noncharging coin-cell backup. Qualify cheaper L1 for the 12V build. Give peripherals their own 3.3V regulator, separate from ESP 3.3V. Update RTC firmware and export matched BOM/CPL/Gerbers.
## Out of scope
Ordering, payment, Android feature changes, battery charging, USB PD and automotive certification.
## Constraints
Retain 102x46mm outline, original mounting holes, module sockets/retention and RF clearance. USB is 5V only. Source selection must prevent intentional simultaneous power-source connection; the ESP VBUS header shares USB power and cannot be isolated by carrier circuitry alone.
## Approach
REV05-12V retains protected accessory input, 5V buck and removable isolation link. REV05-USB removes the vehicle input circuit and adds a polarized 5V/GND injection connector and removable external-source isolation link. USB cable must be unplugged in external-power mode; for USB programming disconnect external power and open the link. Both feed a dedicated carrier 3.3V peripheral regulator.
## Implementation
Verify sourcing and datasheets; derive variant designs without changing REV04; route and inspect; adapt firmware; run native ERC/DRC/parity, net assertions and firmware checks; export separate archives and test instructions.
## Risks
RTC drift and crystal load tolerance require prototype measurement. Power and thermal calculations are design estimates pending load tests. Vendor stock, placement-model alignment and through-hole service acceptance are not guaranteed by CAD checks.
## Open questions
No preference decisions outstanding. Exact components and prototype operating limits are engineering selections recorded in each release.
