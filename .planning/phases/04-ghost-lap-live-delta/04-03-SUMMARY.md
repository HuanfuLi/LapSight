---
phase: 04-ghost-lap-live-delta
plan: 03
subsystem: ghost-delta
tags: [kotlin-multiplatform, compose-multiplatform, ghost, live-delta, ui, tdd]

# Dependency graph
requires:
  - phase: 04-ghost-lap-live-delta
    plan: 01
    provides: LiveDeltaSnapshot (Available/Unavailable) domain model
  - phase: 04-ghost-lap-live-delta
    plan: 02
    provides: Recorder-owned LiveDeltaEngine; SessionController.liveDelta()/activeReference(); TimingSessionRecorder timing state
provides:
  - DeltaDisplayState + DeltaTone — pure, Compose-free value-only delta mapping (--/+x.xxxs/-x.xxxs + faster/slower/neutral tone)
  - TimingRunSnapshot — read-only production timing/delta view for the Drive UI (current/last/best/laps/speed/accuracy/source + deltaDisplay)
  - SessionController.timingRunSnapshot()/ingestSample() — production sample pump + snapshot, removing recorderForTest() from production UI
  - Mounted-phone TimingRunSurface rendering current lap (primary) + value-only live delta (second) + compact secondary metrics
affects: [04-04 variable-pace UAT]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Platform-free display mapping: DeltaDisplayState collapses LiveDeltaSnapshot to text+tone in shared Kotlin; Compose colors are applied only at the UI edge"
    - "Stateless unavailable: an Unavailable delta always maps to `--`/neutral, so the UI can never render a stale value (D-18)"
    - "Production read-through snapshot: UI pumps samples and reads timing/delta via SessionController, never TimingSessionRecorder internals"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/DeltaDisplayState.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/DeltaDisplayStateTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt

key-decisions:
  - "DeltaDisplayState/DeltaTone live in the ghost package (next to LiveDeltaSnapshot), not the ui package — pure, Compose-free presentation that both the session snapshot and the Compose UI can depend on without a session<->ui inversion"
  - "TimingRunSnapshot carries deltaDisplay AND independent non-delta metrics so an unavailable `--` delta never erases current/last/best/laps/speed/accuracy (D-19, T-04-11)"
  - "Added SessionController.ingestSample()/timingRunSnapshot() so the Drive UI drives timing and reads state through the controller, dropping the last production use of recorderForTest()"
  - "Slower tone uses orange #FF9F43 (UI-SPEC offered orange or red); faster #8CFF9B, neutral #9AA8B8"

patterns-established:
  - "Pattern: presentation mapping stays in shared Kotlin and is unit-tested without a Compose screenshot harness; the composable only binds text + a tone->Color lookup"

requirements-completed: [GHOST-03]

# Metrics
duration: 5min
completed: 2026-06-26
---

# Phase 4 Plan 03: Minimal Mounted-Phone Live Delta UI Summary

**Surfaced the live ghost delta on the mounted-phone timing screen as a value-only second readout (`--` / `+0.421s` / `-0.218s`) with faster/slower/neutral semantic color, keeping current lap time primary and all other timing metrics working when the delta is unavailable — driven entirely through `SessionController` so production UI no longer touches `recorderForTest()`.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-06-26T03:51:44Z
- **Completed:** 2026-06-26T03:56:16Z
- **Tasks:** 3
- **Files:** 2 created, 4 modified

## Accomplishments
- TDD Wave 0 tests (RED) for the value-only delta contract: `-0.218s`/faster, `+0.421s`/slower, `--`/neutral, unavailable-clears-stale, and a no-directional-words assertion (D-10, D-14, D-15, D-17, D-18).
- Pure, Compose-free `DeltaDisplayState` + `DeltaTone` mapping the domain `LiveDeltaSnapshot` to value-only copy plus a semantic tone; an `Unavailable` snapshot always yields `--`/neutral so the UI can never render a stale delta (D-18).
- `TimingRunSnapshot` read-only production view exposing current/last/best lap, lap count, latest speed/accuracy, source provenance, and `deltaDisplay`; the delta and the non-delta metrics are independent so an unavailable delta never erases the rest (D-19, T-04-11).
- `SessionController.timingRunSnapshot()` and `ingestSample()` plus `TimingSessionRecorder.timingRunSnapshot()`, letting the Drive UI pump samples and read timing/delta through the controller — the last production use of `recorderForTest()` is gone.
- `TimingRunSurface` rebuilt to UI-SPEC priority order: current lap time primary (52sp/40sp, accent cyan), live delta second and value-only (44sp/32sp, colored by tone, never larger than the lap time), then compact Last/Best/Laps/Speed/Accuracy cards and the existing Stop/orientation controls + DEMO badge. No maps, charts, ghost controls, or new navigation added (D-16).

## Task Commits

Each task was committed atomically:

1. **Task 1: Wave 0 failing delta display-state tests** — `f09097a` (test)
2. **Task 2: Timing run snapshot + value-only delta display state** — `bcec895` (feat)
3. **Task 3: Render value-only live delta on the timing surface** — `9e71440` (feat)

