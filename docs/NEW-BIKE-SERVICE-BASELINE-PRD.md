# New-bike service baseline

Approved by The project owner on 2026-09-26 with "proceed". Requested starting point: September 22, 2026, at an explicitly entered odometer (1.609344 km).

1. Problem: an odometer entry does not initialize individual service tracking.
2. Success: services without actual history can track from the new-bike date and mileage, with date and distance calculations consistent in both app editions and DadRides.
3. Scope: Settings > Services & intervals > Set new-bike baseline; editable date/mileage, affected-item preview, apply/correct/remove actions, reminders, backup/shared-library sync and private website display.
4. Out of scope: fabricated completed work, automatic interval selection, publisher setup and unrelated app changes.
5. Constraints: preserve history, costs and intervals. Actual completed service takes precedence. Unconfigured intervals remain explicit. No automatic data change on app upgrade.
6. Approach: separate per-service baseline records containing only service ID, date, odometer and enabled state. Retain disabled records on removal so old backups cannot silently restore a removed baseline. Use existing causal sync and private metadata mirror.
7. Implementation: checkpoint current files, add data model and UI, update every reminder/display consumer and backup validation/projection, run behavioral tests, build both editions and prepare release artifacts.
8. Risks/backout: validate dates and exact mile conversion, test cross-timezone calendar dates and actual-history precedence. Remove/correct baselines without deleting service history. Prefer a forward correction; older app versions ignore baseline records. No physical-phone data is modified by build or tests.
9. Open questions: none for baseline values; device installation and applying the preview require the updated app on The project owner's phone.
