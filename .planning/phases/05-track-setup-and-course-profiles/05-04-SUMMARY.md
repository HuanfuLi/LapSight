---
phase: 05-track-setup-and-course-profiles
plan: 4
subsystem: track-profiles
tags: [kotlin-multiplatform, create-profile, current-selection, drive-ui, ios-bootstrap, input-validation]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 3
    provides: TrackProfileController.resolveCurrent + typed CurrentProfileResolution, persisted current-selection contract, DriveMarkingController snapshot (currentTrackName/needsTrackSelection/selectableProfiles)
provides:
  - TrackProfileController.saveProfile create-first-profile path (validated name, opaque IDs, immutable first revision, no auto-select)
  - CreateProfileResult typed outcome + isSafeProfileName name validator
  - DriveMarkingController.selectTrack explicit-selection passthrough
  - Drive compact Track selector (current name + direct select action + blocked routing)
  - Track Review name-on-create field; Review Track-detail "Set as current track"
  - iOS MainViewController file-store bootstrap (cold-launch persistence)
affects: [05-05-offline-editor, 05-07-complete-sector-timing, 05-11-session-bootstrap-preflight]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Create-first-profile path validates the user name before any write and never derives a storage path from the name (opaque IDs only)"
    - "Create is selection-free: saveProfile builds + persists; selection is always a separate explicit step (D-01..D-04)"
    - "Compose renders selection state and forwards a profileId/name; it never resolves or derives a Track"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/track/CourseProfileCreateTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackProfileController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt
    - shared/src/iosMain/kotlin/com/huanfuli/lapsight/shared/MainViewController.kt

key-decisions:
  - "The 'no auto-select on create' contract (Task 1) lives in TrackProfileController.saveProfile; DriveMarkingController.saveTrack keeps the mark -> time flow by calling saveProfile then EXPLICITLY setCurrentSelection, so the existing 05-03 mark-selects-it test stays green"
  - "saveProfile reuses SchemaMigrations.migrateTrack for deterministic opaque IDs (profileId == track.id, <id>:r1 / <id>:g1) and stamps the validated name with .copy(name=...), so the name never influences identity or paths (T-05-07)"
  - "isSafeProfileName rejects blank, path separators, '..', and control characters as defense in depth even though names never form a path"
  - "Review 'Set as current track' promotes a V1-only Track to a V2 profile on demand (via saveProfile) before selecting, so selection always resolves a real aggregate without starting a session"

patterns-established:
  - "Pattern: typed create result (Created/Rejected) so callers branch on an invalid name without catching an exception"

requirements-completed: [SC-01, SC-03]

# Metrics
duration: ~50min
completed: 2026-06-27
---

# Phase 5 Plan 04: New-User Create Flow and Selection Surfaces Summary

**Closed SC-01 for a brand-new user with an empty store: a completed Track marking plus a validated name now becomes a selectable V2 `TrackProfile` through a selection-free `TrackProfileController.saveProfile` create path, exposed by a compact current-Track selector on Drive, a name-on-create field and `Set as current track` in Review, and an iOS file-store bootstrap so created profiles and the selection survive cold launch.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 2 (Task 1 TDD RED -> GREEN; Task 2 auto UI + iOS bootstrap)
- **Files:** 6 (1 created, 5 modified)

## Accomplishments

- Added `TrackProfileController.saveProfile(track, name, app)` returning a typed `CreateProfileResult` (`Created` / `Rejected`): it validates the user name BEFORE any write (rejects blank, path separators, `..`, control characters — T-05-07), generates opaque IDs via the canonical `SchemaMigrations.migrateTrack` mapping (`profileId == track.id`, `<id>:r1` / `<id>:g1`), writes a single immutable first revision, and NEVER auto-selects.
- Wrote `CourseProfileCreateTest` as an empty-store integration gate proving: empty store -> one selectable named profile; opaque id never embeds the name; same name twice -> two independent profiles; blank/unsafe names write nothing; and create does NOT select (resolves `None` until an explicit `setCurrentSelection`).
- Routed `DriveMarkingController.saveTrack` through `saveProfile`, then EXPLICITLY persists the current selection so the mark -> time flow keeps unblocking Timing while the create API itself stays selection-free.
- Added `DriveMarkingController.selectTrack(profileId)` — an explicit-selection passthrough that loads only the exactly-named active profile and persists it, never deriving a different Track.
- Added a compact Drive `TrackSelectorSection` (Idle surface only, never the moving fullscreen dash): it always states the current Track name or clearly states none, lists active latest-revision profiles as direct select actions, marks the current row, and routes a blocked Timing to selection (D-01..D-03). No automatic newest/nearby recommendation (D-04).
- Added a name-on-create text field to Track Review and a `Set as current track` action to the Review Track detail that promotes a V1-only Track to a V2 profile on demand and selects it without starting a session (D-02).
- Bootstrapped iOS persistence: `MainViewController` now injects `StoragePaths.fileSessionStore()` instead of defaulting to `InMemorySessionStore`, so created profiles and the current selection persist across cold launch (T-05-09).

## Task Commits

