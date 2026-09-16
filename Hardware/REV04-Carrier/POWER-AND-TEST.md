# REV04 power review and prototype acceptance

## Design limits

The fixed 5V [AP63205](https://www.diodes.com/assets/Datasheets/AP63200-AP63201-AP63203-AP63205.pdf) uses the manufacturer's 4.7uH, 10uF input, two 22uF output and 100nF bootstrap arrangement. A separate 100nF input bypass sits beside the regulator return. The retained Wurth 744316470 has sufficient current rating and low DCR for this application. Ceramic nominal capacitance is not effective capacitance under DC bias; verify startup and load-step waveforms on the assembled board.

The retained [TPS2660](https://www.ti.com/lit/ds/symlink/tps2660.pdf) divider corner calculation includes threshold, resistor and leakage variation. `validation/protection-calculations.json` is a static calculation, not a transient simulation or qualification. The normal operating target remains 9-16V; cranking below that may reboot the display. The buck's 2A component rating is not a 2A system output guarantee: the input eFuse, module LDO, copper and thermal conditions impose lower limits.

The eFuse sees C9 + C10 + C15, 11.1uF nominal. A conservative 20% high-capacitance estimate gives about 0.208A capacitive inrush at the calculated maximum slew. The buck's 44uF output bank and module capacitance charge through its own soft start and are not simply added as 12V input capacitance. Using the datasheet's typical 4ms soft start, 54uF nominal output-side capacitance, 0.5A 5V load and deliberately conservative 70% conversion efficiency gives about 0.67A combined input current at 9V including the high-side capacitive estimate. This illustrative estimate is below the approximately 0.91A conservative low current-limit estimate; it is not a guaranteed startup corner, because soft-start tolerance and real load transients are unmeasured.

The module's linear regulator dissipates approximately (5-3.3) times its 3.3V load current: 0.51W at 300mA, 0.85W at 500mA. The carrier and module thermals therefore need measurement in the enclosure with Bluetooth activity and both displays at maximum brightness. Start with the real peripheral load, not an assumption that the module can continuously power arbitrary added devices. U6's exposed pad has local RTN copper and no thermal through-vias; its temperature and fault cycling must also be checked.

The TVS/eFuse protection target includes controlled reverse connection to -16V and overvoltage disconnect for a suppressed 35V load dump. Full unsuppressed load dump, ISO 7637/16750 certification and immunity to every vehicle transient are not claimed. Actual pulse energy, source impedance, temperature and wiring inductance control the result. The eFuse does not protect an upstream wire short or an input TVS failure; retain the vehicle branch fuse.

## Bench sequence

1. Inspect the assembled carrier before fitting the ESP module. Confirm U3/U5/U6 pin 1, D2/D3/D4 cathodes and J1 polarity. Check for solder bridges, including the U6 exposed pad. Confirm RTN is not shorted to GND.
2. With J8 open and the module absent, use a current-limited 12V bench supply. Measure the protected rail and the 5V side of J8; inspect startup and ripple. Verify J8's module side remains disconnected. Do not connect a USB cable during this test.
3. Remove power, fit the specified module, supports and J8 shunt. Reapply 12V and measure 5V and 3.3V under the real maximum display/radio/sensor load. Test power cycling at 9, 12 and 16V; check for current-limit cycling and brownouts.
4. Disconnect accessory power, remove J8, then use the module USB connector for programming. Confirm firmware boots, identifies the expected 16MB flash/8MB PSRAM, and both displays reset and render correctly. Never reinstall J8 while USB is connected.
5. Check RTC time setting and persistence after removing accessory power with a CR2032 fitted. The cell is not charged. Check BME280 T/RH/P, local MCP9808 and light input. Disconnect/reconnect the remote sensor and verify unavailable/stale states and recovery without inventing readings.
6. Scope SDA/SCL at both ends of the actual 30cm cable. Confirm rise time, low voltage and reliable 100kHz operation during radio activity and vehicle load switching. Verify correct parallel pullup loading before adding an extender.
7. Measure regulator/module temperatures in the enclosure. Verify positive retention, connector strain relief, access to J8 and USB, and antenna clearance/range. Do not use the sockets alone as vibration retention.
8. Test overvoltage cutoff/recovery, controlled reverse polarity and overload on suitable current-limited equipment. Avoid alternate grounded cables bypassing the reverse-polarity path. Transient tests require suitable pulse equipment and probes at U6 input/output and U5 input. Do not experiment with uncontrolled surges on the vehicle.

## Mechanical coordinates

Carrier upper-left: (89.4561,70.7794)mm in KiCad; lower-right: (191.4561,116.7794)mm. Auxiliary/drill/CPL origin is the lower-left (89.4561,116.7794)mm. CPL Y is positive upward.

Original H1/H2 centers: (98.4561,102.2794) and (188.4561,102.2794)mm. New module retention centers: (127.8,75.8), (187.5,75.8), (127.8,97.6), (187.5,97.6)mm. Hole positions follow the downloaded Waveshare drawing; actual seating and screw/support fit remain to be checked against the purchased board.

The two 22-pin sockets have centers (157.72,75.27) and (157.72,98.13)mm. Pin 1 is at X=184.39mm. Antenna copper keepout is at X>=182mm, Y=77.2-96.2mm, covering the full antenna projection plus margin. The module body drawing is on User.Drawings, not in the fabrication outline. Bottom-side through-hole lead clearance remains necessary despite having no bottom-side components.

