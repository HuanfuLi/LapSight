---
phase: 05-track-setup-and-course-profiles
plan: 1
subsystem: storage
tags: [kotlin-multiplatform, kotlinx-serialization, schema-migration, json-dispatch, course-profiles]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: V1 TrackPayloadV1 / TimingSessionPayloadV1 / ReviewIndex / FileSessionStore canonical JSON
  - phase: 04-ghost-lap-live-delta
    provides: GhostReferencePayloadV1 and real/simulated source isolation
provides:
  - Frozen V1 payload DTOs pinned to literal schemaVersion = 1
  - V2 course-profile contracts (TrackProfile, TrackRevision, CourseSetup, CourseSnapshot, CourseCompatibilityKey, SectorBoundary, LegacyCumulativeSplit, CurrentTrackSelection)
  - Literal-2 V2 payloads (TrackProfilePayloadV2, TimingSessionPayloadV2, GhostReferencePayloadV2)
  - SchemaMigrations JsonElement version dispatch returning typed LoadResult
  - Pure, deterministic V1 to V2 migration mapping
  - Literal committed V1 fixture + SchemaMigrationTest gate
affects: [05-02-store-migration, 05-03-current-selection, 05-07-complete-sector-timing, 05-10-ghost-compatibility, 05-13-wrong-course-preflight]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Frozen DTOs + explicit JsonElement schemaVersion dispatch (never bump a constant on a *V1 class)"
    - "Explicit injected geometry-compatibility identity (never a floating-point geometry hash)"
    - "Typed LoadResult corruption for unsafe IDs / non-finite / oversized / empty geometry"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackProfileModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaMigrations.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/SchemaMigrationTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/LegacyDatasetFixture.kt
    - shared/src/commonTest/resources/fixtures/v1/legacy-dataset.json
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaVersions.kt

key-decisions:
  - "V1 DTOs frozen to bare literal 1 (not CURRENT_* constants); CURRENT_* left at 1 so existing FileSessionStore behavior is unchanged"
  - "geometryCompatibilityId = '<oldId>:g1' and revisionId = '<oldId>:r1' deterministic string formulas; geometry is never hashed (D-15)"
  - "Legacy SectorEventDto preserved as LegacyCumulativeSplit; migration never infers a complete final Sector (D-06)"
  - "Migration maps current selection to null; never derives newest/only profile (D-01/D-04)"
  - "CourseSetup.startFinish made nullable so Tracks saved before start/finish confirmation migrate as not-yet-timing-ready"

patterns-established:
  - "Pattern 2 (Frozen DTOs + version dispatch): parse JsonElement, branch on literal schemaVersion, decode frozen serializer, validate, map to V2"
  - "Pattern 5 (Compatibility key + immutable CourseSnapshot): sessions/ghosts key by (profileId, geometryCompatibilityId, direction, isSimulated)"

requirements-completed: [SC-01, SC-03]

# Metrics
duration: 38min
completed: 2026-06-27
---

# Phase 5 Plan 01: V1 Freeze, V2 Profile Contracts, and Migration Mapping Summary

**Frozen V1 payloads, validated V2 course-profile/revision/snapshot contracts, and a pure deterministic V1→V2 migration with explicit JsonElement schema dispatch, gated by a literal historical-JSON fixture test.**

## Performance

- **Duration:** ~38 min
- **Started:** 2026-06-27T00:22Z (approx)
- **Completed:** 2026-06-27T00:60Z (approx)
- **Tasks:** 2
- **Files modified:** 8 (5 created, 3 modified)

