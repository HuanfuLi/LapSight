---
phase: 02-clean-room-lap-engine-v0
reviewed: 2026-06-25T00:00:00Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/DemoLapSession.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/Geometry.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapDashState.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngineConfig.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapModels.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LocalProjection.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayFixtures.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayRunner.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/SegmentGeometry.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetectorTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/GeometryTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/LapDashStateTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/LapEngineTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/LapModelsTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/LapTestSupport.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/ReplayTest.kt
findings:
  critical: 0
  warning: 6
  info: 5
  total: 11
status: issues_found
---

# Phase 2: Code Review Report

**Reviewed:** 2026-06-25T00:00:00Z
**Depth:** standard
**Files Reviewed:** 21
**Status:** issues_found

## Summary

Reviewed the clean-room KMP lap engine: geometry/projection, segment-intersection
crossing detection, the deterministic state machine with filters, replay fixtures,
and the Compose dash. The numeric core (`SegmentGeometry`, `LocalProjection`) is
sound on the common paths and has good test coverage of the documented edge cases.
The state machine handles duplicate-lap suppression, sector dedup, and lap reset
correctly for the tested scenarios.

No BLOCKER-level defects were found: there is no platform leakage into commonMain
(Compose Multiplatform is a legitimate commonMain dependency per `shared/build.gradle.kts`),
no injection/secret/crypto surface, and no crash on the exercised inputs.

However several real correctness and quality defects exist that are not exercised
by the current tests:

- The direction gate degenerates incorrectly when the learned crossing side is
  exactly zero (movement starts on the line), silently rejecting all later laps.
- The advertised heading-based direction tolerance (`directionToleranceDegrees`,
  `CrossingCandidate.headingDegrees`) is entirely dead — the gate is sign-only —
  so the config and KDoc misrepresent behavior.
- A movement segment that crosses both a sector line and start/finish in the same
  step silently drops the sector split.
- `LocalProjection.toGeo` divides by `cos(lat)` with no guard near the poles.
- `ReplayResult.sectorEvents` derivation can drop or misattribute events because
  it depends on `latestSector` persistence plus `distinct()`.

Details below.

## Warnings

### WR-01: Direction gate silently rejects all laps when the learned side is exactly zero

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt:113-114,254-257,274-278`
**Issue:** `startFirstLap` records `expectedStartFinishSign = signOf(candidate.signedSide)`.
`signedSide` is `cross(lineA, lineB, moveStart)`, which is exactly `0.0` when the
first movement *starts on the line* (the documented "movement starting on the line"
case from `SegmentGeometry`, ratio ~0). `signOf(0.0)` returns `0.0`, so the expected
sign becomes `0.0`. Thereafter `directionMatches` requires
`signOf(candidate.signedSide) == 0.0`, which is false for every normal crossing
(sign `+1`/`-1`). Result: the engine learns a start, then rejects every subsequent
start/finish crossing with `WrongDirection`, so no lap ever completes — a silent
data-loss-of-laps condition that only manifests with default `enforceDirection = true`.
**Fix:** Treat a zero learned sign as "unknown" and fall back to the first non-zero
crossing, or skip the gate until a non-zero sign is observed:
```kotlin
private fun startFirstLap(candidate: CrossingCandidate) {
    val sign = signOf(candidate.signedSide)
    expectedStartFinishSign = if (sign != 0.0) sign else null // re-learn later
    ...
}

private fun directionMatches(candidate: CrossingCandidate): Boolean {
    val expected = expectedStartFinishSign ?: run {
        // not yet learned; adopt this crossing's sign if non-zero
        val s = signOf(candidate.signedSide)
        if (s != 0.0) expectedStartFinishSign = s
        return true
    }
    return signOf(candidate.signedSide) == expected
}
```

### WR-02: Advertised heading-based direction tolerance is dead code; gate is sign-only

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngineConfig.kt:19-21,30,37-39`; `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt:23,34,102`
**Issue:** `LapEngineConfig.directionToleranceDegrees` is documented as "half-angle
tolerance for the direction gate ... accepted only if the movement heading is within
this many degrees of the line's expected crossing direction," and
`CrossingCandidate.headingDegrees` is computed for every candidate. Neither is ever
read by `LapEngine` (`directionMatches` only compares `signOf(signedSide)`). The gate
is purely a half-plane sign test, so a crossing that traverses the line in the correct
half-plane but at a grazing/near-parallel heading is accepted regardless of
`directionToleranceDegrees`. This makes the config field and KDoc misleading and the
`headingDegrees` computation dead. Risk: callers tune `directionToleranceDegrees`
expecting an effect that does not exist.
**Fix:** Either implement the heading tolerance the docs promise (compare
`candidate.headingDegrees` against an expected crossing heading within
`config.directionToleranceDegrees`), or remove `directionToleranceDegrees` and the
unused `headingDegrees` field and rewrite the KDoc to describe the actual sign-based
half-plane gate.

