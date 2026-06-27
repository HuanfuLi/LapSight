---
phase: 05-track-setup-and-course-profiles
plan: 5
subsystem: track-profiles
tags: [kotlin-multiplatform, closed-path-geometry, arc-length, course-editor, sector-generation, tdd]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 4
    provides: TrackProfile/TrackRevision/CourseSetup/SectorBoundary V2 domain, TrackReferenceLine + GeoPointDto shapes
provides:
  - ClosedReferencePath pure closed-loop primitive (perimeter, wrapped pointAt, smoothed tangent, O(N) nearest-segment projection with ambiguity, typed rejection)
  - CourseGeometryThresholds centralized geometry tuning (tangent half-window, boundary length, snap, ambiguity margin, min cyclic spacing, DoS bound)
  - CourseGeometryBuilder equal N-1 arc-length boundary generation + perpendicular endpoint derivation + vertex-deduped hairpin crossing count
  - CourseProfileEditor immutable progress-only edit state (snapped/spacing-clamped drag, default-3 enable, 2..6 counts, confirmed start/finish, typed validation, toCourseSetup)
  - CourseSetup.startFinishProgress additive progress anchor for progress-only persistence
affects: [05-06-offline-editor-save, 05-07-complete-sector-timing, 05-09-course-direction, 05-12-course-progress-matcher, 05-13-wrong-course-preflight]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One pure ClosedReferencePath drives editor placement, tangent/normal generation, projection, and (later) direction/preflight/Ghost matching — preventing divergent closed-loop implementations (05-RESEARCH Pattern 3)"
    - "Geometry is stored as absolute recorded arc-length progress only; finite endpoints are always regenerated from progress + tangent, never persisted as canvas coordinates (D-09, D-10)"
    - "All geometry thresholds live in one CourseGeometryThresholds value object; no call site hard-codes a distance"
    - "Invalid geometry and invalid edit state return typed rejections/validation, never exceptions (T-05-10, T-05-11)"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/ClosedReferencePath.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/CourseGeometryBuilder.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/CourseProfileEditor.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/ClosedReferencePathTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/CourseProfileEditorTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackProfileModels.kt

key-decisions:
  - "ClosedReferencePath.fromReferenceLine returns a typed Loaded/Rejected (Empty/NonFinite/Oversized/Degenerate) result; the closing last->first segment always participates and zero-length segments are de-duplicated at construction"
  - "Projection ambiguity is decided by comparing the nearest segment against the nearest NON-ADJACENT runner-up within ambiguityMarginMeters (5 m), so nearby parallel/hairpin straights flag as ambiguous without false positives from the two segments sharing the anchor vertex"
  - "pathCrossingCount de-dupes crossing points within 0.01 m so a boundary anchored exactly on a shared corner vertex (e.g. equal-arc boundary landing on a rectangle corner) counts as one crossing, while a boundary cutting a parallel straight counts as two and is rejected as AmbiguousBoundary"
  - "Editor stores absolute progress for start/finish and every boundary; drag projects + snaps (1 m) + clamps to minimum cyclic spacing relative to start/finish and other boundaries, persisting progress only (D-10)"
  - "CourseSetup gained an additive nullable startFinishProgress anchor (defaults null, V1-safe) so future direction transforms and round-trips can regenerate endpoints from progress"

requirements-completed: [SC-02]

# Metrics
duration: ~40min
completed: 2026-06-27
---

# Phase 5 Plan 05: Closed Reference Path and Constrained Course Editor Summary

**Built the single pure `ClosedReferencePath` arc-length primitive (closing-segment perimeter, wrapped `pointAt`, smoothed unit tangent/normal, O(N) nearest-segment projection with non-adjacent ambiguity, and typed invalid-geometry rejection) and layered `CourseGeometryBuilder` equal `N-1` Sector generation plus the immutable progress-only `CourseProfileEditor` (snapped + spacing-clamped drag, default-3 enable, 2..6 counts, confirmed start/finish, and typed invalid-count/impossible-spacing/hairpin validation) on top of it, all proven by two deterministic green test gates — closing SC-02 / D-05, D-07..D-10 with no UI, platform, or storage types in the domain.**

## Performance

- **Duration:** ~40 min
- **Tasks:** 2 (both `tdd="true"`; strict RED -> GREEN per task)
- **Files:** 6 (5 created, 1 modified)

## Accomplishments