## Accomplishments
- Froze all five V1 artifacts (Track, Review index, saved session, draft, Ghost) to literal `schemaVersion = 1` so a future bump can never re-emit the old shape under a new number.
- Added the full V2 course-profile domain (stable profile + immutable revisions + immutable session `CourseSnapshot` + `CourseCompatibilityKey`) with literal-`2` payloads.
- Implemented `SchemaMigrations` explicit `JsonElement` version dispatch with typed `LoadResult` corruption for unknown versions, malformed JSON, unsafe opaque IDs (`/`, `\`, `..`, control chars), non-finite/oversized geometry, and empty progress curves.
- Implemented the pure, deterministic V1→V2 mapping (deterministic identities without geometry hashing, preserved legacy cumulative splits, null current selection) proven against a literal committed historical-JSON fixture.

## Task Commits

Each task was committed atomically:

1. **Task 1: Freeze V1 and define validated V2 contracts against literal fixtures** - `2fd2d76` (feat)
2. **Task 2: Implement the pure V1 to V2 migration mapping** - `8975b5e` (test — mapping landed in Task 1 by dispatch coupling; Task 2 added the correctness gate)

## Files Created/Modified
- `track/TrackProfileModels.kt` (created) - V2 profile, revision, course setup, snapshot, compatibility key, current selection, and literal-2 payloads
- `storage/SchemaMigrations.kt` (created) - Version dispatch + pure V1→V2 mapping + validation primitives
- `storage/SchemaMigrationTest.kt` (created) - Literal dispatch, version/corruption, and migration-correctness gates (T-05-01, T-05-02)
- `storage/LegacyDatasetFixture.kt` (created) - Embedded literal V1 dataset (KMP commonTest portability)
- `resources/fixtures/v1/legacy-dataset.json` (created) - Canonical literal V1 fixture artifact
- `track/TrackModels.kt` (modified) - Froze TrackPayloadV1/TrackMarkingPayloadV1/ReviewIndex to literal 1
- `session/SessionModels.kt` (modified) - Froze TimingSessionPayloadV1/GhostReferencePayloadV1 to literal 1
- `storage/SchemaVersions.kt` (modified) - Added `SCHEMA_VERSION_V1`/`SCHEMA_VERSION_V2` dispatch constants and freeze documentation

## Decisions Made
- Kept `CURRENT_*_SCHEMA_VERSION` constants at 1 (still consumed by the existing `FileSessionStore` V1 validation) and froze the V1 DTO defaults to a bare literal `1`, decoupling the frozen shape from any future "current" bump.
- Returned the full V2 payloads from `decodeTimingSession`/`decodeGhostReference` (not just the inner domain object) so downstream store/Review plans get samples, laps, and progress curves directly.
- Made `CourseSetup.startFinish` nullable to honor the V1 case of a Track saved before start/finish confirmation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] KMP commonTest cannot portably load file resources**
- **Found during:** Task 1 (fixture authoring)
- **Issue:** `:shared:check` compiles `commonTest` for the iOS target, so JVM/Android classpath resource loading is unavailable; the planned `legacy-dataset.json` resource cannot be read from common test code on all targets.
- **Fix:** Committed the canonical `legacy-dataset.json` artifact AND mirrored its identical literal bytes in `LegacyDatasetFixture.kt` as an `internal const val`; the test decodes the embedded literal (still never reconstructing V1 Kotlin objects). Documented the mirror rationale in the fixture file.
- **Files modified:** shared/src/commonTest/.../LegacyDatasetFixture.kt, shared/src/commonTest/resources/fixtures/v1/legacy-dataset.json
- **Verification:** `:shared:check` compiles commonTest for both Android host and iOS simulator and runs green on Android host.
- **Committed in:** `2fd2d76` (Task 1 commit)

**2. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first test run)
- **Issue:** `:shared:testAndroidHostTest` failed with "SDK location not found"; the parallel worktree does not inherit the main checkout's gitignored `local.properties`.
- **Fix:** Copied `local.properties` (sdk.dir) from the main checkout into the worktree. File is gitignored and was NOT committed.
- **Files modified:** local.properties (worktree-local, untracked)
- **Verification:** Tests subsequently compiled and ran.
- **Committed in:** N/A (gitignored build config, intentionally not committed)

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking, environment/toolchain)
**Impact on plan:** No scope change. Both fixes are environment/portability adaptations; the literal-fixture-through-dispatch intent is fully preserved.

## Issues Encountered
- None beyond the two blocking environment fixes above. The full `:shared:check` suite (existing + new tests) is green; pre-existing compiler warnings in unrelated files (`FileSessionStore`, `ReviewScreen`, lap/geometry tests) were left untouched (out of scope).

## Known Stubs
- Migrated `SectorBoundary.normalizedProgress` is intentionally `null` for V1-migrated boundaries: arc-length anchors require the `ClosedReferencePath` primitive built in Plan 05-05. This is documented in `TrackProfileModels.kt` and is the planned division of work, not an unresolved stub. Legacy boundary geometry (`pointA`/`pointB`) is preserved so nothing is lost.

## TDD Gate Compliance
- Plan frontmatter is `type: execute` with `tdd="true"` tasks (no MVP/TDD runtime gate signaled by the orchestrator). The migration mapping is exercised by Task 1's decode-each-member tests because dispatch necessarily invokes it; Task 2 added the rigorous migration-correctness gate (deterministic identities, legacy-split preservation, null selection, ghost source/direction). Both task commits leave the focused class and `:shared:check` green.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- The migration contract and identities are established and green, unblocking Plan 05-02 (side-by-side store migration), which can now wire `SchemaMigrations` into `FileSessionStore`/`InMemorySessionStore` with idempotent V2 writes and index rebuild.
- `CourseCompatibilityKey` and `CourseSnapshot` are ready for the Ghost-compatibility (05-10/05-11) and complete-Sector timing (05-07) plans.

## Self-Check: PASSED
- Created files verified present: `TrackProfileModels.kt`, `SchemaMigrations.kt`, `SchemaMigrationTest.kt`, `LegacyDatasetFixture.kt`, `legacy-dataset.json`.
- Commits verified in git log: `2fd2d76` (Task 1), `8975b5e` (Task 2).
- `:shared:check` BUILD SUCCESSFUL (Android host tests pass; iOS test compilation succeeds, run skipped on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
