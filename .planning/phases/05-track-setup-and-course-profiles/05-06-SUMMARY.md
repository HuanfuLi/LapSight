---
phase: 05-track-setup-and-course-profiles
plan: 6
subsystem: track-profiles
tags: [kotlin-multiplatform, compose, viewport-transform, course-editor, immutable-revisions]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 5
    provides: ClosedReferencePath, CourseGeometryBuilder, CourseProfileEditor (progress-only edit state), CourseSetup.startFinishProgress anchor
  - phase: 05-track-setup-and-course-profiles
    plan: 4
    provides: TrackProfile/TrackRevision/CourseSetup V2 domain, TrackProfileController.saveProfile, LocalSessionStore.loadProfile/saveProfile
provides:
  - TraceViewport invertible geo/local<->normalized transform (forward render + inverse screen->local) over one common bounding-box projection
  - TrackEditorScreen offline Compose course editor (tap-place start/finish, drag Sector boundaries, confirm, 2..6 stepper, typed validation, progress-only persistence)
  - TrackProfileController.appendRevision + typed AppendRevisionResult (immutable revision append with D-15 geometry-compatibility carry-forward/regeneration)
  - Review Track-detail EditCourseSection (revision history + edit-save flow that refreshes history immediately)
affects: [05-07-complete-sector-timing, 05-08-revision-store-tests, 05-09-course-direction, 05-12-course-progress-matcher]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One TraceViewport owns the bounding-box projection; TraceProjection.project() delegates to it so forward render and the editor's screen->local inverse share exactly one transform (D-10)"
    - "Compose converts pointer positions through the viewport into local candidates and forwards candidate progress to the pure CourseProfileEditor; no endpoint/canvas coordinate is ever persisted"
    - "appendRevision appends an immutable revision (strictly increasing ordinal, deterministic revisionId) and never mutates prior geometry; saveProfile persists the whole aggregate payload-first"
    - "Geometry compatibility is decided by identity comparison (reference line / start-finish equality), never by hashing serialized floating-point geometry (D-15)"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/TrackEditorScreen.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/review/TraceProjection.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackProfileController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt

key-decisions:
  - "TraceViewport is a uniform (scaleX == scaleY) reversible transform built once via fromLayers; TraceProjection.project() now delegates to it, keeping all 11 existing TraceProjectionTest cases green with zero behavior change"
  - "TrackEditorScreen seeds from an existing CourseSetup by reconstructing start/finish and boundary positions from stored normalized progress (canonical anchors), so editing re-derives placement instead of trusting persisted endpoints (D-10)"
  - "appendRevision rejects a setup with no confirmed start/finish WITHOUT writing (defense in depth on top of the editor's canSave gate, D-05); missing/corrupt profiles are typed rejections, never silent creates"
  - "D-15 compatibility: Sector-only edits (same reference line + same start/finish) carry geometryCompatibilityId forward; reference-line or start/finish changes regenerate '<profileId>:g<ordinal>' so existing Ghost references isolate correctly"
  - "Review Track detail promotes a V1-only Track to a V2 profile before editing (reusing TrackProfileController.saveProfile), so a real aggregate always backs appendRevision"

requirements-completed: [SC-02]

# Metrics
duration: ~35min
completed: 2026-06-27
---

# Phase 5 Plan 06: Offline Track Editor and Immutable Revision Save Summary

**Delivered the complete offline course-setup interaction with no map tiles or platform geometry: refactored `TraceProjection` into a single invertible `TraceViewport` (forward render + inverse screen->local over one common bounding-box projection), built the constrained `TrackEditorScreen` that converts pointer positions through the viewport into local candidates and forwards candidate progress to the pure `CourseProfileEditor` (persisting progress only, never canvas coordinates), and added `TrackProfileController.appendRevision` + a Review Track-detail edit flow that appends a validated immutable revision and shows updated version history immediately while preserving all prior geometry — closing SC-02 / D-05, D-10, and D-12 through D-15.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2 (both `type="auto"`)
- **Files:** 4 (1 created, 3 modified)

