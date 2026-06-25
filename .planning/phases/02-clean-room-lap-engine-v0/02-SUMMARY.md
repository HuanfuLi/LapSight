---
phase: 02-clean-room-lap-engine-v0
plan: 02
subsystem: lap-engine
tags: [kotlin-multiplatform, compose-multiplatform, lap-timing, geometry, replay, sectors]

# Dependency graph
requires:
  - phase: 01-mobile-walking-skeleton-gps-probe
    provides: LocationSample model, simulator-backed GPS probe state, Compose dash shell
provides:
  - Clean-room shared lap engine (start/finish crossing detection + state machine)
  - Local equirectangular meter projection around a session origin
  - Segment-crossing geometry with interpolated crossing timestamps
  - Direction, min-lap-time, cooldown, speed, and accuracy false-positive filters
  - Sector-line model, sector crossing detection, and per-lap split timing
  - Deterministic ReplayRunner and synthetic fixtures
  - LapDashState presentation model and replay-backed mounted-phone dash
affects: [Phase 3 sessions/review/export, Phase 4 ghost/delta, Phase 5 course profiles, Phase 6 external GNSS]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure-Kotlin clean-room domain engine in commonMain, independent of Compose/Android/iOS/storage"
    - "Local-meter geometry via equirectangular projection around per-session origin"
    - "Headless deterministic replay (ReplayRunner/DemoLapSession) decoupling lap logic from UI timer"
    - "Presentation state (LapDashState) derived from engine state; UI renders, never reimplements logic"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/Geometry.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngineConfig.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LocalProjection.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/SegmentGeometry.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayRunner.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayFixtures.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapDashState.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/DemoLapSession.kt
    - .planning/phases/02-clean-room-lap-engine-v0/02-VERIFICATION.md
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt
    - README.md

key-decisions:
  - "Lap engine implemented from first principles in commonMain; no GPL code copied"
  - "Start/finish and sector lines are finite two-point segments; crossing is segment-vs-segment, not point proximity"
  - "Reused Phase 1 LocationSample model unchanged as the engine's sample input"
  - "Direction gate learns the expected crossing sign from the first accepted crossing"
  - "Demo dash advances a deterministic replay through the engine on a UI timer"

patterns-established:
  - "CrossingDetector geometry primitive shared by start/finish and sector lines"
  - "Per-sample onSample(): pure with respect to input sequence for deterministic replay"
  - "Reject reasons surfaced in LapTimingState for diagnostics"

requirements-completed: [GPS-05, LAP-01, LAP-02, LAP-03, LAP-04, LAP-05, LAP-06, LAP-07, SAFE-04, ARCH-02]

# Metrics
duration: 14min
completed: 2026-06-25
---

# Phase 2 Plan 02: Clean-Room Lap Engine V0 Summary

**A clean-room shared Kotlin lap engine that detects start/finish and sector crossings from GPS-like sample streams, interpolates crossing times, rejects false positives with five filters, replays deterministic fixtures, and drives a mounted-phone dash showing current/last/best lap, lap count, speed, and compact sector splits.**

## Performance

- **Duration:** ~14 min
- **Started:** 2026-06-25T08:04:46Z
- **Completed:** 2026-06-25T08:18:39Z
- **Tasks:** 7 of 7
- **Files modified/created:** 15 source/doc files (commits also touch generated build artifacts)

## Accomplishments
- Built a fully shared, platform-independent lap engine with deterministic replay coverage.
- Implemented track-scale geometry from first principles: projection, segment intersection, interpolation, heading.
- Added sector-line timing as a first-class feature: detection, per-lap splits, dedup, and reset.
- Integrated lap timing into the existing Compose dash via a derived presentation state, preserving safety/accuracy copy.

## Task Commits

Each task was committed atomically:

1. **Task 1: Establish lap domain models** - `3cef8d2` (feat)
2. **Task 2: Local projection and segment geometry** - `f0907e9` (feat)
3. **Task 3: Clean-room crossing detector** - `c4eddef` (feat)
4. **Task 4: Lap engine state machine and filters** - `6d804be` (feat)
5. **Task 5: Replay runner and synthetic fixtures** - `144341b` (feat)
6. **Task 6: Integrate lap timing into mounted-phone dash** - `5f2a47e` (feat)
7. **Task 7: Update docs and verification artifacts** - `0adc5f3` (docs)

