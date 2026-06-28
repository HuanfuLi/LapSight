---
phase: 05-track-setup-and-course-profiles
plan: 11
subsystem: ghost-storage
tags: [kotlin-multiplatform, ghost, course-compatibility, storage, provider-provenance, tdd]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 10
    provides: Full CourseCompatibilityKey contract and key-gated reference selection
provides:
  - Exact CourseCompatibilityKey reference persistence in file and in-memory stores
  - Provider-provenance-owned timing source for reference keys
  - Payload/request key validation before V2 reference loads return
  - Path-safe encoded V2 reference filenames including geometry compatibility
affects: [05-12-course-progress-matcher, 05-13-wrong-course-preflight, ghost-reference-storage]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Runtime ghost references persist as V2 payloads keyed by profile, geometry compatibility, direction, and source."
    - "Reference filenames encode key components after validation, preserving opaque IDs while avoiding raw path construction."
    - "SessionController derives reference keys from selected profile revision plus active run source, not Track marking provenance."

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/TimingGhostIntegrationTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/StoreMigrationTest.kt

key-decisions:
  - "Keep legacy V1 track/source reference APIs available only for old payload and migration coverage; runtime timing now uses exact V2 compatibility-key operations."
  - "Encode, rather than reject, filename-hostile but otherwise safe opaque ID characters such as ':' after validating IDs for separators, '..', and control characters."

patterns-established:
  - "Exact reference loads return NotFound for missing/different key slots and Corrupt for unsafe request keys or payload/request key mismatches."
  - "Provider/run source participates in the compatibility key, so a real run on a Demo-created profile saves into the real slot."

requirements-completed: [SC-03, SC-04]

# Metrics
duration: 11min
completed: 2026-06-28
---

# Phase 5 Plan 11: Exact-Key Reference Persistence Summary

**Ghost references now persist and resolve by exact course compatibility, with Demo/Real source coming from the active timing run instead of the Track marking source.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-06-28T00:49:01Z
- **Completed:** 2026-06-28T00:59:26Z
- **Tasks:** 1 TDD task
- **Files modified:** 6

## Accomplishments

- Added exact `CourseCompatibilityKey` reference save/load operations to `LocalSessionStore`, `FileSessionStore`, and `InMemorySessionStore`.
- V2 reference filenames now include profile, geometry compatibility, direction, and source slot, with validated opaque IDs encoded before path construction.
- `SessionController` now builds session reference keys from the selected profile revision/direction and the active run source.
- Added integration coverage proving Demo/Real, Recorded/Reverse, duplicate, and geometry mismatches fail closed across both store implementations.
- Added a provider-provenance regression proving a real run on a Demo-created profile persists as real.

## Task Commits

1. **Task 1 RED: Exact-key reference integration gate** - `96e6238` (`test`)
2. **Task 1 GREEN: Exact-key storage and controller wiring** - `59fe5be` (`feat`)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt` - added exact V2 compatibility-key reference operations.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt` - implemented exact-key V2 load/save, key validation, payload/request validation, and encoded filenames.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt` - mirrored exact-key behavior for deterministic tests.
- `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt` - derives session compatibility from selected profile revision and active run source, then loads/saves exact V2 references.
- `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/TimingGhostIntegrationTest.kt` - added the exact-key/provenance gate and updated timing-reference assertions to V2 key loads.
- `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/StoreMigrationTest.kt` - updated migrated V2 reference path expectation to include geometry compatibility.

## Decisions Made

- Retained legacy V1 reference operations for existing V1 store tests and migration seeding, but removed them from the runtime timing path.
- Used reversible component encoding for V2 reference filenames after validating IDs. This keeps migrated IDs like `track:g1` usable without constructing unsafe raw paths.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated migration test expectation for exact-key reference filenames**
- **Found during:** Task 1 GREEN verification
- **Issue:** `StoreMigrationTest` still expected the 05-10 V2 reference filename shape, which omitted geometry compatibility.
- **Fix:** Updated the assertion to expect the exact-key filename containing the encoded geometry compatibility id.
- **Files modified:** `shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/StoreMigrationTest.kt`
- **Verification:** `.\gradlew.bat :shared:testAndroidHostTest --tests "*TimingGhostIntegrationTest*" --tests "*GhostCompatibilityTest*" --tests "*StoreMigrationTest*"` and `.\gradlew.bat :shared:check`
- **Committed in:** `59fe5be`

---

**Total deviations:** 1 auto-fixed (1 blocking verification alignment).
**Impact on plan:** The adjustment keeps migration coverage aligned with the exact-key persistence requirement; no new dependency or deferred scope was introduced.

## Issues Encountered

- RED gate failed at compile time as expected because exact-key store operations did not exist yet.
- Existing Kotlin warnings in unrelated lap/review/storage files remain out of scope.

## Verification

- RED gate: `.\gradlew.bat :shared:testAndroidHostTest --tests "*TimingGhostIntegrationTest*"` failed at compile time because exact-key reference operations were absent.
- Focused plan gate: `.\gradlew.bat :shared:testAndroidHostTest --tests "*TimingGhostIntegrationTest*" --tests "*GhostCompatibilityTest*" --tests "*StoreMigrationTest*"` - BUILD SUCCESSFUL.
- Full shared gate: `.\gradlew.bat :shared:check` - BUILD SUCCESSFUL.
- iOS simulator tests remain skipped on Windows; iOS compilation completed under `:shared:check`.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. Stub scan found only intentional nullable state, empty-list defaults, and test fixtures; no placeholder data path blocks the plan goal.

## Threat Flags

None new. T-05-23 is mitigated by request-key validation, encoded filename construction, and payload/request equality checks. T-05-24 is mitigated by active run source participating in the persisted compatibility key.

## Self-Check: PASSED

- Summary file and all six touched code/test files were verified present on disk.
- Task commits verified in git log: `96e6238` (RED test) and `59fe5be` (GREEN implementation).
- Stub scan found no placeholder/TODO path blocking the plan goal; matched nullable and empty-list values are intentional state defaults or test fixtures.
- Verification commands are current and passing: focused host tests and `.\gradlew.bat :shared:check`.

## Next Phase Readiness

Plan 05-12 can build the course-progress Ghost matcher on exact-key reference persistence without relying on the legacy per-track reference slot.

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-28*
