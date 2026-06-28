---
phase: 05-track-setup-and-course-profiles
plan: 13
subsystem: session
tags: [kotlin-multiplatform, preflight, course-profiles, timing, compose, replay, tdd]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 12
    provides: Direction-relative course matching with exact compatibility-key and timing continuity
  - phase: 05-track-setup-and-course-profiles
    plan: 5
    provides: Bounded ClosedReferencePath geometry and whole-loop projection
provides:
  - Conservative whole-course Ready/Blocked/Unavailable preflight
  - Explicit pre-Timing wrong-course override with persisted evidence
  - Selection-to-preflight-to-save deterministic integration replay
  - Closed-course/private-track safety copy outside the moving timing surface
affects: [05-14-review-override-evidence, timing-session-review, phase-5-uat]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wrong-course preflight is a pure bounded decision over ClosedReferencePath and never enters LapEngine."
    - "A clearly-far start captures its exact profile/revision/direction context once; explicit override consumes that context through normal recorder construction."
    - "Preflight evidence is additive TimingSession metadata and cannot invalidate active timing."

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/WrongCoursePreflight.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/WrongCoursePreflightTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/CourseProfileIntegrationTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibrary.kt

key-decisions:
  - "Only a trustworthy fix whose accuracy-adjusted whole-course distance exceeds 250 m produces a wrong-course block; missing, stale, poor, or malformed evidence remains non-blocking Unavailable."
  - "The override consumes the already-resolved profile revision, direction, source, course, and blocked result without rerunning preflight."
  - "CourseCompatibilityKey(profileId, geometryCompatibilityId, direction, isSimulated) and matcher behavior remain unchanged."

patterns-established:
  - "Pre-Timing evidence: Ready, Unavailable, and explicit Overridden snapshots persist independently from lap and Ghost validity."
  - "Safety boundary: selection and override controls stay off the active fullscreen timing dash."

requirements-completed: [SC-01, SC-02, SC-03, SC-04]

# Metrics
duration: 13min
completed: 2026-06-28
---

# Phase 5 Plan 13: Wrong-Course Preflight and Override Summary

**Added accuracy-aware whole-course preflight and an explicit saved-session override path while preserving exact Ghost compatibility, matcher recovery, and uninterrupted active timing.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-06-28T01:36:50Z
- **Completed:** 2026-06-28T01:49:48Z
- **Tasks:** 2 TDD tasks
- **Files modified:** 7

## Accomplishments

- Added bounded preflight over every closed reference segment, including last-to-first, with 250 m block distance, 100 m accuracy limit, 15-second freshness, and uncertainty subtraction.
- Added deterministic pit/paddock, far-course, accuracy-margin, stale, malformed, non-finite, and oversized geometry coverage.
- Added a typed pre-Timing block with the exact `Still use this track` action; override persists an immutable `CoursePreflightSnapshot` into draft and saved session data.
- Proved selection, direction, block, override, samples, laps, complete Sectors, Ghost suppression/rematch, save, and selection/session reload through the normal controller/recorder/store path.
- Kept the moving fullscreen timing surface passive and made closed-course/private-track and no-public-road-racing language explicit.

## Task Commits

1. **Task 1 RED: Conservative preflight behavior gate** - `703528a` (test)
2. **Task 1 GREEN: Whole-course preflight and snapshot contract** - `b13b5b3` (feat)
3. **Task 2 RED: Override vertical integration gate** - `e081990` (test)
4. **Task 2 GREEN: Preflight block and normal Timing override** - `731308b` (feat)

## Files Created/Modified

- `session/WrongCoursePreflight.kt` - Pure bounded thresholds, typed results, and whole-course decision.
- `session/SessionModels.kt` - Serializable preflight snapshot and typed wrong-course start result.
- `session/SessionController.kt` - Exact-profile preflight, captured blocked start, override, and persistence.
- `ui/DriveScreen.kt` - Stationary override dialog and explicit private-track safety copy.
- `fixtures/GpsFixtureLibrary.kt` - Deterministic near/far/stale/malformed preflight fixtures.
- `session/WrongCoursePreflightTest.kt` - Whole-loop, uncertainty, quality, geometry, and snapshot tests.
- `session/CourseProfileIntegrationTest.kt` - Selection-to-save/reload continuity replay.