**Plan metadata:** _(final docs commit appended after this summary)_

## Files Created/Modified

### Created (shared lap engine, commonMain)
- `lap/Geometry.kt` - `GeoPoint` / `LocalPoint` primitives.
- `lap/TimingLines.kt` - `StartFinishLine`, `SectorLine`, `CourseDefinition`.
- `lap/LapEngineConfig.kt` - filter tuning with explicit defaults and a lenient test variant.
- `lap/LapModels.kt` - `LapEvent`, `SectorEvent`, sector/lap timing state, `LapRejectReason`, `LapPhase`.
- `lap/LocalProjection.kt` - reversible equirectangular meter projection around a session origin.
- `lap/SegmentGeometry.kt` - cross product, segment intersection, interpolation ratio/timestamp, heading.
- `lap/CrossingDetector.kt` - stateless detector + `CrossingCandidate` + `MovementSegment`.
- `lap/LapEngine.kt` - deterministic state machine applying all five filters and sector logic.
- `lap/ReplayRunner.kt` - headless replay of a fixed sample list with per-step state capture.
- `lap/ReplayFixtures.kt` - demo course + synthetic fixtures (single/one-lap/multi-lap/jitter/low-freq/wrong-direction/poor-accuracy/sector).
- `lap/LapDashState.kt` - presentation model + shared `M:SS.mmm` formatting + sector summaries.
- `lap/DemoLapSession.kt` - headless demo driver mapping engine output to dash state.

### Created (tests, commonTest)
- `lap/LapModelsTest.kt`, `lap/GeometryTest.kt`, `lap/CrossingDetectorTest.kt`, `lap/LapEngineTest.kt`, `lap/ReplayTest.kt`, `lap/LapDashStateTest.kt`, `lap/LapTestSupport.kt`.

### Modified
- `App.kt` - rebuilt dash to render `LapDashState` (current/last/best lap, lap count, speed, accuracy, sectors) with passive Start/Stop/Reset controls and retained safety copy.
- `README.md` - documented Phase 2 lap engine and replay-backed status.

### Docs
- `02-VERIFICATION.md` - automated checks, test coverage, requirement map, deferred Android/iOS UAT, known limitations.

## Verification

- `.\gradlew.bat :shared:check` - PASS (exit 0). All `androidHostTest` lap engine tests pass. iOS simulator tasks SKIPPED on Windows (expected). Only non-fatal `!!` style warnings.
- `.\gradlew.bat :androidApp:assembleDebug` - PASS (exit 0). APK at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
- Manual Android UAT and iOS UAT are deferred to human verification (see `02-VERIFICATION.md`).

## Deviations from Plan

### Auto-fixed Issues

None affecting production code. The lap engine and geometry compiled and behaved
correctly on first implementation.

**Test-fixture geometry corrections (test code only):**

While building Tasks 4 and 5, several initial test fixtures created spurious
extra crossings because a naive "drive back west" return leg re-crossed the
finite start/finish segment, and one low-frequency fixture's return path crossed
the line at `y = -30` (outside the line's `[-25, 25]` span), so no crossing
registered. These were **test-design errors, not engine bugs** — the engine
correctly detected (or correctly did not detect) every crossing per the geometry.

- **Fix:** Reworked fixtures so return legs loop south of the finite line
  segments, and ensured the closing crossing for each lap (and the first lap's
  opening crossing) is emitted exactly once. Restructured `ReplayFixtures` into a
  reusable `openingCrossing()` + `lapBody()` model so lap N's completing crossing
  is lap N+1's starting crossing (no double counting).
- **Files modified:** `LapEngineTest.kt`, `ReplayFixtures.kt` (both committed in
  their respective task commits `6d804be` and `144341b`).

## Known Stubs

None. All dash fields are wired to live engine output via `DemoLapSession`. The
data source is intentionally a deterministic replay (a planned Phase 2 decision),
not a stub; real GPS providers are explicitly deferred to a later phase.

## Remaining Gaps / Future Work

- Real Android Fused Location Provider and iOS Core Location wiring (later phase).
- Session persistence, review, and export (Phase 3).
- Ghost lap / delta-to-best (Phase 4).
- iOS runtime UAT requires macOS/Xcode; deferred.
- On-device Android UAT (build verified; smoke test deferred to human).

## Self-Check: PASSED

All listed source files and task commits verified present on disk and in git history.