1. **Task 1 RED — `test(05-4)`** - `d0e4927` — `CourseProfileCreateTest` + `saveProfile`/`CreateProfileResult`/`isSafeProfileName` as a `TODO()` stub; all 4 cases compile and fail RED.
2. **Task 1 GREEN — `feat(05-4)`** - `be000f7` — implement `saveProfile`; route `saveTrack` through it; `CourseProfileCreateTest` + `CurrentTrackSelectionTest` + `DriveMarkingControllerTest` green.
3. **Task 2 — `feat(05-4)`** - `283b0d5` — `selectTrack`, Drive selector, Review name-on-create + `Set as current track`, iOS file-store injection; `:shared:check` and `:androidApp:assembleDebug` green.

## Files Created/Modified

- `track/TrackProfileController.kt` (modified) - new `saveProfile` create path + `CreateProfileResult` + top-level `isSafeProfileName`.
- `track/CourseProfileCreateTest.kt` (created) - empty-store create-flow integration gate (opaque IDs, no-overwrite, name validation, no auto-select).
- `ui/DriveMarkingController.kt` (modified) - `saveTrack` routes through `saveProfile` then explicitly selects; new `selectTrack`; dropped now-unused `SchemaMigrations`/`TrackPayloadV1` imports.
- `ui/DriveScreen.kt` (modified) - `onSelectProfile` wiring, `TrackSelectorSection`, name-on-create field; kept selector off the timing surface and retained closed-course safety copy.
- `ui/ReviewScreen.kt` (modified) - `Set as current track` action + `setAsCurrentTrack` helper (V1->V2 promote-then-select).
- `iosMain/.../MainViewController.kt` (modified) - inject `StoragePaths.fileSessionStore()`.

## Decisions Made

- **Where "no auto-select" lives:** Task 1's contract is enforced in `TrackProfileController.saveProfile` (the create API), not in `DriveMarkingController.saveTrack`. `saveTrack` deliberately calls `saveProfile` and THEN `setCurrentSelection`, preserving the 05-03 mark -> time behavior (and the existing `markingATrackSelectsItAsCurrentAndUnblocksTiming` test) while keeping the create primitive selection-free.
- **Opaque identity reuse:** `saveProfile` reuses `migrateTrack` rather than minting a parallel id scheme, so identity stays deterministic and byte-compatible with `SessionController.startTiming(profileId)` (`profileId == track.id`).
- **V1 Track promotion in Review:** `Set as current track` promotes a legacy V1-only Track to a V2 profile on demand so a selection always names a real aggregate; this avoids a `Stale` resolution for pre-existing tracks that were never migrated.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first test run)
- **Issue:** `:shared:testAndroidHostTest` / `:androidApp:assembleDebug` need `local.properties` (`sdk.dir`); the parallel worktree does not inherit the main checkout's gitignored copy.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (gitignored; NOT committed).
- **Committed in:** N/A (gitignored build config).

**2. [Rule 3 - Blocking] Dropped now-unused imports in `DriveMarkingController`**
- **Found during:** Task 1 GREEN
- **Issue:** Routing `saveTrack` through `saveProfile` removed the direct `SchemaMigrations.migrateTrack(TrackPayloadV1(...))` call, leaving `SchemaMigrations` and `TrackPayloadV1` imports unused and adding `CreateProfileResult`.
- **Fix:** Swapped the imports accordingly.
- **Committed in:** `be000f7`.

---

**Total deviations:** 2 (both Rule 3 environment/blocking). No architectural change, no new dependency. The plan executed as written.

## TDD Gate Compliance

- **Task 1** (`tdd="true"`) followed the strict gate: RED commit `d0e4927` (`test(...)`) lands `CourseProfileCreateTest` against a `TODO()` stub so all 4 cases fail at runtime; GREEN commit `be000f7` (`feat(...)`) implements `saveProfile` until the focused class is green. No REFACTOR commit was needed.
- **Task 2** was `type="auto"` (UI + platform bootstrap), committed as a single `feat(...)`.

## Issues Encountered

- iOS cold-launch UAT (the plan's macOS manual gate: compile the iOS target and cold-launch twice to confirm a created+selected profile persists) could NOT be run on Windows: `:shared` iOS link/run tasks are `SKIPPED` here (as in every prior plan), though iOS source compiles cleanly under `:shared:check`. This remains a Phase 5 manual UAT item for a macOS run.
- Pre-existing compiler warnings in unrelated files (lap/review tests, `expect/actual` beta notes, a pre-existing `ReviewScreen` Elvis-on-non-null at line ~445) were left untouched (out of scope).

## Known Stubs

- None. The create path is fully implemented and exercised; the Drive selector and Review detail render real `listActiveProfiles()` rows and real store selection. No hardcoded empty data flows to the UI.

## Self-Check: PASSED

- All 6 created/modified files verified present on disk.
- All three task commits verified in git log: `d0e4927` (RED test), `be000f7` (Task 1 GREEN), `283b0d5` (Task 2).
- `:shared:testAndroidHostTest` (CourseProfileCreateTest + CurrentTrackSelectionTest + DriveMarkingControllerTest), full `:shared:check`, and `:androidApp:assembleDebug` all BUILD SUCCESSFUL; iOS compiles (link/run SKIPPED on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
