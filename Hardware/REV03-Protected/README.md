> Historical revision: see [the hardware index](../README.md) for current options. Physical testing is pending.

# RykerConnect REV03 — protected accessory-input prototype

**Use this revision for the new prototype.** It supersedes the REV02 manufacturing set for the ignition-switched 12V accessory installation. The source Downloads project and the previous REV02 package remain unchanged. This is a completed design/export package; no physical board has been assembled or tested.

## JLCPCB files

Upload these together, without mixing revisions:

1. `manufacturing/Gerbers.zip` — fabrication layers and plated/non-plated drills.
2. `manufacturing/BOM.csv` — **44 fitted components in 31 part groups**, all with LCSC catalog codes.
3. `manufacturing/CPL.csv` — **32 top-side and 12 bottom-side placements**.

Select an assembly service supporting both sides, the through-hole J1 and USB mechanical tabs. J1 is intentionally included in the CPL. Confirm stock, any preorder/Global Sourcing, and actual placement models before ordering. A catalog code is not a stock reservation or assembly acceptance. Use the Gerbers.zip above for fabrication, not the complete delivery archive.

The enclosure must clear the newly populated underside. Reserve roughly 3mm below the PCB for these components and verify the actual enclosure and manufacturer models before fabrication. The board outline and mounting-hole positions are preserved. This revision may cost more to assemble because it uses both sides.

## What changed

The accessory path is now:

**Fused, switched accessory 12V → J1 → transient suppressors/input capacitors → U6 electronic protection → original diode OR-ing → original 3.3V converter.**

- U6, TI **TPS26600PWPR**, adds reverse-input blocking, programmable overvoltage disconnect, startup slew control and approximately 1A overcurrent protection. MODE is open, selecting the auto-retry circuit-breaker behavior; SHDN uses its internal bias.
- R7/R8/R9 form a 120k/20k/10k divider: nominal **17.85V overvoltage cutoff**, **16.5V recovery**, and **5.95V undervoltage startup threshold**. Low voltage during engine cranking can shut the unit down and restart it; uninterrupted crank operation is not claimed.
- R10 is 12k for nominal 1A protection. C16 is 10nF for approximately a 1ms output ramp at 12V. The current limit is a protection threshold, not a new 1A external accessory output.
- D2 **SMBJ33A** and D4 **SMBJ18A** form an asymmetric TVS pair, connected anode-to-anode. The lower negative clamp accounts for the charged output capacitor and the chip's input-to-output voltage limit. Neither diode is the bidirectional `CA` version.
- C14/C17 provide 2uF nominal, 100V-rated input capacitance. C15 adds local output capacitance. D3 **B140-13-F**, cathode to protected output, limits negative output excursions.
- U6's RTN and exposed pad use an **isolated bottom copper island**. They are deliberately not tied to system GND. The custom footprint follows TI's PWP0016A land drawing, with a segmented exposed-pad paste pattern. There are no through-board thermal vias in this island; temperature at the real load must be checked.
- The protection circuit is on the bottom, preserving the original outline and front-side component locations. Two local ground returns were provided, and decorative bottom artwork was moved away from the new circuit. Original author attribution remains.
- The USB input remains behind its original diode OR-ing path. No firmware-controlled power sequencing or new Android feature was required.

## Installation and sensor

the project owner confirmed **ignition-switched accessory 12V** and approximately **one foot / 30cm** of sensor cable. Use a properly fused accessory branch and verify its connector polarity and wire rating. The electronic protection does not protect upstream wiring or a fault in the input TVS itself. Do not replace the vehicle's fuse with a larger value to address a fault. The unit normally powers off with the accessory supply; a connected USB supply can still power it.

