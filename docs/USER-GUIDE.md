# Using RykerConnect

## Dashboard and connection

Dashboard shows ride, music, navigation, weather and sensor cards, plus fuel/service information and quick actions. Settings → Dashboard layout controls visible cards and order. Connect manages the main BLE device, intentional disconnect/reconnect, diagnostics and the debug OLED preview. A disconnected link or stale sensor is shown as unavailable rather than replaced with demo readings.

## Rides

My Trips contains recorded rides and separate read-only Demo rides. Record manually or configure automatic recording. Automatic recording has dropout recovery; stationary automatic sessions are retained but hidden from default ride totals. Include stationary sessions reveals them. An interrupted ride is labeled.

Open a ride for map, replay, speed/elevation charts, stop information, summary, notes, favorites and photos. Missing GPS spans remain gaps; elevation needs suitable recorded accuracy. Search/filter history, export GPX, trim a copy or merge non-overlapping rides into a copy; originals remain.

Last Parked distinguishes a disconnect candidate from a confirmed location. Confirm parked here or save the current location explicitly. No recent accurate location means no parking location. GPS distance is an estimate, not a vehicle odometer reading.

## Garage and maintenance

My Garage records mileage, fuel and completed service using the actual vehicle odometer. Full-to-full fuel calculations need complete records; partial fills, missed fill-ups and invalid ordering affect results. Costs use USD. Entries support notes, edits, backdating, optional location and receipt photos.

Configure services and recurring intervals in Settings. The maintenance planner separates planned work and parts to buy from completed service. Buying parts/checking a task does not reset maintenance. Record and confirm the completed service to update its baseline. Reminders are advisory, initially disabled, and Android may delay background jobs.

## Appearance and widgets

Settings offers named themes, day/night choices, dashboard layout, riding preferences and display profiles. Home-screen widgets include quick actions, My Ryker and Last Parked. Add them through Android's launcher and resize within the launcher's supported grid. Widgets are snapshots with update times, not live readings. Privacy settings can hide vehicle/parking details; VIN and home address are not displayed on widgets.

## Backup and restore

Use Settings → Backup & restore and Android's document picker. Backups include stored ride/garage/profile data, maintenance plans and app-owned photos. They are not encrypted. Original photo EXIF can be retained in a backup. Keep archives private.

Restore validates the archive and previews contents. Existing IDs are preserved; missing records are added. Preference restoration is optional. Very large photo collections can exceed the 100 MB archive limit and 90 MB photo preflight; the app reports failure rather than silently omitting photos. Pairing, publishing credentials, upload queues, active recordings and cached/offline downloaded content are not a substitute for the original files and are not all backed up.

## Optional DadRides publishing

The integration is built into this APK, disabled by default, and requires a separately deployed [DadRides website/API](https://github.com/neo12242/DadRides).

1. Open a saved ride → Ride summary & photos → Photos & publishing.
2. Attach/select photos, captions, tags and a cover.
3. Enable Settings → Add-ons → DadRides and configure your HTTPS site and its publishing key (not your Cloudflare token).
4. Prepare a public copy. Review route endpoints, privacy radius, photos and statistics; queue a **private draft**.
5. Upload on unmetered Wi-Fi by default; mobile upload needs an explicit override. Retry failed jobs as needed.
6. Open the website's authenticated owner preview and explicitly publish the reviewed revision.

Local changes do not silently replace a published revision. Disabling publishing stops app network work but does not remove published rides. Unpublish through the website. Endpoint trimming and EXIF removal reduce exposure but cannot guarantee anonymity or recall downloaded copies.
