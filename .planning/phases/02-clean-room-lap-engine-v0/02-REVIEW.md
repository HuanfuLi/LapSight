---
phase: 02-clean-room-lap-engine-v0
reviewed: 2026-06-25T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngine.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LapEngineConfig.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/CrossingDetector.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/LocalProjection.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/ReplayRunner.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/lap/SegmentGeometry.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/LapEngineTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/GeometryTest.kt
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/lap/ReplayTest.kt
findings:
  critical: 0
  warning: 0
  info: 2
  total: 2
status: resolved
---

# Phase 2: Code Review Report (final, after commit 622e777)

**Reviewed:** 2026-06-25
**Depth:** standard
**Files Reviewed:** 9
**Status:** resolved

## Summary

Audit trail across three review passes on the Phase 2 lap engine:

1. **Initial review** (commit range 3cef8d2..b48db05) — 0 blockers, 6 warnings, 5 info.
2. **Hardening** (commit `4341e68`) — fixed all 6 original warnings, but introduced
   a fragile heading-tolerance direction gate (re-review found new warnings).
3. **Simplification** (commit `622e777`) — replaced the heading gate with the robust
   sign-only direction gate, resolving the regressions.

All original and newly-introduced WARNING-level findings are now resolved. Two
INFO-level items remain, both consciously accepted/deferred for V0 (see below).
No BLOCKER-level defects at any point. Backed by 56 passing shared host tests
(iOS simulator tests skipped on Windows, expected).

## Resolved findings

### Original warnings (raised pre-hardening)

- **WR-01 (direction-gate sign-0 lockout): RESOLVED** in `4341e68`. `learnDirection`
  adopts only a non-zero approach side, and `directionMatches` skips the side check
  when the candidate sits exactly on the line. Test: `firstCrossingExactlyOnLineDoesNotLockDirectionGate`.
- **WR-02 / IN-02 (dead `directionToleranceDegrees`): RESOLVED** in `622e777` by
  **removal**. The heading-tolerance gate proved fragile (see new-WR-03); the field
  was deleted and the direction gate is now a documented sign-based half-plane check.
  `CrossingCandidate.headingDegrees` remains as diagnostic geometry (like
  `crossingPoint`/`ratio`), still computed by the detector.
- **WR-03 / WR-06 (sector split dropped on a shared segment): RESOLVED** in `4341e68`.
  `onSample` collects every crossing on a segment and applies them in crossing-time
  (then ratio) order. Test: `singleSegmentCrossingStartFinishAndSectorRecordsBoth`.
- **WR-04 (toGeo near-pole division): RESOLVED** in `4341e68`. `toGeo` guards the
  longitude division. Test: `toGeoNearPoleStaysFinite`.
- **WR-05 (fragile `distinct()` in `sectorEvents`): RESOLVED** in `4341e68` via
  change-detection against the previous step's `latestSector`.

### Regressions introduced by hardening (raised in re-review, fixed in 622e777)

- **new-WR-02 (learn-before-reject ordering): RESOLVED.** `learnDirection` now runs
  only after the direction, cooldown, and min-lap gates all pass, so the expected
  direction is never anchored to a rejected crossing.
- **new-WR-03 (heading-gate over-rejection on low-frequency GPS): RESOLVED.** The
  raw-chord heading gate was removed entirely; the sign-based half-plane gate is
  robust to heading noise.
- **new-WR-04 (same-millisecond crossing tie ordering): RESOLVED.** Crossings are
  ordered by `(crossingMillis, ratio)`, so a sector reached before the line is
  applied to the outgoing lap rather than the new one on a timestamp tie.
- **new-WR-01 (multi-crossing `lastRejectReason` order-dependence): RESOLVED to
  deterministic.** Processing order is now deterministic via the `(crossingMillis,
  ratio)` sort. Per-sector reasons are already tracked separately in
  `SectorTimingState.lastRejectReason`; the top-level `lastRejectReason` reflecting
  the last-applied crossing on a multi-crossing segment is accepted as diagnostic
  behavior.
- **IN-01 / IN-02 (heading re-learn / heading-half no-op): MOOT.** Both concerned the
  heading gate, which no longer exists.

## Info (accepted / deferred for V0)

### IN-03: `CourseDefinition.orderedSectors` re-sorts on each access and does not validate unique sector id/order
**File:** `LapEngine.kt` (per-segment loop), `TimingLines.kt:41-42`
Tiny sector counts make the re-sort a non-issue for V0. Uniqueness of sector
`id`/`order` is currently assumed, not enforced. Deferred: cache the sorted list and
validate uniqueness in `CourseDefinition.init` in a later phase if sectors grow.

### IN-04: `SegmentGeometry.headingDegrees` is now used only to populate diagnostic candidate data
**File:** `SegmentGeometry.kt:114`, `CrossingDetector.kt:102`
With the heading gate removed, this primitive feeds only `CrossingCandidate.headingDegrees`
(diagnostic) and its own unit tests. Retained intentionally as a documented geometry
primitive for future heading/UI use; no action required.

---

_Final review reconciled by orchestrator against commits 4341e68 and 622e777; all
WARNING findings verified resolved with passing test coverage._
_Depth: standard_
