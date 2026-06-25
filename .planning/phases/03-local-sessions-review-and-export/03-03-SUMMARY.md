---
phase: 03-local-sessions-review-and-export
plan: 03
subsystem: database
tags: [kotlinx-serialization, okio, kmp, storage, json, expect-actual, tdd]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: Human-approved Maven/Gradle coordinate set (Plan 03-02 dependency gate)
provides:
  - Approved dependency additions (serialization plugin, kotlinx-serialization-json, Okio, material-icons-core)
  - Schema version constants (CURRENT_TRACK_SCHEMA_VERSION, CURRENT_SESSION_SCHEMA_VERSION)
  - Canonical @Serializable track/session DTOs and ReviewIndex metadata rows
  - StoragePaths expect/actual app-private root abstraction (Android filesDir, iOS sandbox)
  - LocalSessionStore interface + Okio FileSessionStore (atomic writes, payload-before-index, corrupt-JSON quarantine)
affects: [03-04 reference extraction, 03-05 navigation shell icons, 03-06 review, 03-07 timing, 03-08 export]

# Tech tracking
tech-stack:
  added:
    - "org.jetbrains.kotlin.plugin.serialization 2.4.0 (Gradle plugin)"
    - "org.jetbrains.kotlinx:kotlinx-serialization-json 1.11.0"
    - "com.squareup.okio:okio 3.17.0"
    - "org.jetbrains.compose.material:material-icons-core 1.7.3"
  patterns:
    - "Versioned canonical JSON payloads with embedded schemaVersion (D-24, D-25)"
    - "Atomic temp-write + atomicMove, payloads written before index (D-22, D-23)"
    - "Typed LoadResult (Loaded/NotFound/Corrupt) instead of throwing on bad JSON (D-21)"
    - "expect/actual platform boundary for app-private storage root"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/SchemaVersions.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackModels.kt
    - shared/src/androidMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.android.kt
    - shared/src/iosMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.ios.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStoreTest.kt
  modified:
    - gradle/libs.versions.toml
    - build.gradle.kts
    - shared/build.gradle.kts

key-decisions:
  - "Added only the four Plan 03-02 approved coordinates; material-icons-core pinned to 1.7.3 (not 1.11.1)"
  - "Serializable DTOs mirror Phase 2 domain types (LocationSampleDto) so the lap engine input stays serialization-free"
  - "Tests use a local in-memory FileSystem on the approved okio core artifact instead of the unapproved okio-fakefilesystem"

patterns-established:
  - "Payload-before-index atomic write ordering for crash-safe local persistence"
  - "Typed corrupt/not-found load results with schemaVersion + finite-coordinate validation"
  - "Platform-supplied app-private root via expect/actual; store accepts FileSystem + root by injection"

requirements-completed: [SESS-04]

# Metrics
duration: 30min
completed: 2026-06-25
---

# Phase 3 Plan 03: Versioned Local Storage Foundation Summary

**App-private, versioned canonical-JSON file store (Okio + kotlinx.serialization) with atomic payload-before-index writes, typed corrupt-JSON quarantine, and the track/session DTO + ReviewIndex schema every later Phase 3 plan persists through.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-06-25
- **Tasks:** 2 (TDD: 1 RED test commit + 1 GREEN implementation commit)
- **Files modified:** 12 (9 created, 3 modified)

## Accomplishments
- Added the four — and only the four — Plan 03-02 human-approved dependency coordinates, with `material-icons-core` correctly pinned to `1.7.3`.
- Established `SchemaVersions` constants and a full set of `@Serializable` canonical DTOs: `LocationSampleDto`, `SourceMetadata`, `AppMetadata`, `GpsQualitySummary`, `Track`, `TrackMarkingSession`, `TrackReferenceLine` (shape only), payload wrappers, and `ReviewIndex`/`ReviewIndexRow`.
- Implemented `LocalSessionStore`/`FileSessionStore` with Okio: atomic temp-write + `atomicMove`, payloads written before the metadata index, typed `LoadResult` for missing/corrupt files, and schema/coordinate validation (mitigates T-03-06, T-03-07, T-03-08).
- `StoragePaths` expect/actual app-private root (Android `filesDir`, iOS `NSDocumentDirectory` sandbox); store takes the `FileSystem` + root via injection so tests use no real platform path.
- Wave 0 tests cover D-21 through D-25: ordering, atomic-write recovery, corrupt-JSON typed result, and app-private root isolation; `:shared:check` passes.

## Task Commits

Each task was committed atomically (TDD):

1. **Task 1: Add Wave 0 tests for versioned local storage** - `30668f8` (test, RED)
2. **Task 2: Implement approved dependencies, schemas, models, and file store** - `34bc8f5` (feat, GREEN — includes the iOS opt-in fix and the in-memory test FS)

