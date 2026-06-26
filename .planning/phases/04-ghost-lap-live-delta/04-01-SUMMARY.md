---
phase: 04-ghost-lap-live-delta
plan: 01
subsystem: ghost-delta
tags: [kotlin-multiplatform, ghost, live-delta, progress-curve, lap-engine, tdd]

# Dependency graph
requires:
  - phase: 02-clean-room-lap-engine-v0
    provides: LocalProjection geometry, GeoPoint/LocalPoint, LocationSample, LapEvent/LapTimingState
  - phase: 03-local-sessions-review-and-export
    provides: simulated GPS provider, Track/TimingSession model split, deterministic fixtures
provides:
  - Pure ghost domain package (shared/.../ghost) with no UI/storage/platform dependency
  - ProgressPoint/ProgressCurve/ReferenceLap telemetry-ready domain models
  - ProgressCurveBuilder with typed-failure curve construction and clamped interpolation
  - LiveDeltaEngine with realtime delta math, suppression reasons, and stale clearing
affects: [04-02 reference-lap storage, 04-03 live delta UI, 04-04 variable-pace UAT]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Typed result (sealed ProgressCurveResult) instead of throwing for bad GPS input"
    - "Streaming pure engine that overwrites a single snapshot to prevent stale state"
    - "Distance-indexed progress curve with binary-search interpolation"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostModels.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ProgressCurveBuilder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngine.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ghost/ProgressCurveBuilderTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngineTest.kt
  modified: []

key-decisions:
  - "ReferenceLap carries a simple isSimulated flag instead of coupling to a storage SourceMetadata type (deferred to Plan 04-02)"
  - "Progress overrun beyond 25% of reference total distance is treated as UnmatchedProgress; smaller overshoot is clamped"
  - "Builder filters poor-accuracy samples and validates finite/monotonic/positive-distance before building"

patterns-established:
  - "Pattern: ghost algorithms live in commonMain and are validated only with deterministic commonTest fixtures"
  - "Pattern: suppression states are an enum-backed Unavailable snapshot so UI can map every reason to `--`"

requirements-completed: [GHOST-02, GHOST-04]

# Metrics
duration: 6min
completed: 2026-06-26
---

# Phase 4 Plan 01: Ghost Progress-Curve and Live-Delta Domain Summary

**Clean-room shared-Kotlin ghost domain: distance-indexed progress curves with clamped interpolation and a realtime LiveDeltaEngine that emits signed delta or a typed Unavailable state, with no UI, storage, or platform dependency.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-06-26T03:26:10Z
- **Completed:** 2026-06-26T03:31:49Z
- **Tasks:** 3
- **Files modified:** 5 created

## Accomplishments
- TDD Wave 0 tests describing progress-curve math and delta suppression semantics (D-05..D-11, D-17..D-19).
- `ProgressCurveBuilder` produces a monotonic, normalized, telemetry-ready progress curve and returns typed failures instead of throwing on bad GPS (T-04-01).
- `ProgressCurve.elapsedAtProgress` interpolates reference elapsed time with binary search and clamps out-of-range progress (T-04-02).
- `LiveDeltaEngine` accumulates current-lap progress, computes `delta = currentElapsed - referenceElapsed`, signs it (positive=slower, negative=faster), and overwrites its snapshot so a stale delta is never reused (T-04-03).

## Task Commits

Each task was committed atomically:

1. **Task 1: Wave 0 failing tests** - `cb5442c` (test)
2. **Task 2: Progress curve construction and interpolation** - `0167346` (feat)
3. **Task 3: Realtime live delta engine** - `a6436f2` (feat)

_TDD plan gate: RED `test(...)` commit (`cb5442c`) precedes the GREEN `feat(...)` commits._

