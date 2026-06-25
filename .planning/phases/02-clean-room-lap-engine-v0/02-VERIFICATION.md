---
phase: 02-clean-room-lap-engine-v0
verified: 2026-06-25T00:00:00Z
status: human_needed
score: 7/7 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Android on-device smoke test of the demo lap session"
    expected: "App launches in portrait; Start Timing advances Current Lap; after a replay re-crossing Last/Best/Laps update; a sector split appears in SECTORS; Stop/Reset clear fields; landscape stays readable; no crash in logcat."
    why_human: "Requires an attached Android device; APK builds but on-device runtime/UX cannot be exercised by static verification or JVM host tests."
  - test: "iOS runtime UAT of the shared dash and demo lap path"
    expected: "iOS app builds and launches on a simulator/device; the shared Compose dash renders; the demo lap timing path advances if the Compose iOS runtime is healthy."
    why_human: "Kotlin/Native iOS test binaries and the iOS app cannot run on Windows; requires macOS/Xcode."
warnings:
  - id: WR-01
    summary: "Direction gate locks out all laps when the first accepted crossing starts exactly on the line (signedSide == 0 -> expected sign 0.0, never matched). Latent; not triggered by demo/test fixtures (opening crossing starts west of the line at x=-15)."
  - id: WR-03/WR-06
    summary: "Any movement segment that yields a start/finish candidate skips handleSectors entirely, so a sector crossed in that same segment (or a quality-rejected start/finish segment) drops its sector split. Not asserted by fixtures; lowFrequencyLap crosses start/finish + both sectors in one segment but the test only checks lapCount."
  - id: WR-02/IN-02
    summary: "directionToleranceDegrees config field and CrossingCandidate.headingDegrees are dead; the direction gate is sign-only. Config/KDoc overstate behavior."
  - id: WR-04
    summary: "LocalProjection.toGeo divides by cos(lat) with no pole guard; non-finite output for a degenerate origin. toGeo is test-sanity API only."
  - id: WR-05
    summary: "ReplayResult.sectorEvents derived from sticky latestSector + distinct(); fragile for event counting but is test-diagnostic code, not engine state."
---

# Phase 2: Clean-Room Lap Engine V0 Verification Report

