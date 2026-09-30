# Ownership dashboard and shared modifications

Status: The project owner approved implementation on 2026-09-29: “yes we are ready to build”. Testing is simulator/local website only. No phone install, production deployment, or publishing is authorized.

2026-09-29 scope confirmation: The project owner requested the same feature in Phone Edition so Phone and ESP stay matching. Reuse the same ownership overview and editors in both edition shells; share the My Garage layout rather than maintain two copies. Build both debug variants and verify Phone Edition navigation in the simulator. Existing simulator data must survive installation; physical-phone installation remains out of scope.

## 1. Problem
Recorded ownership costs are scattered. Modification costs have no shared app/site record. Article/photo editing should remain in DadRides with a direct signed-in link from the app.

## 2. Goals / success criteria
My Garage summarizes fuel, maintenance and modifications by calendar year, all time or a named season. Totals drill into source records and export as CSV/PDF. Estimated costs remain separate. Cost per mile requires confirmed mileage for the same period. Modifications can be created/edited from either app or authenticated site with visible sync/conflict status. A short-lived one-use browser handoff opens the matching website editor without exposing the owner key. Public articles change only through explicit publication.

## 3. Scope
Reuse existing garage entries, reminders/planner, backup and shared-library paths. Add private modifications with planned/purchased/installed status, optional estimate, dated itemized expenses, installation date/mileage and private notes. One mod can have several purchases. Link by stable ID to existing website articles. Add private website ownership editor; article, photo and publication controls stay on the website. Add named date ranges and optional confirmed boundary odometers. Preserve existing records and classifications; no automatic reclassification or invented costs.

## 4. Out of scope
Post-ride journal (#14), insurance/registration/financing/purchase price, public cost disclosure, receipt-image synchronization, hardware/firmware, live deployments and phone installations. Existing receipt photos remain intact. This first version syncs modification metadata/expenses; fuel records and season configuration stay in the existing local/shared-app library.

## 5. Constraints
USD and existing distance unit setting. Unknown price is distinct from zero. Purchases count once on purchase date regardless of installation. Estimates/checklists are not spending. Do not divide by GPS trip distance. Date-period boundaries must align with explicitly confirmed odometers; otherwise cost per distance is unavailable. Existing records win restore ID conflicts. Keep private expenses separate from published mod documents. No permanent credentials in URLs/logs or public exports.

## 6. Proposed approach
Android ownership calculations and Compose screens reuse SoftwareStore. New modifications and seasons participate in backup validation, additive restore and edition sharing. Website private records use revision-checked writes; local pending work survives offline use and stale writes become explicit conflicts retaining both versions. Archive instead of destructive deletion. Browser handoff uses a one-use expiring code exchanged for a secure HttpOnly session; cookies require same-origin protection and logout. Website publication schema stays unchanged.

## 7. Implementation plan
Snapshot changed source and emulator state. Implement models/calculations and tests; storage/export/UI; private site API and editor; modification synchronization and browser handoff. Validate backup compatibility, concurrent edits, retries, private/public boundaries and handoff expiry/replay. Build debug APK only, run local API/browser tests and emulator checks using The project owner's restored data without adding synthetic records to his history. Keep simulator disconnected from production.

## 8. Risks / backout
Incomplete mileage can mislead: withhold ratios without confirmed boundaries. Duplicate expenses: stable IDs and explicit categories. Concurrent edits: reject stale writes and preserve local choice. Auth change: narrow handoff, expire sessions, CSRF checks. Checkpoint simulator APK/data and touched source; local database uses additive tables in isolated test storage. No production schema changes. Restore checkpoint only after accounting for subsequent user changes.

## 9. Decisions / open questions
Confirmed fuel/maintenance/modifications, named seasons plus year/all-time, website-only editorial work, authenticated editor shortcut. Current phase is local review. Deployment, real-device acceptance and any expansion of expense categories require later approval.