- **`ClosedReferencePath` (Task 1):** projects a closed `TrackReferenceLine` to local meters via the canonical `LocalProjection`, builds a cumulative arc-length table that explicitly includes the last->first closing segment, and de-duplicates zero-length segments. Provides `perimeter`, wrapped `pointAt(s)` (binary search), a smoothed unit `tangentAt(s)` with a nearest-non-degenerate-segment fallback, a perpendicular `normalAt(s)`, and an O(N) `projectLocal`/`projectGeo` carrying absolute progress, lateral distance, segment id, and non-adjacent runner-up ambiguity. Empty / non-finite / oversized / degenerate inputs return a typed `Rejected`, never throw (T-05-10).
- **`CourseGeometryThresholds`:** one centralized value object for every tunable distance (boundary length 30 m, snap 1 m, ambiguity margin 5 m, tangent half-window `clamp(0.5% L, 5, 15)`, min cyclic spacing `max(20 m, 2% L)`, max-points DoS bound) — no call site hard-codes a threshold (05-RESEARCH Pattern 3).
- **`CourseGeometryBuilder` (Task 2):** generates `N-1` boundaries at equal closed-loop arc-length intervals from the start/finish anchor, derives each finite boundary as a line perpendicular to the smoothed tangent (endpoints from progress + tangent, centered on the anchor, never canvas coordinates), and counts vertex-deduped path crossings to detect self-intersecting/hairpin placements (D-08, D-09).
- **`CourseProfileEditor` (Task 2):** immutable, progress-only edit state. Placing/dragging projects a candidate onto the trace, snaps to 1 m, and clamps to the minimum cyclic spacing from start/finish and neighbours. Enabling Sector timing defaults to 3; counts 2..6 produce `N-1` stable boundary ids (`sb-1`..`sb-(N-1)`); disabled or valid configured Sectors validate. `validate()` returns typed problems (NoStartFinish, StartFinishUnconfirmed, InvalidSectorCount, ImpossibleSpacing, AmbiguousBoundary, BoundarySpacingTooTight); `toCourseSetup()` persists progress + derived endpoints and requires a confirmed start/finish (D-05, T-05-11).
- **Two deterministic green gates:** `ClosedReferencePathTest` (14 cases) and `CourseProfileEditorTest` (13 cases), built on exact local-meter rectangle/thin-rectangle fixtures (path origin at latitude 0 so meters map 1:1), covering closing-segment perimeter, wrapping, projection/ambiguity, tangent/normal, equal 2..6 generation, perpendicular derivation, constrained drag, spacing, and all rejection paths.

## Task Commits

1. **Task 1 RED — `test(05-5)`** — `8200258` — `ClosedReferencePathTest` + `ClosedReferencePath` stub (TODO bodies) + `CourseGeometryThresholds`; 14/14 fail RED.
2. **Task 1 GREEN — `feat(05-5)`** — `e589c9e` — implement the closed-path primitive; `ClosedReferencePathTest` green (14/14).
3. **Task 2 RED — `test(05-5)`** — `302c250` — `CourseProfileEditorTest` + `CourseGeometryBuilder`/`CourseProfileEditor` stubs + additive `CourseSetup.startFinishProgress`; 13/13 fail RED.
4. **Task 2 GREEN — `feat(05-5)`** — `e492d0d` — implement builder + editor; both focused test classes green and `:shared:check` green.

## Files Created/Modified

- `track/ClosedReferencePath.kt` (created) — pure closed-loop arc-length primitive + `CourseGeometryThresholds` + typed `ClosedReferencePathResult`/`ClosedPathRejection`/`PathProjection`.
- `track/CourseGeometryBuilder.kt` (created) — equal `N-1` generation, perpendicular endpoint derivation, deduped crossing count.
- `track/CourseProfileEditor.kt` (created) — immutable progress-only editor + `EditorBoundary`/`CourseProblem`/`CourseValidation`.
- `track/ClosedReferencePathTest.kt` (created) — closing-segment, wrapping, projection/ambiguity, tangent/normal, typed-rejection gate.
- `track/CourseProfileEditorTest.kt` (created) — default-3/2..6 generation, perpendicular endpoints, constrained drag, spacing, confirmed start/finish, invalid-count/impossible-spacing/hairpin gate.
- `track/TrackProfileModels.kt` (modified) — additive nullable `CourseSetup.startFinishProgress` anchor (V1-safe default).