### WR-03: Sector split dropped when one movement crosses both a sector and start/finish

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt:86-93`
**Issue:** `onSample` checks start/finish first and, on any start/finish candidate,
takes the `if` branch and never calls `handleSectors`. With low-frequency GPS
(explicitly a supported case — see `ReplayFixtures.lowFrequencyLap` and
`lowFrequencySamplesStillDetectLaps`), a single 50–150 m movement segment can cross
a sector line *and* the start/finish line. When that happens on the **opening**
segment (start of lap 1) or any segment where start/finish is detected, the sector
crossing for that segment is silently discarded. The KDoc claims ordering "so a
crossing that both finishes and would re-detect a sector is handled in the correct
order," but the implementation does not record the sector at all in that step.
**Fix:** Evaluate sectors regardless of a start/finish hit, ordering by interpolated
crossing time so a sector crossed *before* the line in the same segment is attributed
to the correct (outgoing) lap:
```kotlin
val sfCandidate = det.detectStartFinish(course.startFinish, movement)
// Record sectors crossed earlier in this segment, then handle start/finish.
handleSectors(det, movement, beforeMillis = sfCandidate?.crossingMillis)
if (sfCandidate != null) handleStartFinish(sfCandidate, movement)
```
(At minimum, document the limitation explicitly and add a fixture/test asserting the
dropped-sector behavior so it is intentional.)

### WR-04: `LocalProjection.toGeo` divides by `cos(latitude)` with no pole guard

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LocalProjection.kt:24,33-36`
**Issue:** `metersPerDegLon = METERS_PER_DEGREE * cos(origin.latitude.toRadians())`
is `0.0` at latitude ±90°. `toGeo` then computes `point.x / metersPerDegLon`,
producing `Infinity`/`NaN`. While `toGeo` is "for test sanity checks," it is public
API in commonMain and there is no validation that the origin latitude is in a usable
range; a session origin taken from a bad first GPS fix (e.g. `latitude` of 90 or a
garbage value) silently yields non-finite geometry rather than a clear failure.
**Fix:** Validate the origin (or guard the divisor):
```kotlin
init {
    require(origin.latitude in -89.9..89.9) {
        "origin latitude too close to a pole for equirectangular projection"
    }
}
```
or clamp `cos(...)` away from zero with a documented minimum.

### WR-05: `ReplayResult.sectorEvents` can drop or misattribute sector events

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayRunner.kt:21-24`; depends on `LapEngine` never clearing `latestSector`
**Issue:** `sectorEvents` is derived as `steps.mapNotNull { it.state.latestSector }.distinct()`.
`LapTimingState.latestSector` is sticky: `LapEngine` only overwrites it on a new
accepted sector crossing and never clears it on lap completion (`completeLap` does not
reset `latestSector`). Consequences:
1. The same `SectorEvent` is emitted on every subsequent step until the next sector
   overwrites it; `distinct()` collapses consecutive repeats but this is fragile.
2. If two different sectors produce structurally-distinct events, fine — but if a
   later lap reproduces an identical `SectorEvent` value (same lapNumber/sectorId/
   splitMillis/crossingMillis is unlikely, but identical objects from re-emission are
   common), `distinct()` over the whole list can still silently merge them, making the
   count of observed sector events unreliable for assertions.
This is test/diagnostic-support code (used by `ReplayTest`), so it does not corrupt
engine state, but tests that count sector events can pass or fail for the wrong reason.
**Fix:** Emit events from a dedicated per-step delta rather than the sticky field. For
example, track the previous step's `latestSector` reference and only collect it when it
changes identity:
```kotlin
val sectorEvents: List<SectorEvent>
    get() = steps.mapNotNull { it.state.latestSector }
        .zipWithNext { a, b -> if (a !== b) b else null } // collect only on change
        .filterNotNull()
        .let { listOfNotNull(steps.firstNotNullOfOrNull { it.state.latestSector }?.takeIf { it === it }) + it }
