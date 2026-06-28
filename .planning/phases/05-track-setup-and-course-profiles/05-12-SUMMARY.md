---
phase: 05-track-setup-and-course-profiles
plan: 12
subsystem: ghost
tags: [kotlin-multiplatform, ghost, course-matching, live-delta, replay, tdd]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 11
    provides: Exact CourseCompatibilityKey reference persistence and provider provenance
  - phase: 05-track-setup-and-course-profiles
    plan: 5
    provides: ClosedReferencePath projection, ambiguity data, and centralized geometry thresholds
provides:
  - Pure direction-relative CourseProgressMatcher with typed unmatched results
  - LiveDeltaEngine course-progress matching with stale-value suppression and automatic rematch
  - Deterministic ambiguity, excursion, rematch, backward-motion, and malformed-input fixtures
  - Production-path proof that matcher failure suppresses only Ghost delta
affects: [05-13-wrong-course-preflight, ghost-live-delta, timing-session-replay]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Realtime course matching is pure O(reference point count) Kotlin over ClosedReferencePath and owns no lap clock, UI, platform, or storage state."
    - "Every live sample overwrites delta state; unmatched course position becomes -- and the next confident match resumes without reset."
    - "LapEngine/raw samples/sectors checkpoint before Ghost matching, so matcher confidence cannot pause or invalidate timing."

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/CourseProgressMatcher.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ghost/CourseProgressMatcherTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibrary.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngine.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/ClosedReferencePath.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngineTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/TimingGhostIntegrationTest.kt

key-decisions:
  - "CourseProgressMatcher requires full CourseCompatibilityKey equality and matching provider provenance before accepting progress; 05-10/05-11 key persistence remains unchanged."
  - "Live elapsed time starts with the first lap sample independently of match confidence, so off-course recovery never restarts the delta clock."
  - "Dense segments within the same bounded local arc neighborhood are excluded as ambiguity competitors; true nonlocal parallel/hairpin candidates remain ambiguous."
  - "Legacy Recorded references without a TrackReferenceLine derive a bounded closed path from their exact-key raw lap rather than falling back to traveled-distance accumulation."

patterns-established:
  - "Matcher isolation: raw append and LapEngine/Sector processing are unconditional; only LiveDeltaSnapshot availability depends on CourseMatchResult."
  - "Direction progress: Recorded and Reverse use the existing CourseGeometryBuilder transform over one physical closed path."

requirements-completed: [SC-04]

# Metrics
duration: 20min
completed: 2026-06-28
---

# Phase 5 Plan 12: Course-Progress Ghost Matcher and Recovery Summary

**Replaced traveled-distance Ghost progress with exact-key, direction-relative closed-course matching that suppresses only live delta during ambiguity or excursions and automatically resumes on rematch while timing, sectors, raw capture, and same-session best updates continue.**

## Performance

- **Duration:** 20 min
- **Started:** 2026-06-28T01:10:49Z
- **Completed:** 2026-06-28T01:31:06Z
- **Tasks:** 2 TDD tasks
- **Files modified:** 10

## Accomplishments

- Added `CourseProgressMatcher` with finite/source/key validation, conservative accuracy and lateral-distance gates, nonlocal ambiguity rejection, speed/time continuity, Recorded/Reverse progress transforms, and typed non-throwing failures.
- Replaced `LiveDeltaEngine` traveled-distance accumulation with matched normalized course progress; every unmatched sample clears stale delta to `--`, and a later match resumes against the same exact reference without resetting elapsed time.
- Added deterministic ambiguity, off-course/rematch, backward-motion, incompatible-source/key, malformed-input, and implausible-jump coverage.
- Proved the normal provider → controller → recorder path transitions `Available → -- → Available` while checkpointing every raw sample, continuing elapsed/lap/Sector state, and promoting same-session new bests.
- Preserved full `CourseCompatibilityKey` equality and exact-key persistence from Plans 05-10/05-11; no reference-store API or filename logic changed.

## Task Commits

1. **Task 1 RED: Course matcher recovery gate** - `c0b13af` (test)
2. **Task 1 GREEN: Direction-relative course matching** - `fc7fc30` (feat)
3. **Task 2 RED: Realtime matcher continuity gate** - `390d074` (test)
4. **Task 2 GREEN: Timing-preserving matcher integration** - `c334176` (feat)

## Files Created/Modified

- `ghost/CourseProgressMatcher.kt` - Pure bounded matcher, thresholds, continuity state, and typed match output.
- `ghost/GhostModels.kt` - `CourseMatchResult`, confidence, and unmatched reason contracts.
- `ghost/LiveDeltaEngine.kt` - Course-progress delta interpolation, automatic rematch, and legacy Recorded exact-reference path fallback.
- `fixtures/GpsFixtureLibrary.kt` - Stable ambiguity, recovery, and backward-motion scenarios.
- `session/TimingSessionRecorder.kt` - Builds the exact session-key matcher and evaluates it only after lap/raw/Sector fan-out.
- `session/SessionController.kt` - Passes the selected Track reference line into new and recovered recorders.
- `track/ClosedReferencePath.kt` - Excludes dense local arc neighbors from nonlocal ambiguity competition.
- `ghost/CourseProgressMatcherTest.kt` - Independent matcher and recovery gate.
- `ghost/LiveDeltaEngineTest.kt` - Existing sign/interpolation semantics exercised over matched course position.
- `session/TimingGhostIntegrationTest.kt` - Production-path excursion/rematch and uninterrupted timing/Sector/reference proof.