_TDD gate: RED `test(04-03)` commit (`f09097a`) precedes the GREEN `feat(04-03)` commits (`bcec895`, `9e71440`)._

## Files Created/Modified
- `shared/.../ghost/DeltaDisplayState.kt` (new) — `DeltaTone` enum + `DeltaDisplayState` value object with `from(LiveDeltaSnapshot)` / `fromDeltaMillis(Long)`; explicit-sign, zero-padded-millis, width-stable copy.
- `shared/.../ui/DeltaDisplayStateTest.kt` (new) — 6 pure tests encoding the exact copy/color/unavailable contract without a Compose harness.
- `shared/.../session/SessionModels.kt` — `TimingRunSnapshot` data class + `inactive()` factory.
- `shared/.../session/TimingSessionRecorder.kt` — `timingRunSnapshot()` assembling lap engine state + latest sample + live delta.
- `shared/.../session/SessionController.kt` — `timingRunSnapshot()` (read-through) and `ingestSample()` (production sample pump).
- `shared/.../ui/DriveScreen.kt` — `timingRun` state; poll loop now uses `ingestSample()`/`timingRunSnapshot()`; `TimingRunSurface` renders current lap primary + value-only `DeltaReadout` second + compact secondary metrics; `DeltaTone.toDeltaColor()` UI-SPEC color lookup.

## Decisions Made
- `DeltaDisplayState`/`DeltaTone` placed in the `ghost` package (not `ui`) to keep the mapping pure and avoid a session<->ui dependency inversion; the `ui`-package test imports it from `ghost`.
- `TimingRunSnapshot` keeps delta and non-delta metrics independent so `--` never blanks the working timing readouts.
- Drive UI now drives timing exclusively through `SessionController`; `recorderForTest()` remains declared for tests only.
- Slower tone rendered orange `#FF9F43` (UI-SPEC allowed orange or red).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing worktree `local.properties` (Android SDK location)**
- **Found during:** Task 1 (RED verification)
- **Issue:** The git worktree had no `local.properties`, so `:shared:testAndroidHostTest` failed with "SDK location not found" before any test could compile/run.
- **Fix:** Copied the main checkout's gitignored `local.properties` (`sdk.dir`) into the worktree root. Not committed (gitignored). Same prerequisite documented in Plan 04-02.
- **Files modified:** `local.properties` (untracked, local-only)
- **Committed in:** N/A (gitignored environment file)

### Minor scope notes (not behavior changes)
- Task 2 added a production sample-pump accessor (`SessionController.ingestSample()`) in addition to `timingRunSnapshot()`. This is required to remove the last production use of `recorderForTest()` (the plan's explicit `<interfaces>` constraint) without the Task 3 UI reaching into recorder internals. No new behavior beyond forwarding a sample to the active recorder.

---

**Total deviations:** 1 auto-fixed (Rule 3 - blocking environment prerequisite). No scope creep; production code matches the plan's interfaces and tasks.

## Issues Encountered
- None beyond the deviation above. `DeltaDisplayStateTest` (6) passes; full `:shared:testAndroidHostTest` is green; `:androidApp:assembleDebug` succeeds. iOS simulator test execution is SKIPPED on Windows (no Xcode), consistent with prior waves.

## TDD Gate Compliance
- RED: `f09097a` (`test(04-03)`) committed the failing display-state tests; verified the failure was the missing `DeltaDisplayState`/`DeltaTone` production symbols (compile error in the single `commonTest` unit).
- GREEN: `bcec895` and `9e71440` (`feat(04-03)`) made `DeltaDisplayStateTest` pass (6/6) and wired the UI; full host suite + `assembleDebug` green.
- No REFACTOR commit was needed.

## Known Stubs
None. The delta is fed by the formal recorder's `LiveDeltaEngine` through `SessionController`; `TimingRunSnapshot` carries real lap-engine state and the live delta. No placeholder/empty values flow to the timing surface — `--` is the defined unavailable readout, not a stub.

## Threat Flags
None. No new network endpoints, auth paths, or trust boundaries. The plan's `<threat_model>` (T-04-07 safety, T-04-10 sign integrity, T-04-11 missing-field robustness) is mitigated: value-only large-typography display with semantic color, tests assert explicit sign + tone, and an unavailable delta renders `--` while other metrics keep rendering.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- GHOST-03 satisfied: a minimal value-only live delta readout is on the mounted-phone timing surface (D-13..D-19 implemented in state + UI).
- Plan 04-04 (variable-pace simulator + UAT) can drive the existing demo provider through Start Timing / Stop / Save and observe the live delta surface; no new UI wiring is required for delta display.

## Self-Check: PASSED

All 2 created files and 4 modified files exist; all task commits (`f09097a`, `bcec895`, `9e71440`) are present in git history. `DeltaDisplayStateTest` (6 tests) passes; `:shared:testAndroidHostTest` and `:androidApp:assembleDebug` are green.

---
*Phase: 04-ghost-lap-live-delta*
*Completed: 2026-06-26*
