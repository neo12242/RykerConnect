# Photo upload repair — 2026-09-24

## Problem
The project owner's Phone upload fails at 0% with “Upload a JPEG without EXIF metadata.” Production has accepted two private manifests but neither photo. Re-encoding an Android bitmap does not guarantee that HDR/XMP metadata is omitted; the server rejects APP1, APP13 and JPEG comments.

## Goals / success criteria
New uploads and retries of existing queued uploads produce JPEG copies accepted by the unchanged server privacy check. Preserve original photos, local derivatives, rides, settings, credentials and the user's chosen route/privacy fields. Verify actual Android-generated HDR input and an old queued payload against the real worker handler using isolated local storage.

## Scope
Normalize upload copies into a fresh SDR/sRGB bitmap when metadata is present. Use the exact normalized bytes for manifest hashes and photo requests. Repair old prepared copies during retry, with deterministic revisions and recoverable writes. Share the fix between Phone and Companion and release 1.4.2 / code 12 through their existing App Shelf entries.

## Out of scope
No weakening of server metadata checks, automatic publication, deletion of remote drafts, changes to original photos or local ride data, signing-key changes, new Cloudflare permissions or infrastructure changes.

## Constraints
Keep same-signed in-place updates and demo features disabled. Never emit keys, image content, route coordinates or private manifests into logs. Existing upload snapshots remain the source when repairing a retry, even if the journal was edited afterward.

## Proposed approach
Render only unsafe upload derivatives into a fresh standard bitmap, encode and validate the JPEG before upload. Preserve already-safe bytes unchanged. New queues store the same bytes described by their manifest. For old queues, verify source checksums and write normalized sibling upload copies; preserve their immutable source files. Recalculate the remote revision from the repaired manifest before sending assets. Never reuse a revision whose checksums describe different bytes.

## Implementation plan
1. Add shared photo normalization and retry preparation.
2. Add HDR/metadata, checksum, idempotency and original-preservation regression checks; run local worker integration and relevant Android checks.
3. Build and verify both APKs, update App Shelf with prior releases retained, and record release evidence.
4. Ask The project owner to install as an update and retry the failed upload on his phone.

## Risks / rollback
Upload copies use standard dynamic range for web compatibility; original HDR photos remain intact. Old incomplete remote drafts remain private and are not automatically deleted. Android physical-phone acceptance still needs The project owner's retry. Preserve prior APKs/catalog backup; fix-forward is preferable to downgrading an installed version.

## Open questions / authorization
The reported error and accepted production manifests identify the affected step. This is a repair of The project owner's requested phone-upload capability. No additional user data is needed for synthetic local reproduction. Exact metadata in the user's photo is not inspected or assumed to be HDR specifically.