## Decisions Made

- Kept the `CourseCompatibilityKey(profileId, geometryCompatibilityId, direction, isSimulated)` contract byte-for-byte unchanged and validated equality at the matcher boundary.
- Used normalized matched course progress to query the existing reference progress curve, retaining positive/negative delta sign and interpolation behavior while decoupling current position from traveled distance.
- Kept matcher state out of `LapEngine`; static verification confirms no matcher/confidence dependency enters lap timing.
- Retained safe behavior for legacy Recorded references lacking a persisted reference line by constructing a bounded path from the exact compatible reference's raw samples. Reverse remains fail-closed without explicit recorded-orientation geometry.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Dense reference lines falsely reported local curve segments as ambiguous**
- **Found during:** Task 2 GREEN production-path replay
- **Issue:** `ClosedReferencePath.project` considered segments only two or more indices away as non-adjacent, so a 240-point smooth oval treated nearby pieces of the same local curve as competing candidates and suppressed every delta.
- **Fix:** Excluded a bounded cyclic arc neighborhood around the best segment before evaluating nonlocal runner-up ambiguity; true parallel/hairpin segments remain eligible.
- **Files modified:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/ClosedReferencePath.kt`
- **Verification:** `CourseProgressMatcherTest`, `TimingGhostIntegrationTest`, `CourseDirectionReplayTest`, and full `:shared:check`.
- **Committed in:** `c334176`

**2. [Rule 2 - Missing Critical] Preserved legacy Recorded Ghost operation without traveled-distance fallback**
- **Found during:** Task 2 GREEN regression verification
- **Issue:** Existing saved Tracks may have a valid reference but no `TrackReferenceLine`; removing traveled-distance accumulation would otherwise make their following-lap delta permanently unavailable.
- **Fix:** For legacy Recorded-only references, derive a bounded `ClosedReferencePath` from the exact-key raw reference lap. Reverse remains unavailable unless explicit recorded-orientation course geometry exists.
- **Files modified:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngine.kt`
- **Verification:** Existing `firstLapHasNoReferenceThenFollowingLapUsesNewReference` integration test and full `:shared:check`.
- **Committed in:** `c334176`

**3. [Rule 3 - Blocking] Passed reference geometry through the controller construction seam**
- **Found during:** Task 2 GREEN integration
- **Issue:** `TimingSessionRecorder` had the exact compatibility key but not the selected Track reference line, so it could not construct the required course matcher.
- **Fix:** Added an optional reference-line constructor input and passed the loaded Track geometry for both new and recovered recorder instances.
- **Files modified:** `TimingSessionRecorder.kt`, `SessionController.kt`
- **Verification:** Full production-path timing replay and recovery gate.
- **Committed in:** `c334176`

**4. [Rule 3 - Blocking] Aligned real-run replay provenance with the exact source key**
- **Found during:** Task 2 GREEN regression verification
- **Issue:** One older integration test declared a Phone GPS run but replayed samples tagged Simulated; the new source-integrity gate correctly rejected those samples.
- **Fix:** Copied the deterministic replay samples with `LocationSource.PhoneGps` for that real-run test.
- **Files modified:** `TimingGhostIntegrationTest.kt`
- **Verification:** Focused integration tests and full `:shared:check`.
- **Committed in:** `c334176`

---

**Total deviations:** 4 auto-fixed (1 Rule 1 bug, 1 Rule 2 missing critical compatibility behavior, 2 Rule 3 blocking integration/test issues).
**Impact on plan:** All fixes are required for correct matcher behavior and backward-compatible production integration. No dependency, schema, storage-key, or Phase 5.1 scope change was introduced.

## Issues Encountered

- iOS simulator tests cannot execute on Windows; iOS shared source and test source compilation completed under `:shared:check`.
- Existing unrelated Kotlin warnings in storage, Review, geometry, and lap tests remain out of scope.

## Verification

Passed:

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProgressMatcherTest*" --tests "*LiveDeltaEngineTest*"
.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProgressMatcherTest*" --tests "*TimingGhostIntegrationTest*" --tests "*CourseDirectionReplayTest*"
.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProgressMatcherTest*" --tests "*LiveDeltaEngineTest*" --tests "*TimingGhostIntegrationTest*" --tests "*CourseDirectionReplayTest*"
.\gradlew.bat :shared:check
```

Static gates passed:

- `LapEngine.kt` contains no `CourseMatchResult`, `CourseProgressMatcher`, or matcher-confidence dependency.
- `LocalSessionStore.kt`, `FileSessionStore.kt`, and `InMemorySessionStore.kt` exact-key persistence implementations were unchanged.
- No package-manager install or new dependency occurred.

## Known Stubs

None. Nullable matcher/reference state represents explicit unavailable behavior; test-local nullable capture variables are asserted before use.

## Threat Flags

None new. T-05-25 is mitigated by finite/bounded typed input rejection, T-05-26 by nonlocal ambiguity and continuity gates, and T-05-27 by unconditional lap/raw/Sector fan-out before matcher-controlled delta suppression.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 05-13 can add whole-course preflight and override integration on top of the same `ClosedReferencePath` and typed unavailable behavior.
- Phase 5 remains in progress at 12/14 plans; Plans 05-13 and 05-14 are not complete.

## Self-Check: PASSED

- Created matcher, matcher test, and summary files exist.
- All four TDD task commits are present in git history.
- Targeted replay gates and full `:shared:check` are green.
- Exact-key persistence files and the Phase 5.1 validation-gate directory were not modified.

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-28*
