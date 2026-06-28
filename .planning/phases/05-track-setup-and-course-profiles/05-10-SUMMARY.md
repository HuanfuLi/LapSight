---
phase: 05-track-setup-and-course-profiles
plan: 10
subsystem: ghost
tags: [kotlin-multiplatform, ghost, course-compatibility, tdd, schema-validation]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 9
    provides: Recorded/Reverse CourseDirection snapshot and Reverse ghost suppression
provides:
  - Full CourseCompatibilityKey identity exposed to ghost/reference code
  - ReferenceLap.compatibilityKey with deterministic V1 Recorded migration
  - ReferenceLapSelector.fasterOf key-gated strict-faster comparison
  - GhostCompatibility validation for unsafe IDs, snapshot mismatches, and source-slot mismatch
  - Legacy reference-slot guard until exact-key persistence lands in 05-11
affects: [05-11-exact-key-reference-persistence, 05-12-course-progress-matcher]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Ghost/reference compatibility is full value-object equality over profileId, geometryCompatibilityId, CourseDirection, and isSimulated."
    - "V1 Track-scoped references migrate to a deterministic Recorded-only key: <trackId>:g1 plus the original real/sim source slot."
    - "Legacy V1 reference storage is used only when the session key equals the deterministic legacy Recorded key; non-legacy keys wait for 05-11 exact-key persistence."

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostCompatibility.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ghost/GhostCompatibilityTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ReferenceLapSelector.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaMigrations.kt

key-decisions:
  - "Keep the serialized CourseCompatibilityKey in track/profile DTOs and expose the same value object from ghost via typealias, avoiding churn in prior Phase 5 storage models."
  - "Do not persist non-legacy compatibility keys through the old per-track V1 reference slot; exact-key storage remains Plan 05-11."
  - "Treat V1 references as Recorded-only during migration, preserving their original real/simulated source boundary."

patterns-established:
  - "Reference selection must check compatibility key equality before any duration comparison."
  - "Schema/reference validation rejects unsafe IDs and source-slot mismatches before comparison."

requirements-completed: [SC-03, SC-04]

# Metrics
duration: 9min
completed: 2026-06-28
---

# Phase 5 Plan 10: Ghost Compatibility Key Contract Summary

**Ghost reference selection now fails closed on full course compatibility: profile, geometry compatibility, Recorded/Reverse direction, and Demo/Real source must all match before a faster lap can replace the incumbent.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-06-28T00:33:09Z
- **Completed:** 2026-06-28T00:42:37Z
- **Tasks:** 1 TDD task
- **Files modified:** 8

## Accomplishments

- Added `GhostCompatibility` with deterministic V1 key migration and validation for unsafe IDs, session snapshot mismatches, and source-slot mismatch.
- Added `ReferenceLap.compatibilityKey` and V2 reference payload mapping while keeping V1 payloads mapped to Recorded-only compatibility keys.
- Changed `ReferenceLapSelector.fasterOf` so strict-faster replacement only happens for equal compatibility keys; equal durations still preserve the incumbent.
- Added `GhostCompatibilityTest` covering D-15, D-16, D-18, D-19, V1 Recorded migration, and every key dimension mismatch.
- Propagated the session compatibility key when live and saved sessions build reference laps.

## Task Commits

