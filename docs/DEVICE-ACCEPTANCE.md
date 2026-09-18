# Physical-device acceptance

Status: **pending — not performed by the engineering baseline**.

Run bench tests before road evaluation. Follow the selected board's POWER-AND-TEST instructions; software builds do not validate wiring or power behavior. Set up recording while parked. Have a passenger/operator handle observations where appropriate; do not interact with the app while riding.

Record app commit/version, firmware commit/environment, PCB revision, phone model/OS, permission and battery settings, test date, observed result and evidence for each case. Avoid publishing personal routes, addresses, photos or device identifiers. Mark each case pass/fail/blocked; record expected behavior before executing it. Do not convert an unexecuted case to pass.

| Case | Acceptance observation | Status |
|---|---|---|
| Board bench acceptance | Complete the selected revision's power, boot, clock, display, sensor and thermal procedure | Pending |
| Initial pairing and reconnect | Pair the intended unit; connect state and fresh data agree; failures provide a useful recovery action | Pending |
| Screen locked / app backgrounded | Record a controlled route with screen off; compare timestamps and route afterward; gaps are explicit | Pending |
| Power management | Exercise Android Doze/App Standby and the phone's battery restrictions; verify recording/recovery and document limits | Pending |
| Temporary BLE loss | Disconnect/reconnect within and beyond configured recording grace periods; no duplicated ride or invented points | Pending |
| Ignition cycle | Safely cycle accessory power; unit restarts and reconnects; ride boundaries and parking candidate are accurate | Pending |
| GPS disabled / unavailable | Missing data is explicit; no invented motion; valid recording resumes when GPS returns | Pending |
| Process interruption | Exercise OS process death separately from user force-stop; preserve saved data and label interruptions; document any manual restart requirement | Pending |
| Calls, music and navigation | Verify actual phone/intercom behavior, navigation/media updates and recording continuity | Pending |
| Permission denial/revocation | Deny relevant permissions individually; unaffected tools remain usable and recovery instructions are clear | Pending |
| Sensor absent/stale/recovered | Correct unavailable/stale states; fresh values appear only after a successful read | Pending |
| Samsung fold / rotation | Preserve selected ride and unsaved form state across supported screen/posture transitions | Pending |
| Backup on separate test installation | Export, inspect, restore onto a disposable installation; compare records/assets and duplicate handling without touching the original | Pending |

For each failure, retain sanitized evidence, reproduction steps, impact and a follow-up decision. Hardware and phone acceptance is separate from the four automated checks.

Android provides [Doze and App Standby test procedures](https://developer.android.com/training/monitoring-device-state/doze-standby#testing_doze_and_app_standby).