## Files Created/Modified
- `shared/.../ghost/GhostModels.kt` - ProgressPoint, ProgressCurve (with `elapsedAtProgress`), ReferenceLap, ProgressCurveResult/Failure, LiveDeltaSnapshot, DeltaUnavailableReason.
- `shared/.../ghost/ProgressCurveBuilder.kt` - Pure builder: accuracy filtering, finite/monotonic validation, local-projection distance accumulation, normalization, zero-distance rejection.
- `shared/.../ghost/LiveDeltaEngine.kt` - Streaming delta engine: lap reset, progress accumulation, reference interpolation, suppression rules, stale clearing.
- `shared/.../ghost/ProgressCurveBuilderTest.kt` - Curve monotonicity, normalized endpoints, half-progress interpolation, clamping, and integrity-failure cases.
- `shared/.../ghost/LiveDeltaEngineTest.kt` - Delta sign, all unavailable reasons, stale clearing, and lap reset.

## Decisions Made
- `ReferenceLap` uses an `isSimulated: Boolean` flag rather than a storage-layer source type; full source metadata and persistence are deferred to Plan 04-02 to keep this layer pure.
- Progress overrun tolerance is 25% of the reference total distance before flagging `UnmatchedProgress`; within tolerance, progress is clamped to the curve range.
- Accuracy gating applies in both layers: the builder drops poor-accuracy reference samples; the engine suppresses on poor-accuracy live samples.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing worktree `local.properties` (Android SDK location)**
- **Found during:** Task 1 (running the RED verification)
- **Issue:** The git worktree had no `local.properties`, so `:shared:testAndroidHostTest` failed with "SDK location not found" before any test could compile/run.
- **Fix:** Copied the main checkout's `local.properties` (gitignored `sdk.dir`) into the worktree root. Not committed (gitignored).
- **Files modified:** `local.properties` (untracked, local-only)
- **Verification:** Gradle proceeded to compile/run tests after the copy.
- **Committed in:** N/A (gitignored environment file)

**2. [Rule 3 - Blocking] `LiveDeltaEngine` skeleton added in Task 2**
- **Found during:** Task 2 (running `--tests "*ProgressCurveBuilderTest*"`)
- **Issue:** Kotlin compiles the whole `commonTest` source set as a unit. `LiveDeltaEngineTest` (committed in Task 1) references `LiveDeltaEngine`, so the targeted ProgressCurveBuilder run could not compile until that class existed.
- **Fix:** Added a minimal `LiveDeltaEngine` skeleton (returns `Unavailable`) in Task 2 so the source set compiles; the full implementation replaced it in Task 3.
- **Files modified:** `shared/.../ghost/LiveDeltaEngine.kt`
- **Verification:** `ProgressCurveBuilderTest` passes after Task 2; `LiveDeltaEngineTest` passes after Task 3.
- **Committed in:** `0167346` (Task 2), fully implemented in `a6436f2` (Task 3)

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking)
**Impact on plan:** Both were environment/compilation prerequisites, not behavior changes. No scope creep; production code matches the plan's interface and tasks.

## Issues Encountered
- None beyond the deviations above. All targeted ghost tests and the full `:shared:check` gate pass (iOS simulator test execution is SKIPPED on Windows, but iOS compilation succeeds).

## TDD Gate Compliance
- RED: `cb5442c` (`test(04-01)`) committed failing tests; verified failure was only unresolved ghost production references.
- GREEN: `0167346` and `a6436f2` (`feat(04-01)`) made `ProgressCurveBuilderTest` then `LiveDeltaEngineTest` pass.
- No REFACTOR commit was needed.

## Known Stubs
None. The Task 2 `LiveDeltaEngine` skeleton was fully replaced in Task 3; no placeholder values flow to any consumer. The progress curve is intentionally telemetry-ready (extra per-point data), which is a planned forward hook (D-07), not a stub.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- GHOST-02 delta engine foundation and GHOST-04 telemetry-ready reference model are in place for Plan 04-02 (storage + timing-session integration) and Plan 04-03 (live delta UI).
- Plan 04-02 must introduce the storage DTOs/persistence and map `ReferenceLap.isSimulated` onto the real/simulated isolation boundary.

---
*Phase: 04-ghost-lap-live-delta*
*Completed: 2026-06-26*