1. **Task 1 RED: Ghost compatibility gate** - `14e3f4e` (`test`)
2. **Task 1 GREEN: Course compatibility selection** - `14c77c6` (`feat`)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostCompatibility.kt` - compatibility type exposure, deterministic V1 key migration, and validation gate.
- `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ghost/GhostCompatibilityTest.kt` - compatibility collision, migration, validation, and key-gated selection coverage.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostModels.kt` - `ReferenceLap.compatibilityKey`.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ReferenceLapSelector.kt` - key-aware reference creation and key-gated `fasterOf`.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt` - session compatibility snapshot and V1/V2 reference mapping.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt` - completed-lap references inherit the session key.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt` - saved-session references inherit the session key and avoid non-legacy V1 slot reads/writes.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaMigrations.kt` - V2 ghost decode reuses the central compatibility validator.

## Decisions Made

- Kept `CourseCompatibilityKey` serialized with course-profile DTOs and exposed it from the ghost package as the same type. This avoids migrating already-written Phase 5 DTOs while giving ghost code the planned API.
- Blocked non-legacy keys from legacy V1 reference storage. This preserves the plan boundary that exact-key reference persistence is implemented in 05-11, while preventing Reverse/profile-derived data from contaminating old per-track slots.
- V1 references always migrate to Recorded direction with their original source slot; no floating-point geometry hash is introduced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Prevented non-legacy keys from using the legacy reference slot**
- **Found during:** Task 1 GREEN
- **Issue:** Once `ReferenceLap` carried a full key, existing live/save reference builders could still read or write the legacy per-track V1 reference slot for non-legacy keys.
- **Fix:** Added `usesLegacyReferenceSlot` in `SessionController` and passed `TimingSession.courseCompatibilityKey` through `TimingSessionRecorder` and saved-session reference rebuilds.
- **Files modified:** `SessionController.kt`, `TimingSessionRecorder.kt`
- **Verification:** `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest*"` and `.\gradlew.bat :shared:check`
- **Committed in:** `14c77c6`

**2. [Rule 2 - Missing Critical] Reused compatibility validation at the V2 decode boundary**
- **Found during:** Task 1 GREEN
- **Issue:** `SchemaMigrations.validateGhostV2` checked basic fields but did not reject a source flag that disagreed with the key's `isSimulated` slot.
- **Fix:** Routed V2 ghost payload validation through `GhostCompatibility.validateReferencePayload`.
- **Files modified:** `SchemaMigrations.kt`
- **Verification:** `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest*"` and `.\gradlew.bat :shared:check`
- **Committed in:** `14c77c6`

---

**Total deviations:** 2 auto-fixed (2 missing critical validation/guard issues).
**Impact on plan:** Both fixes preserve the intended compatibility boundary without adding new dependencies or implementing 05-11 storage scope early.

## Verification

- RED gate: `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest*"` failed at compile time before implementation because `GhostCompatibility`, `CourseCompatibilityValidation`, and `ReferenceLap.compatibilityKey` did not exist.
- GREEN focused gate: `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest*"` - BUILD SUCCESSFUL.
- Full shared gate: `.\gradlew.bat :shared:check` - BUILD SUCCESSFUL.
- iOS simulator link/run tasks remain skipped on Windows; iOS source compilation completed under `:shared:check`.

## Issues Encountered

- Existing Kotlin warnings in unrelated lap/review/storage tests and source files remain out of scope and were not modified.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. Exact-key reference persistence is intentionally deferred to Plan 05-11; this plan blocks non-legacy keys from using legacy storage rather than pretending exact-key storage exists.

## Threat Flags

None new. T-05-21 is mitigated by full value-object equality and validation before reference comparison. T-05-22 is mitigated by deterministic V1 Recorded-only key migration with preserved source slot.

## Self-Check: PASSED

- Created/modified files verified present on disk, including `05-10-SUMMARY.md`, `GhostCompatibility.kt`, and `GhostCompatibilityTest.kt`.
- Task commits verified in git log: `14e3f4e` (RED test) and `14c77c6` (GREEN implementation).
- Stub scan of touched files found only existing optional defaults/test fixture literals and documented V1 migration defaults; no placeholder UI/data path blocks the plan goal.
- Verification commands are current and passing: `.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostCompatibilityTest*"` and `.\gradlew.bat :shared:check`.

## Next Phase Readiness

Plan 05-11 can replace the legacy per-track reference slot with exact-key persistence using the validated key contract and session/reference mapping from this plan.

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-28*
