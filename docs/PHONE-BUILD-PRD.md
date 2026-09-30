# RykerConnect Phone — approved 2026-09-23

## Problem
The web-distributed companion APK is blocked by Play Protect with “App blocked to protect your device.” Its notification-listener permission matches Google's documented sensitive-permission block. The project owner needs phone GPS recording before his ESP is available.

## Goals and success criteria
Provide a separately installable phone edition with manual start/stop recording, local ride history, photos, backup/restore, and optional DadRides draft uploads. The packaged manifest must omit notification-listener, accessibility, SMS, phone-state, and Bluetooth/companion permissions and services. No notification-reading setup prompts or nonfunctional ESP controls should appear. Play Protect remains enabled; a real-phone installation remains the final acceptance check.

## Scope
Reuse maintained Android source and existing trip/photo/publishing stores. Add phone and companion build flavors, a phone dashboard/setup path, appropriate regression tests, App Shelf download/setup notes, and an updated DadRides Owner link. Keep the full companion edition available.

## Out of scope
No Play Store account, policy appeal, security bypass, ESP firmware change, new publishing key, automatic ride publication, or automatic cross-app data migration. The phone edition excludes notification mirroring and notification-based Maps/music integration.

## Constraints
Retain Android 12+ support and existing signing identity for test distribution. Use a distinct `.phone` application ID so the original companion app and its data are untouched. Each edition has separate local data; existing reviewed backup/restore provides migration. No credentials in APKs, setup notes, source, or logs. App Shelf stays behind the existing Cloudflare policy with local disk storage.

## Proposed approach
Use standard Android product flavors and manifest overlays. Keep the notification listener only in companion sources. Share recording, journal, garage, photo, backup, and publishing implementations. Present only usable phone tools and ask for location when recording starts; notification posting for the app's own recording/reminders remains supported.

## Implementation plan
1. Add flavors, manifest/source separation and phone UI.
2. Build and test both flavors; inspect the final APK manifest/signature and run phone emulator checks for manual recording, journal access and supported tools.
3. Import the verified phone APK as its own App Shelf entry and update setup links. Preserve the prior release and verify authenticated download routing.
4. Record checks and physical-device limitations.

## Risks
Google may still request scanning or flag a build for another reason. Valid signing is not a malware verdict. Separate app storage requires an explicit backup/restore when switching editions. A debug-signed test APK is not a Play Store production release.

## Open questions and approval
The project owner approved the proposed phone build after reviewing the feature exclusions. No further product decision blocks implementation. Physical-phone Play Protect/install/GPS acceptance remains pending The project owner's device test.