Keep the external sensor as an assembled [Adafruit BME280 STEMMA QT module, product 2652](https://www.adafruit.com/product/2652), connected directly to J5 at 100kHz I2C. No extender is fitted for the initial one-foot run. Confirm reliable operation with the actual cable and vehicle electrical loads.

| J5 pin | Signal |
|---|---|
| 1 | GND |
| 2 | +3.3V |
| 3 | SDA, GPIO8 |
| 4 | SCL, GPIO18 |

Use a correctly wired JST SH/STEMMA QT cable. Power off before connection, and never put vehicle 12V or 5V on J5. Locate the sensor away from engine/electronics heat and sunlight, inside a ventilated splash-protected enclosure. Temperature, humidity and absolute pressure are measurements; they remain separate from GPS weather forecasts. A BMP280 is not a humidity-capable replacement.

The battery holder is assembled on the PCB; the CR2032 cell, OLED, external sensor module, cables, media remote and enclosure are separate items. Turnkey PCB assembly does not automatically include these items or firmware programming.

## Validation performed

- KiCad 10.0.6: **zero configured DRC violations, zero unconnected items, zero schematic parity issues, zero ERC violations**. This does not mean all optional KiCad rules were enabled.
- BOM/CPL sets match all 44 fitted components. The source netlist independently confirms that J1 feeds U6, the protected output feeds D1, the TVS diodes share only their anodes, and RTN is separate from GND.
- DC corner calculations include independent pin leakage and resistor tolerances. With 1% resistors the calculated cutoff is 17.20–18.75V. A wider 2.5% resistor-error envelope gives 16.73–19.28V, preserving margin above normal 16V operation and below the existing 25V capacitor rating. These are static calculations, not measured transient overshoot.
- Only new protection nets were imported from local Freerouting output. Existing signal and USB routing were not imported or optimized by that tool. The detailed native checks are the acceptance results; intermediate router logs are diagnostic.
- Original source hashes, board outline and retained component positions are checked. The preserved REV02 software is reused; it was previously built successfully with 32 Android unit tests passing. It was not reflashed or physically retested for this revision.

Reports, native placements, schematic/assembly PDFs, calculations and routing provenance are in `validation`. Purchasing identities and sources are in `selected-parts.json`.

## Prototype acceptance still required

This circuit targets normal 9–16V accessory operation, reverse connection at -16V, overvoltage disconnect for a **suppressed load dump up to 35V**, and short transients within the TVS ratings. It is **not qualified for a full unsuppressed alternator load dump**, and is not an ISO 7637/16750-certified automotive assembly. The protection chip is an industrial-rated part. Actual temperature, pulse energy, source impedance, DC-bias capacitance and PCB overshoot must be tested.

Before committing an assembly order, review:

1. JLCPCB stock/process acceptance and model alignment for every part, especially U6/D2/D3/D4 and the bottom-side rotation convention. U6 pin 1 must agree with the board marker. D2/D4 cathode polarity is essential.
2. J1/J2 mechanical fit, the exact SOG320132A OLED variant, and underside/enclosure clearance.
3. The actual accessory branch fuse and cable ratings.

Then test the assembled prototype on a protected, current-limited bench supply before connecting it to the vehicle:

1. Inspect soldering, isolated RTN, polarity and input/3.3V continuity.
2. Verify normal supply startup, soft start, overvoltage disconnect/recovery and undervoltage behavior. Apply controlled reverse-polarity tests with current limiting and no alternate grounded cables that could bypass the test path.
3. Verify overload/short-circuit auto-retry and temperature at the intended load. Recheck the regulator's local ground return.
4. Test the specified transient waveforms with suitable equipment; verify voltage at both U6 and the downstream converter, not just at J1.
5. Verify the OLED, USB, Bluetooth, RTC and environmental readings; confirm unavailable/stale readings and recovery. Finally test engine-start and vehicle electrical-noise behavior.

## Placement conventions and software

CPL coordinates share the KiCad auxiliary origin on both sides. Existing assembler adjustments are retained: J1 centroid offset (-1.5mm X, -2.53mm Y), U1 rotation +180 degrees and U2 rotation -90 degrees. New bottom parts use the native KiCad orientation as the model-preview starting point. These are not a substitute for JLCPCB's actual placement preview. DNP C1/C2/JP1 and off-board Q1 stay excluded; test pads, mounting holes and artwork stay excluded.

The included `software` files are the previously built REV02/N8 firmware and Android debug APK, reused unchanged because GPIOs, memory variant and sensor protocol are unchanged. Use the `RykerConnect_REV02` PlatformIO environment from the included firmware source for a complete first-board upload. `firmware.bin` alone is only the application image; do not flash it at address zero. The APK is not installed by this package. Earlier simulator-only trip/navigation/weather display extensions remain separate from this physical firmware.

## Rebuild

Keep this directory beside `REV02-Corrected`, which is the preserved input. Run `build_schematic.py` with regular Python, then `build_board.py`, `import_routing.py` and `finish_board.py` with KiCad's bundled Python; run `check_calculations.py` and `export_release.py` with regular Python. Run `finish_board.py` once per fresh board build. The saved `validation/protection.ses` supplies the reviewed routing, so reproducing the delivered design does not require downloading an autorouter.

For intentional rerouting, `prepare_routing.py` fixes existing routes in DSN and defines new net widths. Local Freerouting 2.4.1 was run with analytics, GUI, server, fanout and optimizer disabled. `import_routing.py` imports only the eight protection nets and rejects component movements by ignoring placement data. Reinspect and revalidate any rerouted result; the current finishing adjustments apply to the delivered geometry only.

References: [TI TPS2660 datasheet and reference circuit](https://www.ti.com/lit/ds/symlink/tps2660.pdf), [Littelfuse SMBJ datasheet](https://m.littelfuse.com/~/media/electronics/datasheets/tvs_diodes/littelfuse_tvs_diode_smbj_datasheet.pdf.pdf), [Freerouting CLI](https://github.com/freerouting/freerouting/blob/v2.4.1/docs/command_line_arguments.md). Per-part catalog links are recorded with the BOM identities.
