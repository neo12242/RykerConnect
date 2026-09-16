# Publication validation — 2026-09-16

Checked against the isolated publication copy, based on upstream commit `b404ef754d7ee47d6c592978d7a1c26311b3fb4a`.

| Check | Result |
|---|---|
| Android `assembleDebug testDebugUnitTest lintDebug` | Build succeeded; 80 tests passed; lint 0 errors, 165 warnings |
| Simulator `unittest discover -v` | 16 tests passed, including loopback HTTP validation |
| REV05 production firmware | PlatformIO build succeeded |
| REV05 SensorTest firmware | PlatformIO build succeeded |
| REV05 native RTC test | Passed time boundaries, BCD, voltage-loss/STOP flags, I2C failure and recovery |
| REV05C-12V KiCad ERC/DRC | 0 violations, 0 unconnected items, 0 schematic parity issues |
| REV05-USB KiCad ERC/DRC | 0 violations, 0 unconnected items, 0 schematic parity issues |
| Separate DadRides site | Fresh npm install/build/local schema succeeded; all 6 API tests passed |

These checks did not flash hardware, place a PCB order, modify production hosting/DNS, or rerun the full interactive phone/foldable test suite. Build success does not prove electrical safety, real phone background behavior, physical sensor/BLE behavior, road GPS accuracy or assembler placement. Historical reports under hardware revisions retain their original validation context; sanitized paths do not represent new engineering checks.

The simulator tests ran with the existing pinned Bumble Python environment against this copy's source. DadRides used a fresh local database and newly generated ignored development credentials. Android dependencies/toolchains used this machine's configured SDK and caches; a completely clean machine still needs the prerequisites documented in the setup guides.

## Privacy review boundaries

Publication uses an explicit source tree, not a bulk upload of the working folder. Private checkpoints, app backups, local databases, owner keys, bonds, logs, SDK paths, signing keys and generated test binaries are excluded. Personal domain defaults were replaced with `rides.example.com`. Source and archive scans supplement image/PDF metadata inspection; automated scanning cannot guarantee that every possible sensitive value has been recognized.

Gitleaks scans of upstream history and publication source flagged inherited interactive-BOM keyboard code (`metaKey`/`shiftKey`) and a public vendor datasheet URL's `hkey` query parameter in the KiCad footprint cache. These are upstream material, not a newly supplied account credential. Upstream history remains intact, including upstream author identities and previously published files. No user-local Git history is imported. The review does not claim to remove upstream information from GitHub's existing history.

Private-key header strings in old upstream firmware binaries were checked: no complete PEM private-key blocks were found by that check. This is a binary string check, not reverse engineering or a full security audit of upstream firmware.
