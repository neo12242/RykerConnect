# Current application updates

Application version: **1.9.0**, version code **20**. Phone and ESP packages retain their existing application IDs. Install the matching edition over the existing app; do not uninstall or clear data. Update both editions if both participate in a shared library. This release does not require a watch update.

Deploy DadRides ownership API support and its additive `ownership-dashboard.sql` migration before using modification synchronization. Preserve the existing database and media. Distribution builds may configure their watch-download destination with the `watchDownloadsUrl` Gradle property; the public-source default is the repository's Releases page.

Phone and ESP companion editions share the ownership dashboard and garage editors. The Phone Edition records manually with the phone GPS; ESP-specific device and notification capabilities remain confined to the companion edition.

## Ownership and service records

- Fuel, maintenance and modification spending by year, all time or named season, with monthly/category details and CSV/PDF export.
- Cost per distance requires confirmed odometers for the selected period. Unknown prices remain distinct from zero; estimates do not count as spending.
- Private modifications include dated purchases, estimates, installation details and notes. Revision-checked DadRides synchronization preserves conflicting edits for review.
- Article/photo editing stays on DadRides. The app's editor shortcut exchanges a short-lived, one-use code for a browser session without putting the permanent owner key in the URL.
- New-bike service baselines require owner-entered date/mileage, Preview and Apply. Completed service history takes precedence. There are no prefilled personal baseline values in the source.

## Rides, sharing and watches

- Shared ride libraries, explicit conflict review, website edit synchronization, trip deletion and map filters.
- Pause/resume, interrupted-ride recovery, and backup validation for paused trips.
- Upload normalization for metadata-bearing JPEG derivatives while retaining originals.
- Wear OS controls for recording and service status, with Phone and ESP watch flavors.

## Validation and release boundaries

See `DEVELOPMENT.md` for repeatable build/test commands. Simulator and local API evidence does not establish physical-device or hardware acceptance. Firmware and hardware remain in this repository; the DadRides website/API remains in its separate repository.

Publishing source does not install applications or deploy DadRides. Supply local SDK settings, signing material and service credentials privately. Deployment receipts, personal backups, real ride/photo data and machine-specific review notes are intentionally excluded from Git.