## Decisions Made

- Poor or unavailable GPS evidence does not claim the selected course is wrong; only clearly-far, accuracy-adjusted evidence blocks.
- The pending override retains the exact selected revision reference line and compatibility identity so profile/direction state cannot drift between block and start.
- Preflight runs only before recorder construction. After start, raw capture, LapEngine, Sectors, references, and session time remain unconditional; matcher confidence still controls only delta availability.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Preserved non-null start/finish across the extracted start helper**
- **Found during:** Task 2 GREEN compilation
- **Issue:** Moving recorder construction behind the preflight/override seam lost Kotlin's local smart cast for nullable `Track.startFinish`.
- **Fix:** Captured the validated start/finish DTO in the resolved pending-start context and reused it for session creation.
- **Files modified:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt`
- **Verification:** Focused integration tests, full `:shared:check`, and Android debug assembly.
- **Committed in:** `731308b`

**2. [Rule 3 - Blocking] Reconciled legacy STATE layout with current GSD handlers**
- **Found during:** Plan close-out
- **Issue:** `state.advance-plan` could not parse this repository's free-form plan fields, `state.update-progress` found no body progress bar, and session sync temporarily wrote an incorrect 50% frontmatter value.
- **Fix:** Retained the successful SDK roadmap 13/14 update, used supported named arguments for metrics/session tracking, and explicitly reconciled STATE to 27/28 plans (96%) and Phase 5 at 13/14.
- **Files modified:** `.planning/STATE.md`, `.planning/ROADMAP.md`
- **Verification:** Roadmap remains `13/14 plans executed`, Phase 5 remains `In Progress`, and STATE points to 05-14.
- **Committed in:** plan metadata commit

---

**Total deviations:** 2 auto-fixed (2 Rule 3 blocking issues).
**Impact on plan:** Both fixes preserve required implementation and workflow invariants without changing schema identity, matcher behavior, or scope.

## Issues Encountered

- iOS simulator tests are unavailable on Windows; `:shared:check` compiled iOS main/test sources and skipped simulator execution as expected.
- Existing unrelated Kotlin warnings in storage, Review, and legacy lap tests remain unchanged.
- The SC-01 through SC-04 identifiers are Phase 5 planning criteria rather than formal REQUIREMENTS.md IDs, so the requirements handler correctly reported them as not found and REQUIREMENTS.md remained unchanged.

## Verification

Passed:

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests "*WrongCoursePreflightTest*"
.\gradlew.bat :shared:testAndroidHostTest --tests "*WrongCoursePreflightTest*" --tests "*CourseProfileIntegrationTest*" --tests "*CourseProgressMatcherTest*"
.\gradlew.bat :shared:check
.\gradlew.bat :androidApp:assembleDebug
```

Static gates passed:

- `LapEngine.kt` contains no preflight, wrong-course, or matcher dependency.
- `CourseCompatibilityKey(profileId, geometryCompatibilityId, direction, isSimulated)` remains unchanged.
- The exact override and closed-course/private-track/no-public-road-racing copy are present only on the stationary pre-Timing flow.
- No package-manager install or new dependency occurred.

## Known Stubs

None. `NotChecked` and `Unavailable` are explicit backward-compatible evidence states, not placeholder UI/data.

## Threat Flags

None new. T-05-28 is covered by bounded geometry and typed quality rejection; T-05-29 is covered by pre-recorder-only preflight and integration replay proving uninterrupted active timing.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 05-14 can render the persisted override evidence in Review and author the Phase 5 UAT gates.
- Phase 5 remains in progress at 13/14 plans and must not be marked complete until 05-14 executes and verification finishes.

## Self-Check: PASSED

- All three created files and four modified production/test files exist.
- All four 05-13 TDD commits are present in git history.
- Focused replay tests, full shared checks, Android debug build, static isolation, and compatibility-key gates pass.
- `.claude/worktrees/agent-ad91f9618b2606cd1` and `.planning/phases/05.1-mvp-field-validation-and-hardening-gate` were not modified.

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-28*
