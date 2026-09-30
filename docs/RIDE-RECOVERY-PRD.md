# One ride and website recovery

Status: approved by The project owner with “proceed” in this task on 2026-09-24. Implementation and local validation authorized. The project owner subsequently approved deployment; 1.5.2 was deployed and verified on 2026-09-24.

## 1. Problem
Owner lists every stored revision as a separate ride. Website edits only overlay existing Android recordings, so deleting a trip leaves no way to restore the website copy into My Trips.

## 2. Goals and success criteria
One Owner card per ride ID; prior versions remain accessible. Existing trips receive website edits when either app opens/reconnects, with manual sync and visible conflicts. An explicit restore adds the latest available website copy once under its existing ID and shares it between Phone and ESP. Metadata and route operations perform zero R2 calls.

## 3. Scope
Owner list and version history; authenticated restore selection and confirmation in both apps; durable, labeled website-only trip copies; foreground/reconnect sync; conflict, deletion, retry, backup and peer-sharing verification. Include the already approved, staged map filters and trip deletion in the next update.

## 4. Out of scope
Recovering removed raw GPS, timestamps, hidden endpoints or original photos from public data. No automatic resurrection of deliberately deleted trips, automatic remote deletion, or photo downloads during recovery. Service-baseline and simulator proposals remain separately pending approval.

## 5. Constraints
Keep stable APK identities/signatures, existing data and website revision history. Use existing D1 metadata APIs and local library replication. A website copy must not masquerade as an original recording or certify route privacy using incomplete endpoints.

## 6. Proposed approach
Select the editorial head for the Owner card, with published/latest-upload fallback and an explicit version list. Restore only after confirmation, using a persistent website-copy marker and retained metadata under the same ride ID. Keep raw tracks empty and unavailable analytics clearly labeled. Preserve existing original trips; concurrent changes remain reviewable.

## 7. Implementation plan
Update Owner queries/history UI and regression tests. Add a paged metadata-only restore catalog, durable local restore and UI. Connect on-resume/network retry and propagate newly received edits to the peer. Validate deleted-trip restoration, repeated restores, future edits, offline/restarts, backups, both APKs and zero R2 operations. Build signed update artifacts and a deployment/backout note.

## 8. Risks and rollback
Incomplete website copies, stale revisions and concurrent peer changes require explicit labels/review. Never replace an existing original with a website copy. Keep local causal history and original server revisions. Retain previous website assets and APK releases; recover Android problems with a forward update rather than uninstalling and losing data.

## 9. Decisions and open questions
The project owner approved the recovery amendment. Website metadata/route restoration excludes photo binaries. Recovery is explicit; ordinary website edits synchronize automatically when Android permits the app to run. Physical phone behavior must be distinguished from emulator evidence.
