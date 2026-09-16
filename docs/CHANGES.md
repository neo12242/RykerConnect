# Changes in this fork

This publication combines two development tracks based on JanB97/RykerConnect, retaining the upstream history/license.

## Android and simulator

- Five-destination app navigation and customizable dashboard.
- Ride recording/recovery, summaries, maps, replay, charts, stops, trim/merge copies and GPX export.
- Garage fuel/service history, mileage, receipts, maintenance planning, intervals and reminders.
- Themes, day/night schedules, display profiles, widgets, backups and separate synthetic demo rides.
- Optional DadRides private-draft upload and reviewed publication workflow, built into the same APK.
- BLE/display simulator, browser OLEDs, sensor/failure scenarios and separate foldable guided demonstration.

## Hardware and firmware

- JLCPCB BOM/placement corrections and matched revision-specific exports.
- Protected accessory power, socketed N16R8 carrier and external BME280 connection.
- REV05 PCF8563 battery-backed clock, dedicated peripheral regulator and separate 12V/USB variants.
- REV05C cost reductions with retained protection and documented placement/cable requirements.

## Publication preparation

- DadRides website/API moved to its own repository; Android integration remains built in.
- Root and component setup/user guides, hardware warning and explicit historical/current version boundaries.
- Exclusion of private runtime data/backups/caches; personal domain hints replaced with `rides.example.com`.
- Local setup generates fresh DadRides development credentials instead of shipping the developer's key.
- Publication copies preserve the original development workspace.

This is a consolidation/documentation release, not a hardware redesign. See VALIDATION.md for what was rerun. Prior emulator checks do not establish current physical device or manufacturing acceptance.