## Accomplishments

- **`TraceViewport` (Task 1):** a uniform, reversible transform holding the canonical `LocalProjection`, bounding-box min corner, aspect-preserving scale, and centering offset. Exposes `geoToNormalized`/`localToNormalized` (forward) and `normalizedToGeo`/`normalizedToLocal` (inverse), plus `projectLayer`. `fromLayers` computes the identical bounding-box math the one-way projector used and returns `null` for empty/degenerate inputs. `TraceProjection.project()` now delegates to the viewport, so the editor's inverse and the existing render path share exactly one transform (D-10). All 11 `TraceProjectionTest` cases stay green.
- **`TrackEditorScreen` (Task 1):** a Compose editor that draws the closed reference loop (cyan), the derived start/finish line (green), and derived Sector boundaries (amber) using the existing canvas style, every handle re-derived from canonical geographic/progress data each frame. `detectTapGestures` places/replaces the start/finish candidate; `detectDragGestures` hit-tests the nearest handle and forwards the dragged local candidate to `CourseProfileEditor.placeStartFinish`/`dragBoundary`. Controls cover confirm start/finish (mandatory, D-05), a Sector on/off switch, a 2..6 count stepper, typed validation feedback, and Save (enabled only when `canSave`). It lives on an explicit pre-Timing/Review surface and is never mounted on the moving fullscreen dash; closed-course safety language stays visible.
- **`TrackProfileController.appendRevision` (Task 2):** loads the profile aggregate, rejects a setup with no confirmed start/finish without writing, appends an immutable `TrackRevision` with a strictly increasing ordinal and deterministic `revisionId`/`geometryCompatibilityId`, copies prior revisions forward unchanged, and persists the whole aggregate via `saveProfile`. D-15 is honored by identity comparison: Sector-only edits carry the prior compatibility id forward; reference-line or start/finish changes regenerate it. Returns a typed `AppendRevisionResult` (`Appended`/`Rejected`).
- **Review Track-detail editing (Task 2):** `EditCourseSection` lists immutable revision history, opens `TrackEditorScreen` seeded with the latest revision's `CourseSetup`, appends on valid save, and refreshes the history immediately by holding the updated profile in state. A V1-only Track is promoted to a V2 profile (via `saveProfile`) before editing so a real aggregate always backs the edit.

## Task Commits

1. **Task 1 — `feat(05-06)`** — `6a2f94b` — invertible `TraceViewport` + `TrackEditorScreen`; `CourseProfileEditorTest`/`ClosedReferencePathTest`/`TraceProjectionTest` green and `:androidApp:assembleDebug` succeeds.
2. **Task 2 — `feat(05-06)`** — `fde7e67` — `TrackProfileController.appendRevision` + `AppendRevisionResult` + Review `EditCourseSection`; `:shared:check` and `:androidApp:assembleDebug` succeed.

## Files Created/Modified

- `ui/TrackEditorScreen.kt` (created) — offline constrained Compose editor + pure handle hit-testing, screen->local conversion, derived-geometry rendering, and `seedEditor` reconstruction from canonical progress.
- `review/TraceProjection.kt` (modified) — added the invertible `TraceViewport`; `TraceProjection.project()` now delegates to it (no behavior change).
- `track/TrackProfileController.kt` (modified) — added `appendRevision`, the typed `AppendRevisionResult`, and a non-throwing clock guard.
- `ui/ReviewScreen.kt` (modified) — added `EditCourseSection` (revision history + edit-save flow) and the `ensureProfile` V1->V2 promotion helper; wired into Track detail.

## Decisions Made

