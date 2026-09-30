# Map date filters and trip deletion

Approved by The project owner with “proceed” on 2026-09-24.

1. **Problem:** The public map cannot filter routes by month/year. False-start trips cannot be removed from the apps.
2. **Success criteria:** Map filters update routes and bounds, including empty results. Deleting a saved trip in either edition removes it from the paired edition after sync.
3. **Scope:** Map-only Year/Month controls with All options; confirmed deletion in Phone/ESP trip details; cancellation of the deleted trip's queued uploads.
4. **Out of scope:** Automatic short-trip deletion, journal-card filter changes, and deleting uploaded DadRides versions.
5. **Constraints:** Preserve active recordings and unrelated data. Date filtering uses loaded published manifests. Local deletion and paired sync use no R2 API.
6. **Approach:** Calendar-date filtering plus existing map source updates. Persist per-record deletion intents in the existing shared library before removing local trip files, journal, favorites, website overlay and photos not referenced by another journal.
7. **Plan:** Implement, test route filtering and synced deletion/restart/recovery, build both signed updates, and prepare deployment artifacts for review.
8. **Risks:** Deletion propagates to both apps. Show a trip-specific confirmation and backup guidance. Hold the existing recording lease during deletion, requiring recording to be stopped in both apps. Persist interrupted deletions for retry. Conflicting library versions require explicit resolution.
9. **Open questions:** None. Map filters are independent of journal cards. A month with All years selects that month across years. Website deletion remains a separate owner action.

## Validation and release

Release target: Android 1.5.1 (version code 14); website static map update. Both APKs retain package IDs and signing key.
Use synthetic emulator records only for destructive validation. Existing exported backups can restore deleted trips.
Production rollout: publish both APKs to App Shelf and deploy static website assets with the existing compatible Worker. No database migration, DNS, Access or R2 configuration change.
Backout: restore the prior website deployment and App Shelf current release pointers. Do not downgrade installed apps or restore a production database for this UI change. Restore accidentally deleted trips from an exported app backup.
