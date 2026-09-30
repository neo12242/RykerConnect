# Shared Ride Library

Status: approved by The project owner in this task on 2026-09-24. Implementation and local validation authorized; production rollout is reviewed after validation.

## 1. Problem
DadRides supports upload/publication but no owner editing or reverse synchronization. Phone and ESP editions have different package identities and private data directories.

## 2. Goals and acceptance criteria
- Continuously exchange saved user data between both editions on the same Android profile, including completed original tracks, photos, journals, fuel/service records, vehicle information, plans, favorites and common preferences.
- Edit website title, date, story, tags, captions, cover, route and statistics; display the edits in both apps while retaining original GPS and calculated measurements.
- Work offline, retry safely, identify competing changes and never silently discard them.
- Metadata synchronization and local app-to-app transfers must make zero R2 operations. Ordinary website photo viewing/uploads still use R2. D1 and Workers retain their normal usage accounting.
- Keep existing APK identities/signing identity and update in place without uninstalling.

## 3. Scope
Owner editor with save draft/update published actions, route point correction/trim/section removal/undo, validated distance/time overrides and derived average; incremental metadata sync, Sync now/status/conflict resolution; protected local library replication and one recorder at a time.

## 4. Out of scope
Different-phone sync; copying Android permission grants/Bluetooth pairings; publishing private garage records or raw untrimmed GPS. OS/SDK caches are not personal library records.

## 5. Constraints
Use existing D1/Worker and R2 media. Android integration remains in RykerConnect; DadRides website/API remains separate. Owner keys never enter URLs, logs, backups or public files. Both apps keep complete local copies, so initial replication needs space for verified files and recovery copies.

## 6. Approach
Use a same-signature, explicit-peer ContentProvider connection with immutable streamed file blobs and versioned records. Compare causal versions; retain conflicts until resolved. Exchange saved records on change/foreground/manual sync. Keep an independent durable copy in each app, with additive migration and recovery data.

Keep website edit revisions in D1, referencing existing media rather than copying it. Use compare-and-swap revisions and an indexed incremental change cursor. Preserve source tracks; represent edited route/statistics separately. Public route corrections cannot automatically restore hidden endpoints. When corrections could enter a hidden area, require the app with the original track to validate endpoint trimming before publication. Website drafts remain usable while this check is pending.

## 7. Implementation plan
1. Record approved scope and inventory existing persistence. Preserve originals/backups.
2. Add and test D1-only editor/sync operations and media reference safety.
3. Add local replication, migration, conflict UI, recording coordination and app metadata sync.
4. Add owner editor and connect both app UIs to effective edited data with original access.
5. Test old-data migration, real installed APK interoperability, interrupted transfers, offline/concurrent edits, privacy and zero R2 calls. Run Android checks and web tests.
6. Prepare signed update APKs and additive production migration/deployment with concrete validation and rollback evidence for review.

## 8. Risks and rollback
Conflicting edits, partial transfers, insufficient disk and duplicate recordings are the main risks. Verify file hashes before committing imported records; preserve causal conflicts and source data; coordinate recording with one authority. Persist migrations/backups before writes. Sync can be disabled without removing rides. Server migration is additive and prior site assets/deployment are retained; do not downgrade an APK with new data without a tested forward-compatible recovery build.

## 9. Decisions and open questions
The project owner selected continuous sharing and route/statistics editing. Assume same phone/profile. Common data is shared; package-specific runtime state stays local. Production deployment remains pending implementation evidence. No physical ESP hardware validation is claimed.
