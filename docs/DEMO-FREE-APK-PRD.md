# Demo-free APK update — 2026-09-23

## Problem
The distributed companion and phone APKs expose synthetic rides and development demonstrations. The project owner requested that demo features be turned off in both APKs.

## Goals / success criteria
Both downloads show only real local ride history. Demo rides, guided demonstrations, sample navigation, simulator preview and demo launch shortcuts are unavailable. Real recording, photos, maintenance, backup and publishing retain their current behavior.

## Scope
Use one build-time demo switch, disabled by default for both editions, including debug builds. Guard UI entry points and their underlying actions, including restored state and launch extras. Increment both editions to 1.4.1 / version code 11 and update their existing App Shelf entries.

## Out of scope
No deletion or migration of user rides, signing identity change, Play Protect bypass, permission changes, Cloudflare changes or firmware changes. Existing simulator tools and synthetic test fixtures remain available for deliberate development builds. Simulated external sensor data must still be identified honestly.

## Constraints
Retain current package IDs and signing certificate for updates from our previous APKs. The original upstream release has a different certificate and cannot be updated in place by our companion APK. Keep prior immutable App Shelf releases for rollback. No credentials are embedded in downloads or notes.

## Proposed approach
Add DEMO_FEATURES with an explicit development-only Gradle opt-in. Disable all packaged demo actions and entry points by default; do not depend on the debug flag. Preserve actual hardware diagnostics. Existing demo rides are computed fixtures rather than saved user records, so no data cleanup is required.

## Implementation plan
1. Gate demonstrations, saved demo selections, launch shortcuts and navigation samples.
2. Build both editions with demos explicitly disabled; run unit/lint checks and focused emulator tests for demo absence and real recording.
3. Verify APK identity, signatures and manifests; import both verified APKs into the existing local App Shelf through its operator importer.
4. Verify hashes, authenticated catalog/download behavior and retained prior releases. Record release notes and limitations.

## Risks / backout
Removing demo navigation must not suppress live Maps navigation or user history. UI regression tests cover the affected screens. App Shelf can select the preceding release without deleting either release. Android will generally reject installing an older version code over the new build; fix-forward is preferred for an installed APK. Build/emulator success does not prove physical-phone GPS or Play Protect acceptance.

## Open questions / authorization
The project owner explicitly requested this scoped update for both APKs. Play Protect acceptance remains a separate physical-device check; removing demo features is not claimed to fix its classification. No external appeal is submitted by this update.
