# REV05-USB power and prototype acceptance

## Power selection
J1 pin 1 is **regulated 5.0V external positive**; pin 2 is GND. J8 is the external-source isolation link.

| Mode | J1 | J8 | Development-board USB |
|---|---|---|---|
| USB power/programming | Disconnected | Open (shunt removed) | Connected |
| External operation | Correct external supply | Closed | Unplugged |

Disconnect both sources before changing modes. The module VBUS header shares its USB power path. **Never connect USB and external power together with J8 closed.** The carrier cannot isolate that shared path. A data connection still counts as USB unless its VBUS conductor is physically disconnected and verified.

## Design limits and calculations
J1 requires regulated 5.0V +/-5%, correct polarity. Start with a supply rated at least 1A; 2A gives useful headroom. A higher current rating does not force that current into the load. Do not use a 12V source or USB-PD voltage trigger. For a vehicle, the external supply must itself provide suitable automotive input protection.

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
