# REV04 single-sided ESP32 carrier

Status: the project owner approved this scope with "proceed" on 2026-09-13. Design, firmware build and prototype export completed. Vendor acceptance and physical qualification pending; no order placed.

## Problem
The JLCPCB export reports six unavailable component groups and omits ten REV03 protection groups. Two-sided assembly increases cost.

## Goals and success criteria
All carrier component bodies on the top side; replaceable N16R8 ESP32 development board; complete sourced BOM and matching placement file; passing KiCad electrical/layout checks and a built firmware configuration. Physical qualification and vendor acceptance remain required.

## Scope
Remove the carrier USB connector, USB support circuitry and programming buttons. Use sockets for a documented ESP32-S3 development board. Retain protected ignition-switched 12V input, OLED/light connections, battery-backed RTC, and remote BME280 temperature/humidity/pressure sensing over approximately 30cm of 3.3V I2C cable. Replace unavailable components with validated purchasing identities.

## Out of scope
Orders, payments, vehicle certification, custom media remote, and claiming physical testing without hardware.

## Constraints
Preserve mounting geometry where feasible; explicitly report any outline/enclosure change. Socketed board requires positive mechanical retention and antenna clearance. Header and USB power must not contend. JLCPCB assembles the carrier; the separate ESP board is plugged in afterward. Prefer stocked basic parts where electrical specifications permit. Through-hole assembly can still add process cost.

## Proposed approach
Protected accessory supply, regulated power, plug-in ESP32 and 3.3V peripherals. Programming uses the development board USB with supply isolation. All carrier SMT parts on top. Verify exact module dimensions before freezing sockets.

## Implementation plan
1. Resolve documented module, pin mapping, power topology and six source shortages.
2. Generate a separate REV04 schematic/layout and preserve REV03 unchanged.
3. Validate power calculations, native ERC/DRC, independent nets and mechanical clearances.
4. Build a separate N16R8 firmware target.
5. Export and reconcile complete Gerbers/BOM/CPL, review drawings and package with a stock/provenance report.

## Risks
More height, vibration retention, connector process fees, live stock changes, USB backfeeding, regulator thermal/current margins, and physical surge/EMI performance.

## Open questions
Selected Waveshare ESP32-S3-DEV-KIT-N16R8-M (28836), with documented geometry and pre-soldered headers. All 27 purchasing identities had stock at the recorded check. Actual module seating, enclosure fit, vendor placement/process acceptance and physical qualification remain open. No generic clone is interchangeable without pin/dimension checks.
