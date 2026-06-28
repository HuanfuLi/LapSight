---
phase: 05-track-setup-and-course-profiles
plan: 9
subsystem: lap-timing
tags: [kotlin-multiplatform, lap-engine, course-direction, recorded-reverse, tdd, compose]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 7
    provides: CourseDefinition.derivedSectors, SectorResult, next-expected-boundary order gate
  - phase: 05-track-setup-and-course-profiles
    plan: 8
    provides: TrackProfileController lifecycle + current-selection contract (CurrentTrackSelection.direction)
provides:
  - CourseDefinition.acceptedStartFinishSign (explicit accepted approach side; null keeps legacy learned-first-crossing)
  - LapEngine enforces explicit orientation from the FIRST crossing and on completion; turnaround never fabricates a lap/Sector nor pauses timing (D-17, D-21)
  - CourseGeometryBuilder.directionalCourse (Recorded/Reverse projection over identical anchors) + directionalProgress (wrap(s-s0) / wrap(s0-s))
  - courseFromTrack(startFinish, sectors, direction) direction-aware overload
  - TimingSession.direction snapshot (defaulted Recorded) persisted in every draft/session
  - SessionController.startTiming resolves + uses the persisted direction; Ghost suppressed for non-Recorded runs
  - DriveMarkingController.selectedDirection + selectDirection; DriveScreen pre-Timing Recorded/Reverse selector
affects: [05-10-ghost-compatibility, 05-12-course-progress-matcher]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Course Direction is a configuration over ONE physical revision, not a global wrong-direction state machine: Recorded and Reverse share anchors but invert progress, Sector order, and every line's endpoint orientation, and accept the opposite physical crossing"
    - "Explicit accepted approach side (acceptedStartFinishSign) replaces learned-first-crossing for direction-specific courses, so even the first opposite crossing is rejected; null courses keep the legacy learned gate so existing tests/courses are unchanged"
    - "Reverse keeps ONE positive accepted-side convention and instead swaps every line's endpoints, so a reverse-direction physical crossing lands on the same accepted side (no per-direction sign value needed)"
    - "A wrong-way crossing (including a mid-lap turnaround) is rejected BEFORE any cooldown/min-lap bookkeeping, so timing simply continues with no false completion and no pause"
    - "Direction rides the immutable TimingSession snapshot, so recovery Resume and Review read the exact configuration the run was timed under; Ghost loading is suppressed for non-Recorded runs until reference identity becomes direction-aware (05-10)"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/CourseDirectionReplayTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/CourseGeometryBuilder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/SessionControllerTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingControllerTest.kt

key-decisions:
  - "Encode the accepted approach side as a nullable CourseDefinition.acceptedStartFinishSign: non-null enforces explicit orientation from the first crossing (Recorded/Reverse); null keeps the legacy learned-first-crossing behavior gated by config.enforceDirection, so every existing course/test is byte-for-byte unchanged"
  - "Reverse swaps every line's endpoints and reverses Sector order but keeps the SAME positive accepted-side convention, because swapping endpoints flips the geometric sign exactly once — the engine therefore needs one orientation rule for both directions and the convention is FORWARD_CROSSING_SIGN=+1.0 (the orientation the editor and demo course produce)"
  - "Boundary opposite/duplicate/backward rejection stays the 05-07 next-expected-boundary order gate (no per-boundary direction model); only the start/finish line needs an explicit accepted side, so a turnaround re-cross of a passed boundary never advances and the wrong-way start/finish re-cross is rejected before cooldown/min-lap, leaving timing running"
  - "Direction is resolved inside SessionController.startTiming from the exact current selection (matching the Track id) and snapshotted into the defaulted TimingSession.direction; Ghost loading is suppressed for non-Recorded runs so a Reverse run can never consume the recorded-direction legacy per-Track reference (identity becomes direction-aware in 05-10)"

requirements-completed: [SC-03, SC-04]

# Metrics
duration: ~55min
completed: 2026-06-27
---

# Phase 5 Plan 09: Recorded / Reverse Course Direction Summary

**Delivered selectable Recorded/Reverse course behavior as a configuration over ONE physical revision rather than a global wrong-direction state machine: the clean-room lap engine now takes an EXPLICIT accepted approach side (`CourseDefinition.acceptedStartFinishSign`) so an opposite-direction crossing is rejected from the very first pass and a mid-lap turnaround never fabricates a short lap/Sector nor pauses the running clock; `CourseGeometryBuilder.directionalCourse` projects a forward-oriented course into Recorded/Reverse over identical anchors by swapping every line's endpoints and reversing Sector order while keeping one positive accepted-side convention; and `SessionController.startTiming` resolves the persisted current-selection direction, builds the direction-specific course, snapshots `TimingSession.direction` into every draft/session, and suppresses Ghost loading for non-Recorded runs so a Reverse run never consumes the recorded-direction legacy reference. A compact pre-Timing Recorded/Reverse selector appears only on the stationary Drive surface. Closes SC-03 / SC-04 and D-17, D-18, D-20, D-21.**

## Performance