## Decisions Made

- **Ambiguity uses the nearest non-adjacent runner-up.** Comparing the best segment only against segments NOT cyclically adjacent to it avoids a false ambiguity from the anchor's own two neighbouring segments, while still flagging a genuinely parallel/hairpin straight (proven by the thin-rectangle fixture).
- **Crossing-count de-duplication (0.01 m).** Equal-arc boundaries frequently land exactly on a polyline vertex (e.g. a rectangle corner for N = 2/4/6). De-duping crossing points by location makes such a corner-anchored boundary count once (valid) while a boundary that also cuts a parallel edge counts twice (rejected) — this is what makes both "all 2..6 counts valid on the oval" and "hairpin rejected" pass.
- **Progress-only persistence with an additive `CourseSetup.startFinishProgress`.** The editor never stores endpoint/canvas coordinates; `toCourseSetup()` writes the normalized progress anchor plus regenerated finite endpoints, keeping D-09/D-10 intact and giving downstream direction/round-trip work a clean anchor.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `sortedSetOf` is JVM-only — broke the iOS common compile**
- **Found during:** Task 2 GREEN (`:shared:check`)
- **Issue:** `:shared:testAndroidHostTest` passed, but `:shared:compileKotlinIosSimulatorArm64` failed: `sortedSetOf`/`TreeSet` are not in the Kotlin Multiplatform common stdlib, so the spacing-clamp helper did not resolve on iOS.
- **Fix:** Replaced `sortedSetOf(0.0)` + `+=` with a plain `mutableListOf(0.0)` accumulated and `.sorted()` — multiplatform-safe and behaviourally identical.
- **Files modified:** `track/CourseProfileEditor.kt`
- **Commit:** `e492d0d` (folded into Task 2 GREEN before commit).

**2. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first Gradle run)
- **Issue:** The parallel worktree does not inherit the main checkout's gitignored `local.properties` (`sdk.dir`), so `:shared:testAndroidHostTest` / `:shared:check` cannot configure the Android SDK.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (gitignored; NOT committed).
- **Commit:** N/A (gitignored build config).

### Predicted-file note

- The plan's `files_modified` listed `TrackProfileModels.kt`; it was modified exactly once with the additive `startFinishProgress` field described above. No other predicted file required structural change.

**Total deviations:** 2 (both Rule 3 environment/blocking). No architectural change, no new dependency, no deferred scope.

## TDD Gate Compliance

- **Task 1** (`tdd="true"`): RED `8200258` (`test(...)`) lands `ClosedReferencePathTest` against TODO stubs (14/14 fail at runtime); GREEN `e589c9e` (`feat(...)`) implements until green. No REFACTOR needed.
- **Task 2** (`tdd="true"`): RED `302c250` (`test(...)`) lands `CourseProfileEditorTest` against TODO stubs (13/13 fail); GREEN `e492d0d` (`feat(...)`) implements until green. No REFACTOR needed.
- Gate sequence (`test(...)` -> `feat(...)`) is present for both tasks in git log.

## Issues Encountered

- iOS link/run tasks remain SKIPPED on Windows (as in every prior plan); iOS source compiles cleanly under `:shared:check` after the `sortedSetOf` fix. No iOS-runtime UAT is in scope for this pure-geometry plan.
- Pre-existing compiler warnings in unrelated lap/review test files and `expect`/`actual` beta notes were left untouched (out of scope).

## Known Stubs

- None. Both primitives are fully implemented and exercised by deterministic green tests; no hardcoded empty/placeholder data flows anywhere.

## Threat Flags

- None. Task 1 mitigates T-05-10 (DoS via malformed/huge geometry) through finite/bounded/non-degenerate typed rejection; Task 2 mitigates T-05-11 (candidate-progress tampering) by projecting candidates and persisting progress only. No new security surface beyond the plan's `<threat_model>`.

## Self-Check: PASSED

- All 6 created/modified files verified present on disk.
- All four task commits verified in git log: `8200258` (T1 RED), `e589c9e` (T1 GREEN), `302c250` (T2 RED), `e492d0d` (T2 GREEN).
- `:shared:testAndroidHostTest` (`ClosedReferencePathTest` 14/14 + `CourseProfileEditorTest` 13/13) and full `:shared:check` are BUILD SUCCESSFUL; iOS compiles (link/run SKIPPED on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