## Files Created/Modified
- `gradle/libs.versions.toml` - Added serialization/okio/material-icons versions, libraries, and the serialization plugin alias (approved coordinates only).
- `build.gradle.kts` - Registered `kotlinSerialization` plugin (apply false).
- `shared/build.gradle.kts` - Applied serialization plugin; added json/okio/material-icons-core to `commonMain`.
- `.../storage/SchemaVersions.kt` - `CURRENT_TRACK_SCHEMA_VERSION` / `CURRENT_SESSION_SCHEMA_VERSION`.
- `.../session/SessionModels.kt` - Shared base DTOs + `LocationSample <-> Dto` mappers + GPS quality rollup.
- `.../track/TrackModels.kt` - Track/marking/reference DTOs, payload wrappers, `ReviewIndex`.
- `.../storage/StoragePaths.kt` (+ android/ios actuals) - app-private root + FileSystem boundary.
- `.../storage/LocalSessionStore.kt` - repository API, `SaveResult`, `LoadResult`.
- `.../storage/FileSessionStore.kt` - Okio implementation with atomic writes and quarantine.
- `.../storage/FileSessionStoreTest.kt` - Wave 0 coverage + local in-memory FileSystem.

## Decisions Made
- **Approved coordinates only:** material-icons-core pinned to `1.7.3` per the binding 03-02 gate (the plan/research text's `1.11.1` does not exist).
- **DTO mirrors over annotating Phase 2 models:** `LocationSampleDto`/`GeoPointDto`/line DTOs keep `GpsProbeModels.kt` and `TimingLines.kt` untouched and serialization-free, with explicit mappers.
- **Shared base metadata in `SessionModels`:** `SourceMetadata`/`AppMetadata`/`GpsQualitySummary` live in the session package because tracks and timing sessions both persist them (D-24).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Test in-memory FileSystem without the unapproved `okio-fakefilesystem` artifact**
- **Found during:** Task 2 (GREEN compile)
- **Issue:** 03-RESEARCH.md's test pattern uses Okio `FakeFileSystem`, which ships in the separate Maven coordinate `com.squareup.okio:okio-fakefilesystem`. That coordinate is NOT in the Plan 03-02 human-approved set, and the gate forbids adding any coordinate not on the list.
- **Fix:** Implemented a minimal multiplatform in-memory `FileSystem` in the test source using only the approved `okio` core artifact (`FileSystem`, `ForwardingFileSystem`, `Buffer`). This also better satisfies the plan's "no real platform path" requirement.
- **Files modified:** shared/src/commonTest/.../storage/FileSessionStoreTest.kt
- **Verification:** `:shared:testAndroidHostTest` passes all four tests; no new coordinate added.
- **Committed in:** `34bc8f5`

**2. [Rule 3 - Blocking] iOS `ExperimentalForeignApi` opt-in for `NSDocumentDirectory` lookup**
- **Found during:** Task 2 (`:shared:check`, iosSimulatorArm64 compile)
- **Issue:** `NSFileManager.URLForDirectory(...)` exposes a cinterop `NSError` pointer parameter requiring `@OptIn(ExperimentalForeignApi::class)`; compilation failed without it.
- **Fix:** Added the import and `@OptIn(ExperimentalForeignApi::class)` to `appPrivateRoot()`.
- **Files modified:** shared/src/iosMain/.../storage/StoragePaths.ios.kt
- **Verification:** `:shared:check` BUILD SUCCESSFUL (iOS simulator link/test SKIPPED on Windows, as expected).
- **Committed in:** `34bc8f5`

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking).
**Impact on plan:** Both necessary to complete the task within the approved coordinate set and to compile all targets. No scope creep; no extra dependency added.

## Issues Encountered
- The `okio-fakefilesystem` mismatch (above) was the only substantive blocker; resolved by a local in-memory FS rather than escalating, keeping the binding supply-chain gate intact.

## Known Stubs
- `TrackReferenceLine` is intentionally a data-shape-only DTO (empty `points` by default). The extraction algorithm that populates it is owned by Plan 03-04 (`ReferenceLineExtractor`), as stated in the plan interfaces. Not a blocking stub for this plan's storage goal.

## User Setup Required
None - no external service configuration required. (Worktree build needed the gitignored `local.properties` copied from the main checkout for the Android SDK path; not committed.)

## Next Phase Readiness
- Storage layer + canonical versioned DTO schema are in place for 03-04 (reference extraction populates `Track.referenceLine`), 03-06 (Review reads `ReviewIndex`/payloads), 03-07 (timing sessions reuse `SessionModels` base DTOs), and 03-08 (JSON/GPX export reuses the same canonical schema per D-24).
- SESS-04 foundation (versioned canonical JSON shared by saved + exported payloads) is established; the actual `JsonExportService` user-facing export is delivered in Plan 03-08.
- `StoragePaths.initialize(context)` must be wired from the Android entrypoint when the store is first used (Android actual requires an application Context).

---
*Phase: 03-local-sessions-review-and-export*
*Completed: 2026-06-25*