- **One transform, two directions.** Rather than add ad-hoc lat/lon->pixel math in the editor, the existing one-way projector was promoted to a reversible `TraceViewport` and `project()` delegates to it. Canonical calculations stay in meters; the canvas remains rendering-only; and forward/inverse can never drift.
- **Seed from progress, not endpoints.** Editing an existing setup reconstructs start/finish and boundary positions from stored `normalizedProgress` (falling back to line midpoints only for V1-migrated boundaries with `null` progress), keeping D-10's progress-only invariant intact through a round trip.
- **Reject-without-write on partial edits.** `appendRevision` guards against a null start/finish independently of the editor's `canSave`, so no half-finished edit can ever append a revision even if a future caller bypasses the UI gate.
- **Identity-based compatibility.** D-15 carry-forward/regeneration compares reference-line and start/finish equality (data-class structural equality), never a float hash, matching the migration identity scheme (`<id>:r<n>` / `<id>:g<n>`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first Gradle run)
- **Issue:** The parallel worktree does not inherit the main checkout's gitignored `local.properties` (`sdk.dir`), so `:shared:testAndroidHostTest` / `:shared:check` / `:androidApp:assembleDebug` cannot configure the Android SDK.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (confirmed gitignored; NOT committed).
- **Commit:** N/A (gitignored build config).

### Predicted-file note

- The plan's `files_modified` listed `TraceProjection.kt`, `TrackEditorScreen.kt`, `TrackProfileController.kt`, and `ReviewScreen.kt`; all four were modified/created exactly as predicted with no additional files needed. `CourseProfileEditor.kt` was reused as-is (its public mutators were sufficient to seed and drive the editor), so no out-of-scope edit to the 05-05 domain was required.

**Total deviations:** 1 (Rule 3 environment/blocking). No architectural change, no new dependency, no deferred scope.

## Verification

- **Task 1:** `:shared:testAndroidHostTest --tests "*CourseProfileEditorTest*" --tests "*ClosedReferencePathTest*"` (plus `*TraceProjectionTest*`) BUILD SUCCESSFUL; `:androidApp:assembleDebug` BUILD SUCCESSFUL.
- **Task 2:** `:shared:testAndroidHostTest --tests "*CourseProfileEditorTest*"` BUILD SUCCESSFUL; full `:shared:check` BUILD SUCCESSFUL; `:androidApp:assembleDebug` BUILD SUCCESSFUL.
- **Note on `TrackRevisionStoreTest`:** the plan's Task 2 verify references `*TrackRevisionStoreTest*`, which is authored by a later plan (05-08) and does not exist yet. Per the execution objective, verification was scoped to the tests this plan owns/touches (`CourseProfileEditorTest`, `ClosedReferencePathTest`, `TraceProjectionTest`) plus full `:shared:check`; no 05-08 test was authored here.
- iOS link/run tasks remain SKIPPED on Windows (as in every prior plan); iOS source compiles cleanly under `:shared:check`. No iOS-runtime UAT is in scope for this UI/controller plan.

## Issues Encountered

- Pre-existing compiler warnings in unrelated lap/review test files and `expect`/`actual` beta notes were left untouched (out of scope). One transient redundant-conversion warning introduced in `TrackEditorScreen` was removed before commit.

## Known Stubs

- None. The editor is fully wired to the pure `CourseProfileEditor` and real `appendRevision`/`saveProfile` persistence; no hardcoded empty/placeholder data flows to the UI. Manual on-device drag UAT of 2/3/6-Sector behavior and start/finish confirmation remains a normal phase-level checkpoint (not a code stub).

## Threat Flags

- None new. Task 1 keeps T-05-11 mitigated (pointer input becomes a candidate progress via the editor; no screen coordinate is persisted). Task 2 mitigates T-05-12 (revision-append tampering): a complete setup is validated before an atomic append, prior revisions are never overwritten, and partial/invalid edits write nothing. No security surface beyond the plan's `<threat_model>`.

## Self-Check: PASSED

- All 4 created/modified files verified present on disk.
- Both task commits verified in git log: `6a2f94b` (Task 1), `fde7e67` (Task 2).
- `:shared:check` (all shared tests incl. `CourseProfileEditorTest`/`ClosedReferencePathTest`/`TraceProjectionTest`) and `:androidApp:assembleDebug` are BUILD SUCCESSFUL; iOS compiles (link/run SKIPPED on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