```
or, cleaner, have `LapEngine` expose discrete sector events and accumulate them in
`ReplayResult` directly instead of inferring from a sticky state field.

### WR-06: `qualityReject` rejects a start/finish crossing but suppresses sector evaluation for the same segment

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt:88-93,100-105`
**Issue:** When `detectStartFinish` returns a candidate, `handleStartFinish` runs; if
`qualityReject(movement)` rejects it (poor accuracy / too slow), the method returns and
`handleSectors` is never called for that segment (the `else` branch was not taken). A
sector crossed in that same poor-quality segment is therefore neither recorded nor
explicitly marked rejected. Combined with WR-03 this means sector handling is entirely
skipped on any segment that produces a start/finish candidate, accepted or rejected.
**Fix:** Decouple sector evaluation from the start/finish branch (see WR-03). Sectors
should be evaluated and quality-gated on their own each segment, independent of whether
start/finish fired.

## Info

### IN-01: `MovementSegment.effectiveSpeed` falls back to end-sample speed, not segment-derived speed

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt:117-132`; `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt:83`
**Issue:** The engine always supplies `speedMetersPerSecond = sample.speedMetersPerSecond`
(the end sample's reported speed), so `effectiveSpeed()`'s geometry-based fallback
(distance/dt) is never reached when the provider reports any speed — including a reported
`0.0` while the vehicle is actually moving across the line, which would trip the `TooSlow`
gate. The KDoc says speed "defaults to the segment distance over the time delta if not
supplied," which only happens when the provider passes `null`. Consider deriving speed
from geometry when the reported speed is implausibly low relative to displacement, or
clarify the KDoc.
**Fix:** Document the actual precedence, or use `max(reportedSpeed, geometrySpeed)` for
the gate.

### IN-02: `headingDegrees` computed for every candidate but never consumed

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt:102`
**Issue:** `SegmentGeometry.headingDegrees(...)` is invoked for every candidate and stored,
but no engine or dash logic reads `CrossingCandidate.headingDegrees`. Dead computation/field
(see WR-02). Remove if the heading gate is not implemented.
**Fix:** Drop the field or wire it into the direction gate.

### IN-03: Magic numbers in replay fixtures and dash formatting

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayFixtures.kt:85`; `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapDashState.kt:35,99-105`
**Issue:** `lapMillis / 8` (segment step count), `* 3.6` (m/s→km/h), and the `60_000`/`1_000`
time divisors are unnamed literals. Low impact but reduces readability/maintainability.
**Fix:** Extract named constants (e.g. `LAP_BODY_SEGMENTS = 8`, `MS_PER_KMH = 3.6`).

### IN-04: `lapMillis / 8` integer division can desync fixture timestamps

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayFixtures.kt:85-99`
**Issue:** `step = lapMillis / 8` is `Long` integer division. For `lapMillis` not divisible
by 8 (e.g. `34_000` used by `multiLapMultiSector`), `7 * step` does not equal
`lapMillis - step`, so the spacing between the last body sample (`7*step`) and the closing
crossing (`lapMillis`) differs from the others. Fixtures still function, but the implied
even spacing is not actually even, which could surprise future fixture edits/assertions.
**Fix:** Either require divisible durations, or compute the closing offset relative to
`8 * step` consistently.

### IN-05: `CourseDefinition.orderedSectors` recomputed (re-sorts) on every access

**File:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/TimingLines.kt:41-42`; called in `LapEngine.handleSectors`/`resetSectors`
**Issue:** `orderedSectors` is a `get()` that sorts on every call; `handleSectors` accesses
it for each movement segment. Not a v1 performance concern (sector counts are tiny), but it
also does not de-duplicate sectors sharing the same `order`, so two sectors with equal
`order` have an unspecified relative position. Worth a `val` cache and/or an `init`
uniqueness check on sector `id`/`order`.
**Fix:** Cache the sorted list in a `val` and validate unique sector ids/orders in
`CourseDefinition`'s `init`.

---

_Reviewed: 2026-06-25T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
