# Engineering baseline PRD

Status: approved 2026-09-17. Implementation, branch push, and GitHub Actions execution approved. Merge remains a separate review step.

## 1. Problem
Development and publication have separate source copies, and validation is manual.

## 2. Goals and success criteria
One maintained checkout; repeatable local and GitHub checks; a dated validation record tied to a commit. Existing failures and warnings remain visible. Physical testing is never inferred from builds.

## 3. Scope
Android debug build, unit tests and lint; simulator tests; REV05 production and SensorTest builds; native RTC tests; physical-device acceptance checklist. Reuse existing checks.

## 4. Out of scope
New features, UI changes, hardware redesign, flashing, deployment, release binaries, merging, repository security settings, and DadRides website/API changes.

## 5. Constraints
Preserve private data, existing folders, repository boundaries and pinned toolchains. No emulator installs or instrumentation tests against working app data.

## 6. Proposed approach
Maintain the existing Git checkout associated with the published fork. Use a single validation runner locally and in separate CI jobs. Record results and retain logs outside tracked source.

## 7. Implementation plan
Document source ownership; rerun existing checks; add the local command and GitHub Actions workflow; record results and known warnings; review the diff, scan outgoing content and push codex/engineering-baseline; verify hosted results.

## 8. Risks and rollback
Clean CI runners may expose local toolchain assumptions. Fix baseline tooling within scope; report product failures separately. All changes are isolated on the branch. Existing source and private checkpoints remain intact. Before merge, return to the prior checkout/branch; after a separately approved merge, revert its commit through normal review. CI does not flash, install, or deploy.

## 9. Open questions
Physical phone/hardware acceptance remains pending until devices are available. Existing lint warnings are recorded rather than silently suppressed. No branch protection changes are included.