- **Duration:** ~55 min
- **Tasks:** 2 (both `type="auto" tdd="true"`)
- **Files:** 11 (1 created test, 10 modified)

## Accomplishments

- **Explicit accepted orientation in the engine (Task 1):** `CourseDefinition` gains a nullable `acceptedStartFinishSign`. When set (a Recorded/Reverse config) `LapEngine` seeds `expectedStartFinishSign` from it, gates even the FIRST start/finish crossing in `AwaitingStart`, and enforces the side on completion regardless of `config.enforceDirection`. A wrong-way crossing is rejected with `WrongDirection` BEFORE any cooldown/min-lap bookkeeping, so a turnaround continues timing with no false completion and no pause (D-17, D-21). When `acceptedStartFinishSign` is null the engine keeps the legacy learned-first-crossing behavior, so every existing course/test is unchanged.
- **Direction-specific course construction (Task 1):** `CourseGeometryBuilder.directionalCourse(base, direction)` projects a forward-oriented lap course into the selected direction over identical physical anchors. Recorded keeps geometry/order with the explicit `FORWARD_CROSSING_SIGN`; Reverse swaps the start/finish and every boundary's endpoints, walks the intermediate boundaries in reverse spatial order (relabeled Sector 1..N from start/finish), and keeps the same positive accepted side so a reverse-direction physical crossing is accepted and the recorded one is rejected. `directionalProgress` mirrors progress around the start/finish anchor (`wrap(s - s0)` vs `wrap(s0 - s)`).
- **Green direction replay gate (Task 1):** `CourseDirectionReplayTest` proves Recorded/Reverse share anchors but produce distinct ordered definitions; an opposite first crossing is rejected (both directions); the IDENTICAL physical replay completes a lap under Reverse but none under Recorded; a full reverse lap yields exactly N complete Sectors in reversed order; a turnaround creates no false lap/Sector and never pauses the clock; and the progress transform mirrors around start/finish.
- **Persisted direction drives Timing (Task 2):** `TimingSession.direction` (defaulted Recorded) snapshots the configuration into every draft/session. `SessionController.startTiming` resolves the persisted current-selection direction for the Track (via `selectedDirectionFor`), builds the direction-specific `CourseDefinition` through the new `courseFromTrack(startFinish, sectors, direction)` overload, and recovery Resume rebuilds the same direction from the snapshot. Ghost loading is suppressed for any non-Recorded direction so a Reverse run never consumes the recorded-direction legacy per-Track reference (SC-04).
- **Pre-Timing selector (Task 2):** `DriveMarkingController` exposes `selectedDirection` and `selectDirection` (rewrites only the direction of the current selection, never the Track). `DriveScreen` adds a compact two-button Recorded/Reverse selector rendered only on the stationary Drive surface (never the moving fullscreen timing dash), with no automatic recommendation.

## Task Commits

1. **Task 1 — RED** — `00ccb1d` — `test(05-09)`: failing `CourseDirectionReplayTest` (compile-fails on the missing `acceptedStartFinishSign` / `directionalCourse` / `directionalProgress` API).
2. **Task 1 — GREEN** — `018ff20` — `feat(05-09)`: explicit accepted orientation in `LapEngine`, `acceptedStartFinishSign` on `CourseDefinition`, `directionalCourse`/`directionalProgress` in `CourseGeometryBuilder`; direction + complete-Sector + lap-engine tests green.
3. **Task 2 — RED** — `bb0d235` — `test(05-09)`: direction-persistence/rejection assertions in `SessionControllerTest` + `DriveMarkingControllerTest`.
4. **Task 2 — GREEN** — `0d00bfd` — `feat(05-09)`: `TimingSession.direction`, direction-aware `startTiming` + course overload, Ghost suppression, `selectDirection`/`selectedDirection`, Drive selector; `:shared:check` + `:androidApp:assembleDebug` green.

## Files Created/Modified

- `lap/TimingLines.kt` (modified) — `CourseDefinition.acceptedStartFinishSign` (the explicit accepted approach side).
- `lap/LapEngine.kt` (modified) — seed/explicit-direction wiring, first-crossing gate, completion gate before cooldown/min-lap.
- `track/CourseGeometryBuilder.kt` (modified) — `FORWARD_CROSSING_SIGN`, `directionalCourse`, `directionalProgress`.
- `lap/CourseDirectionReplayTest.kt` (created) — the Recorded/Reverse/turnaround replay gate.
- `session/SessionModels.kt` (modified) — `TimingSession.direction` (defaulted Recorded).
- `session/TimingSessionRecorder.kt` (modified) — direction-aware `courseFromTrack` overload.
- `session/SessionController.kt` (modified) — `selectedDirectionFor`, directional course at start, snapshot direction, recovery Resume direction, Ghost suppression for non-Recorded.
- `ui/DriveMarkingController.kt` (modified) — `selectedDirection` snapshot field + `selectDirection`.
- `ui/DriveScreen.kt` (modified) — pre-Timing `DirectionSelectorSection` + `onSelectDirection` threading.
- `session/SessionControllerTest.kt`, `ui/DriveMarkingControllerTest.kt` (modified) — direction wiring/persistence assertions.

