# Development and validation

## Maintained source

The Git checkout whose origin is `https://github.com/neo12242/RykerConnect.git` is the maintained source. Work in that checkout's `Android/`, `Simulator/`, `Firmware/RykerConnect-REV05/`, and current hardware directories. `origin/main` is the shared baseline; changes use review branches.

For the original development workspace, this is the nested `Publication-20260916/RykerConnect` checkout. Despite its historical directory name, it is now the development checkout. The outer working copies and private checkpoints are retained as historical/local material; do not continue parallel source edits or copy them wholesale into Git. There is no need to move or delete them. New contributors can clone the repository to any directory.

Older upstream source directories remain provenance/reference material. DadRides website/API remains in its separate repository. Optional Android integration stays here.

## Run the baseline

Use a standard Python 3.13 installation, the SDK/JDK prerequisites in [Android setup](ANDROID.md), and a C++17 compiler. On Windows, use an MSVC Developer PowerShell/Command Prompt if using `cl`; Linux can use `g++`. `CXX` may specify a compiler executable, without flags.

On the original Windows machine, launching Gradle from Microsoft Store Python made the SDK under the user's AppData directory invisible to Gradle, even with a valid `ANDROID_HOME`. Running the same command through a standard Python runtime resolved it. Use a non-Store Python installation for the runner if you encounter this symptom. The runner itself also supports Python 3.12; the simulator environment and CI use 3.13.

From the Git checkout root, create a private validation environment once:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r Simulator/requirements.txt -r scripts/requirements-validation.txt
.\.venv\Scripts\python.exe scripts/validate.py
```

On Linux/macOS, use `.venv/bin/python` instead. Set `ANDROID_HOME` to the local Android SDK and `JAVA_HOME` to a compatible installed JDK; the committed Gradle daemon criteria request JetBrains JDK 21. Dependencies require network access on a fresh installation.

If you use ignored `Android/local.properties` on Windows, escape the drive colon and use forward slashes, for example `sdk.dir=C\:/Android/Sdk`. An unescaped drive colon can trigger Android lint's `PropertyEscape` error even when the build locates the SDK.

Run selected checks with `python scripts/validate.py --only simulator rtc` (using your environment's Python). All four checks are selected by default. If reusing separate existing environments, `SIMULATOR_PYTHON` and `PLATFORMIO_PYTHON` may specify their Python executables. These settings are local environment variables, never committed machine paths.

The command runs existing tests/builds and exits nonzero if any selected check fails. It continues to other selected checks after a failure. Each invocation writes its own ignored `.validation/<UTC timestamp>/` directory with logs and a JSON summary containing commit, dirty-tree status, selected checks, durations and exit codes. A partial run is not a complete baseline. Reports from dirty trees are development evidence, not evidence for an unchanged commit. Review logs before sharing; local build paths may be private.

Android tasks are explicitly rerun so an earlier up-to-date test report cannot substitute for the current execution. Firmware builds may reuse compiler caches locally; CI starts from a clean checkout.

The runner does not provision SDKs, install an APK, run instrumentation tests, flash a board, or publish artifacts. Build outputs and compiler intermediates remain ignored.

## GitHub checks

`Engineering baseline` runs four independent Ubuntu jobs on pushes to `main`/`codex/**` and pull requests to `main`. Manual dispatch is available once the workflow exists on the default branch. Each job calls the same runner with one selected check. Setup or validation failures fail the job; matrix siblings continue. Reports are retained for 14 days; APKs and firmware binaries are not uploaded.

Actions are pinned to reviewed commit IDs, with a read-only repository token and no application credentials. CI uses the pinned project dependency versions and installs SDK 37.0/build-tools 37.0.0. This validates clean Linux builds alongside local Windows checks. Runner images and transitive dependencies can still change; this is repeatable validation, not a claim of bit-for-bit reproducibility.

Keep existing warnings visible. Review Android lint reports before proposing cleanup; do not introduce a suppression baseline merely to make the first run green. CI checks are not automatically required merge checks; branch protection is outside this change.

## Before proposing a merge

1. Run the relevant checks locally, and all four when establishing a baseline.
2. Review `git diff --check` and the complete diff; stage only intended files.
3. Scan outgoing content for private data and credentials. Keep pairing state, owner keys, SDK paths, signing keys, backups and runtime logs ignored.
4. Push the review branch and verify all four hosted jobs for its current commit.
5. Record manual acceptance separately using [device acceptance](DEVICE-ACCEPTANCE.md).

Historical results remain in [publication validation](VALIDATION.md). The engineering baseline has its own dated evidence record.
