# Watch ride controls and recording pauses

Approved scope: implement watch ride controls and provide separately reviewed application downloads.

## 1. Problem
Starting and ending a ride should be possible from a Galaxy Watch Ultra2. A long stop should not record walking around a mall as part of the ride or require a second trip.

## 2. Goals and success criteria
Start, Pause, Resume and End Ride work in both Android editions and on the watch. Pause retains one stop and its duration, stops recorder GPS requests, and resumes the same ride in a new route segment. End saves locally; uploading and publishing remain separate. The watch reports confirmed phone state and never claims an unacknowledged action succeeded.

## 3. Scope
Wear OS remote, Phone and ESP app controls, recording notification, durable pause metadata, separate paused time, shared-library/backup preservation, app stop display, and reviewed DadRides stops subject to existing route privacy. Updated APKs and setup instructions are distributed through App Shelf behind its existing Cloudflare protection.

## 4. Out of scope
Standalone watch GPS, automatic pause, automatic upload/publication, new R2 use, and changes to the pending Alaska Geek publisher credentials.

## 5. Constraints
Preserve existing app identities, signing keys and user data. Keep Phone edition free of ESP and notification-reading features. Android background location and foreground-service rules apply. Physical Galaxy Watch validation requires The project owner's device; emulator evidence must be identified as such.

## 6. Proposed approach
The phone remains the recorder and storage authority. Use Google's Wear Data Layer with matching package/signature watch editions. Signed local IPC routes controls to the phone edition owning the active recording. Commands carry identifiers and an expected state token; acknowledge only after persistence. A paused session retains its recording lease and cannot auto-finish or auto-resume on an ESP disconnect/reconnect. Recovery after process death requires an explicit Resume or End and keeps interrupted time distinct from deliberate pauses.

## 7. Implementation plan
1. Verify background-start requirements and provide explicit optional watch setup.
2. Add persistent pause/resume, statistics and recording-service controls with state-machine and persistence tests.
3. Add phone, notification and round-screen watch controls, confirmed status and connection/error handling.
4. Preserve stop metadata through editing, sharing, backups and privacy-reviewed publication.
5. Validate builds, automated tests and available emulators; preserve current production releases and deploy compatible website/API changes before new APKs.
6. Publish Phone, ESP and matching watch downloads with setup instructions and rollback records.

## 8. Risks and backout
Android or Samsung power restrictions may reject a background start: show an actionable error instead of silently recording or claiming success. Duplicate or delayed commands must not affect a different ride. Missing/stale GPS must not invent a stop location. A storage failure must not acknowledge a successful action. Keep previous App Shelf releases and an online SQLite backup; record the prior DadRides deployment for rollback. Existing recordings remain in place; do not uninstall phone apps during an update.

## 9. Open questions resolved / validation remaining
“Send” means End Ride, not upload. The watch runs as a remote for the phone. The recommended initial watch download matches RykerConnect Phone; an ESP-matching watch build is supplied for users running that edition. Physical watch pairing, locked-phone behavior and battery behavior remain device acceptance checks after emulator validation.

## 1.7.1 defect correction — 2026-09-26

The project owner confirmed that Start Ride on the installed watch repeatedly becomes disabled and
enabled while the app remains open. The existing five-second STATUS poll sets the
same pending flag as a command. A new emulator regression test reproduces the defect.

This correction stays within the approved watch controls and App Shelf release scope:
retain fresh acknowledged controls during STATUS requests, allow an explicit command
to supersede a status poll, ignore superseded discovery/reply/failure callbacks, and
keep command acknowledgement, stale-status and disconnect protections. Give discovery
the same bounded timeout as message delivery. Publish both watch editions as 1.7.1
with the existing package/signing identities and compatibility with phone apps 1.7.0.
No phone recorder, ride data, website or wire protocol changes. Validate the reported
UI behavior and command/poll races on the Wear OS emulator, build/lint both watch
flavors, verify signing identities, back up the catalog and publish retained releases.
Physical watch acceptance remains The project owner's check after installing the watch update.