## Decisions Made

- **Nullable accepted side, not a new engine mode.** `acceptedStartFinishSign` is the whole direction mechanism in the engine; non-null = explicit enforcement (Recorded/Reverse), null = legacy learned-first-crossing. This adds the explicit-orientation contract without touching any existing course, fixture, or test.
- **Reverse = swap endpoints + reverse order, one accepted-side convention.** Because swapping a line's endpoints flips the geometric signed side exactly once, Reverse and Recorded can share a single positive convention (`FORWARD_CROSSING_SIGN`); Reverse encodes the flip entirely in geometry/order, so the engine has one orientation rule.
- **Order gate stays the boundary guard.** Boundary duplicate/backward/opposite rejection remains the 05-07 next-expected-boundary order gate; only start/finish needs an explicit side. A turnaround re-cross of a passed boundary therefore never advances, and the wrong-way start/finish re-cross is rejected before any cooldown/min-lap so timing continues.
- **Snapshot-truth direction + Ghost suppression.** Direction rides the immutable `TimingSession` snapshot (defaulted Recorded for V1 compatibility), and Ghost loading is suppressed for non-Recorded runs so a Reverse run cannot consume the recorded-direction legacy reference; direction-aware reference identity is deferred to Plan 05-10.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first Gradle run)
- **Issue:** The parallel worktree does not inherit the main checkout's gitignored `local.properties` (`sdk.dir`), so Gradle cannot configure the Android SDK.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (confirmed gitignored; NOT committed).
- **Commit:** N/A (gitignored build config).

### Predicted-file note

- Task 1's `<files>` predicted an edit to `track/TrackProfileModels.kt` ("Add `CourseDirection` to `TrackProfileModels`"). No edit was needed: `CourseDirection` (Recorded/Reverse) and `CurrentTrackSelection.direction` already exist there from Plans 05-03/05-04, so the enum was read fresh and left intact. The accepted-side carrier instead lives in the lap-domain `CourseDefinition` (`TimingLines.kt`), which the engine consumes directly.

**Total deviations:** 1 (Rule 3 environment/blocking). No architectural change, no new dependency, no deferred scope beyond the plan's already-deferred direction-aware Ghost identity (05-10).

## Verification

- **Task 1:** `:shared:testAndroidHostTest --tests "*CourseDirectionReplayTest*" --tests "*CompleteSectorReplayTest*"` (plus `*LapEngineTest*` for regression) BUILD SUCCESSFUL.
- **Task 2:** `:shared:testAndroidHostTest --tests "*CourseDirectionReplayTest*" --tests "*CurrentTrackSelectionTest*" --tests "*SessionControllerTest*" --tests "*DriveMarkingControllerTest*"` BUILD SUCCESSFUL; full `:shared:check` BUILD SUCCESSFUL; `:androidApp:assembleDebug` BUILD SUCCESSFUL.
- iOS link/run tasks remain SKIPPED on Windows (as in every prior plan); iOS source compiles cleanly under `:shared:check`.
- Manual on-device UAT of the Recorded/Reverse selector and a reverse-direction lap run remains a normal phase-level checkpoint (not a code stub).

## Issues Encountered

- Pre-existing `!!`-on-non-null and always-true-condition warnings in unrelated lap/review test files (`CrossingDetectorTest`, `GeometryTest`, `LapEngineTest`, `ReviewSummaryTest`) were left untouched (out of scope).

## Known Stubs

- None. The engine enforces real explicit orientation, the directional builder produces real swapped/reordered geometry, `startTiming` persists and uses the real selected direction, and the Drive selector writes the real current selection. No hardcoded empty/placeholder data flows to the UI. Direction-aware Ghost reference identity is a documented deferral to Plan 05-10 (Reverse currently runs without a Ghost), not a stub.

## Threat Flags

- None new. T-05-19 (directional boundary tampering) is mitigated by the explicit accepted side enforced from the first crossing and proven by `CourseDirectionReplayTest`'s opposite-crossing cases. T-05-20 (direction selection tampering) is mitigated by the typed `CourseDirection` enum, exact current-selection persistence (defaulted-Recorded migration), and Ghost suppression for non-Recorded runs, proven by `SessionControllerTest`/`DriveMarkingControllerTest` direction cases. No security surface beyond the plan's `<threat_model>`.

## Self-Check: PASSED

- All 11 created/modified files verified present on disk.
- All four task commits verified in git log: `00ccb1d` (Task 1 RED), `018ff20` (Task 1 GREEN), `bb0d235` (Task 2 RED), `0d00bfd` (Task 2 GREEN).
- `:shared:check` (all shared tests incl. `CourseDirectionReplayTest`/`CompleteSectorReplayTest`/`LapEngineTest`/`SessionControllerTest`/`DriveMarkingControllerTest`/`CurrentTrackSelectionTest`) and `:androidApp:assembleDebug` are BUILD SUCCESSFUL; iOS compiles (link/run SKIPPED on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