**Phase Goal:** As a track user, I want LapSight to detect laps from GPS crossing a start/finish line, so that I can see current, last, and best lap timing during a session.
**Verified:** 2026-06-25
**Status:** human_needed
**Re-verification:** No — initial goal-backward verification (prior 02-VERIFICATION.md was the executor's automated-check log, not a goal-backward verdict; overwritten here).

## Goal Achievement

All seven ROADMAP success criteria are observably met in the codebase, backed by 53 passing
lap-engine tests (independently re-run, not trusted from SUMMARY). The shared engine compiles
and runs headless with no Compose/Android/iOS/storage dependency, the Android APK builds, and
the dash renders engine-derived presentation state. Two runtime UAT items (Android device, iOS)
remain human-only, so the phase routes to `human_needed` rather than `passed`.

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can define a start/finish line using two points (or position+heading) | VERIFIED | `TimingLines.kt` `StartFinishLine(pointA, pointB)` finite two-point segment; `CourseDefinition`. V0 locked to two-point form per plan decision (PLAN line 450); the OR is satisfied by the two-point branch. Heading-from-position helper not implemented (documented descope, not a gap). |
| 2 | Lap engine detects segment crossing between consecutive GPS samples | VERIFIED | `SegmentGeometry.intersectMovementWithLine` (segment-vs-segment, not point proximity); `CrossingDetector.detectStartFinish`; `LapEngine.onSample` builds `MovementSegment` from `previous`+`sample`. Tests: `CrossingDetectorTest` (W→E, E→W, low-freq line-between-points, near-miss null), `GeometryTest` clear/no/parallel/touching cases. |
| 3 | Crossing timestamp interpolated and stable under replay | VERIFIED | `SegmentGeometry.interpolateTimestamp(start,end,ratio)` with out-of-range rejection; ratio clamped in `intersectMovementWithLine`. Determinism: `ReplayTest.replayIsDeterministic` asserts identical `finalState` across two runs; `interpolateTimestampMidpoint` asserts exact 1500ms. |
| 4 | False positives reduced by min lap time, speed, direction, cooldown, accuracy filters | VERIFIED (with warnings) | `LapEngineConfig` 5 filters with defaults (8s/3s/25m/2.0mps/dir); `LapEngine.qualityReject` + `completeLap` gates. Tests: `cooldownBlocksDuplicateCrossing`, `minLapDurationBlocksFalseLaps`, `accuracyThresholdBlocksPoorSamples`, `speedThresholdBlocksStationarySamples`, `directionGateBlocksWrongWayCrossing`, `jitterDoesNotCreateLaps`. Caveat: WR-01 (sign-0 lockout) and WR-02 (heading tolerance is dead) are real latent defects in untested paths. |
| 5 | Live dash shows current/last/best lap, lap count, and speed | VERIFIED | `App.kt` `LapMetricsPanel` renders `currentLapLabel/lastLapLabel/bestLapLabel/lapCountLabel/speedKmhLabel` from `LapDashState`; data flows `DemoLapSession.tick` → `engine.onSample` → `LapDashState.from`. `LapDashStateTest.demoSessionAdvancesAndCompletesLaps` asserts lapCount=3, best/last non-null. |
| 6 | Live dash shows compact sector split info from sector-line detection | VERIFIED (with warnings) | `App.kt` `SectorReadout`/`SectorChip` render `sectorSummaries` + latest split; `LapEngine.handleSectors`/`handleSectorCrossing` with per-lap reset and dedup. Tests: `LapDashStateTest.demoSessionExposesSectorSummaries` (S1,S2), `LapEngineTest` sector split/before-start/duplicate/reset, `ReplayTest.multiSectorFixtureProducesPerLapSplits`. Caveat: WR-03/WR-06 — sectors are silently skipped on any segment that also yields a start/finish candidate; unasserted by fixtures. Demo/multi-lap paths cross sectors on separate segments, so the demo dash does show real splits. |
| 7 | Engine tests cover geometry, crossing, interpolation, filters, sector detection, replay fixtures | VERIFIED | 53 lap tests across `GeometryTest`(13), `CrossingDetectorTest`(7), `LapEngineTest`(13), `LapModelsTest`(5), `ReplayTest`(10), `LapDashStateTest`(5); re-run independently — 0 failures, 0 errors. |

**Score:** 7/7 truths verified (criteria 4 and 6 carry advisory warnings, not blockers).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `lap/Geometry.kt` | GeoPoint/LocalPoint primitives | VERIFIED | Pure data, no platform deps. |
| `lap/TimingLines.kt` | StartFinishLine/SectorLine/CourseDefinition | VERIFIED | Two-point finite segments; ordered sectors. |
| `lap/LapEngineConfig.kt` | 5 filters + defaults + lenient test variant | VERIFIED | Defaults asserted in `LapModelsTest`; init validation present. |
| `lap/LapModels.kt` | LapEvent/SectorEvent/timing state/reject reasons/phase | VERIFIED | All reject reasons enumerated and surfaced in state. |
| `lap/LocalProjection.kt` | reversible equirectangular meter projection | VERIFIED | Round-trip tested; WR-04 pole guard missing (warning). |
| `lap/SegmentGeometry.kt` | cross/intersect/interpolate/heading | VERIFIED | Edge cases tested; EPSILON parallel guard. |
| `lap/CrossingDetector.kt` | stateless detector + candidate + segment | VERIFIED | Statelessness asserted; shared by S/F and sectors. |
| `lap/LapEngine.kt` | deterministic state machine + filters + sectors | VERIFIED | Wired; WR-01/WR-03/WR-06 latent defects noted. |
| `lap/ReplayRunner.kt` | headless replay with per-step capture | VERIFIED | No UI/platform deps. |
| `lap/ReplayFixtures.kt` | synthetic fixtures (all scenarios) | VERIFIED | single/one-lap/multi-lap/jitter/low-freq/wrong-dir/poor-acc/sector fixtures. |
| `lap/LapDashState.kt` | presentation model + formatting + sector summaries | VERIFIED | `M:SS.mmm` formatting tested. |
| `lap/DemoLapSession.kt` | headless demo driver | VERIFIED | Maps engine output to dash state; lifecycle tested. |
| `App.kt` | dash renders LapDashState | VERIFIED | Renders all metrics + sectors; safety copy retained. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `App.kt` | `DemoLapSession` | `remember { DemoLapSession() }`, `session.tick()` on timer | WIRED | `LaunchedEffect` advances replay; dash state updated. |
| `DemoLapSession` | `LapEngine` | `engine.onSample(sample)` per tick | WIRED | Returns `LapTimingState`. |
| `LapEngine` | `CrossingDetector`/`SegmentGeometry` | `detectStartFinish`/`detectSector` | WIRED | Geometry primitive shared. |
| `DemoLapSession` | `LapDashState` | `LapDashState.from(...)` | WIRED | Engine timing → presentation labels. |
| `App.kt` LapMetricsPanel/SectorReadout | `LapDashState` fields | label getters + sectorSummaries | WIRED | All five lap fields + sector chips rendered. |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `App.kt` dash | `dash: LapDashState` | `session.tick()` → `engine.onSample` over `ReplayFixtures.multiLapLoop` | Yes — deterministic 3-lap replay drives real lapCount/best/last/sector splits | FLOWING (replay-backed by design; real GPS deferred) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Shared engine tests pass | `gradlew :shared:check` | BUILD SUCCESSFUL (exit 0) | PASS |
| Lap host tests execute | `gradlew :shared:cleanTestAndroidHostTest :shared:testAndroidHostTest` | BUILD SUCCESSFUL; 53 lap tests, 0 failures/0 errors (from test-results XML) | PASS |
| Android APK builds | `gradlew :androidApp:assembleDebug` | BUILD SUCCESSFUL; `androidApp-debug.apk` (12.4 MB) present | PASS |
| iOS native tests | `linkDebugTestIosSimulatorArm64` / `iosSimulatorArm64Test` | SKIPPED on Windows (expected) | SKIP (human/iOS UAT) |

### Requirements Coverage

All 10 PLAN-declared requirement IDs cross-referenced against REQUIREMENTS.md (all mapped to Phase 2 and marked Complete there). No orphaned Phase-2 requirements in REQUIREMENTS.md beyond this set.

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| GPS-05 | 02 | Replay a recorded session through the lap engine | SATISFIED | `ReplayRunner`/`ReplayFixtures`; `ReplayTest`. |
| LAP-01 | 02 | Define start/finish via two points or position+heading | SATISFIED | `StartFinishLine` two-point (V0 locked); position+heading descoped per plan. |
| LAP-02 | 02 | Detect start/finish crossing from consecutive samples | SATISFIED | `CrossingDetector` + `SegmentGeometry`; `CrossingDetectorTest`. |
| LAP-03 | 02 | Estimate crossing time by interpolation | SATISFIED | `SegmentGeometry.interpolateTimestamp`; `GeometryTest`. |
| LAP-04 | 02 | Prevent false laps (direction, min lap, speed, cooldown, accuracy) | SATISFIED | `LapEngineConfig`/`LapEngine`; 6 filter tests. (WR-01/WR-02 latent warnings.) |
| LAP-05 | 02 | See current/last/best lap, lap count, speed live | SATISFIED | `LapDashState` + `App.kt`; `LapDashStateTest`. |
| LAP-06 | 02 | Engine runs from fixtures without UI/platform services | SATISFIED | `ReplayRunner` headless; `ReplayTest`. |
| LAP-07 | 02 | Configure sectors, see splits, replay sector crossings | SATISFIED | Sector model/detection/per-lap splits/dash + replay; sector tests. (WR-03/WR-06 warnings.) |
| SAFE-04 | 02 | Expose GPS accuracy limits, no pro-grade implication | SATISFIED | `App.kt` header "not pro-grade timing"; control copy "deterministic replay"; README accuracy/closed-course copy. |
| ARCH-02 | 02 | Tests for geometry, crossing, interpolation, filters, replay | SATISFIED | 53 lap tests across all six categories, passing. |

Note: ARCH-01 (shared engine has no platform UI deps) is mapped to Phase 1 in REQUIREMENTS.md, not in this phase's requirement set, so it is out of scope for this verification. Observation: the engine package (`lap/*`) is in fact free of Compose/Android imports; only `App.kt` imports Compose, consistent with ARCH-01 intent.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `LapEngine.kt` | 88-93 | Start/finish branch skips `handleSectors` (else-only) | Warning | WR-03/WR-06 — sector split dropped on combined segments; unasserted. |
| `LapEngine.kt` | 113-114,254-257 | `signOf(0.0)` learned sign locks out laps | Warning | WR-01 — latent; not hit by fixtures. |
| `LapEngineConfig.kt` | 19-21,30 | `directionToleranceDegrees` + `headingDegrees` dead | Warning | WR-02/IN-02 — config/KDoc overstate behavior. |
| `LocalProjection.kt` | 24,33-36 | `cos(lat)` divisor, no pole guard | Warning | WR-04 — test-sanity API; degenerate origin → NaN. |
| `ReplayRunner.kt` | 21-24 | sticky `latestSector` + `distinct()` | Warning | WR-05 — diagnostic code only. |

No debt markers (TBD/FIXME/XXX/TODO) found in modified source. No stubs: all dash fields wired to live engine output (data source is a deterministic replay by design, not a placeholder). No console-log-only handlers; UI controls call real session methods.

### Human Verification Required

#### 1. Android on-device smoke test
**Test:** Install `androidApp-debug.apk`; launch; Start Timing; observe Current Lap advancing; after replay re-crosses start/finish observe Last/Best/Laps update; confirm a sector split appears in SECTORS; Stop then Reset; rotate to landscape and recheck readability; check logcat for crashes.
**Expected:** Fields update from deterministic replay without real GPS; portrait and landscape both readable; safety copy visible; no crash.
**Why human:** Requires an attached Android device; static checks and JVM host tests cannot exercise on-device runtime/UX.

#### 2. iOS runtime UAT
**Test:** Build and launch the iOS app on macOS/Xcode; confirm the shared dash renders and the demo lap path advances.
**Expected:** Shared Compose dash renders; demo lap timing advances if the Compose iOS runtime is healthy.
**Why human:** Kotlin/Native iOS binaries and the iOS app cannot run on Windows; iosSimulatorArm64Test is correctly SKIPPED here.

### Gaps Summary

No blocking gaps. All 7 ROADMAP success criteria and all 10 requirement IDs are satisfied with
independently re-run automated evidence (53/53 lap tests pass, `:shared:check` and
`:androidApp:assembleDebug` both BUILD SUCCESSFUL). The phase goal — detecting laps from GPS
crossing a start/finish line and showing current/last/best timing during a session — is achieved
on the demo/replay path that the product slice targets.

Five code-review warnings (WR-01..WR-06) are confirmed present in the source. They are latent
defects in code paths that current fixtures do not assert (sign-0 direction lockout; sector drop
on combined start/finish+sector segments; dead heading-tolerance config; unguarded pole division;
fragile replay event derivation). They do not break the tested demo path, so they are advisory
rather than blocking — but criteria 4 and 6 are "met on the demo path" rather than "robust across
all inputs," and these warrant follow-up hardening in a later phase or a focused fix.

Status is `human_needed` (not `passed`) solely because two runtime UAT items (Android device, iOS)
cannot be verified without devices/macOS. Automated verification is otherwise complete and green.

---

_Verified: 2026-06-25_
_Verifier: Claude (gsd-verifier)_
