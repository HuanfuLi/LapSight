---
phase: 05-track-setup-and-course-profiles
plan: 8
subsystem: track-profiles
tags: [kotlin-multiplatform, compose, immutable-revisions, profile-lifecycle, archive, duplicate]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 6
    provides: TrackProfileController.appendRevision + AppendRevisionResult, Review Track-detail EditCourseSection, TrackEditorScreen
  - phase: 05-track-setup-and-course-profiles
    plan: 4
    provides: TrackProfile/TrackRevision V2 domain, LocalSessionStore.saveProfile/loadProfile/listActiveProfiles, current-selection store API
  - phase: 05-track-setup-and-course-profiles
    plan: 3
    provides: CurrentTrackSelection + TrackProfileController.resolveCurrent (fallback-free selection)
provides:
  - TrackProfileController.renameProfile (metadata-only rename, immutable revisions) + RenameProfileResult
  - TrackProfileController.archiveProfile (history-preserving archive that clears ONLY a matching current selection, idempotent) + ArchiveProfileResult
  - TrackProfileController.duplicateProfile (fresh profile/revision/compatibility ids, internal sector-only sharing preserved, source untouched) + DuplicateProfileResult
  - TrackProfileController.listHistory (ordered revisions incl. archived) + ProfileHistoryResult
  - appendRevision hardening (non-increasing-ordinal rejection, MAX_REVISIONS_PER_PROFILE bound)
  - Review Track-detail ProfileLifecycleSection (rename/duplicate/archive) + pure renameTrack/archiveTrack/duplicateTrack helpers
affects: [05-09-course-direction, 05-12-course-progress-matcher, drive-track-selection]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Lifecycle operations live on TrackProfileController and are built on the existing store primitives (loadProfile/saveProfile/loadCurrentSelection/clearCurrentSelection), so file and in-memory stores produce identical lifecycle results with zero store-implementation change"
    - "Archive is a reversible data-state stamp (archivedAtEpochMillis), never a file delete; it clears ONLY a current selection whose profileId matches and never selects a replacement (D-01/D-03)"
    - "Duplicate remaps each DISTINCT source geometryCompatibilityId to one fresh profile-scoped id, preserving internal sector-only sharing while guaranteeing no collision with the source (D-16)"
    - "Track-detail Compose forwards to pure internal helpers (renameTrack/archiveTrack/duplicateTrack) that return status strings; profile edits append to the V2 aggregate and never touch the frozen V1 Track payload that old session Review renders"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/TrackRevisionStoreTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/TrackProfileReviewTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackProfileController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt

key-decisions:
  - "Lifecycle as pure controller operations: rename/archive/duplicate/listHistory build on existing store primitives, so no change to LocalSessionStore/FileSessionStore/InMemorySessionStore/TrackProfileModels was required (Plan 05-07's additive sectorResults work was read fresh and left intact)"
  - "Archive idempotency keeps the ORIGINAL archive timestamp on re-archive; archive clears a matching current selection atomically (separate atomic store write) and never auto-selects a replacement"
  - "Duplicate takes an explicit opaque newProfileId (id generation stays out of the controller); the Track-detail helper derives '<id>-copy-<millis>' so duplicates are deterministic under an injected clock"
  - "appendRevision hardened with a non-increasing-ordinal gate (covers the in-memory store, which does not re-validate on load) and a MAX_REVISIONS_PER_PROFILE=100 bound (defense in depth, Rule 2)"
  - "Old session Review stays tied to old geometry by construction: profile edits write the V2 aggregate only, leaving the frozen V1 Track payload that TimingSession Review reads byte-for-byte unchanged"

requirements-completed: [SC-01, SC-03]

# Metrics
duration: ~45min
completed: 2026-06-27
---

# Phase 5 Plan 08: Profile Lifecycle (Rename, History, Archive, Duplicate) Summary

**Completed the maintainable-profile lifecycle: `TrackProfileController` now exposes `renameProfile` (metadata-only, immutable revisions), `archiveProfile` (history-preserving, idempotent, clears ONLY a matching current selection and never auto-selects a replacement), `duplicateProfile` (fresh profile/revision/compatibility ids with internal sector-only sharing preserved and the source untouched), and `listHistory` (ordered revisions including archived) — all built on the existing store primitives so the file and in-memory stores match by construction, plus `appendRevision` hardening against non-increasing ordinals and unbounded history. Track Review detail gains a `ProfileLifecycleSection` (rename field, Duplicate, Archive) wired to pure helpers, and an old session's Review stays bound to the frozen V1 geometry because profile edits append to the V2 aggregate only — closing SC-01 / SC-03 and D-12 through D-16 with no destructive delete path.**

## Performance

- **Duration:** ~45 min
- **Tasks:** 2 (Task 1 `tdd="true"`, Task 2 `auto`)
- **Files:** 4 (2 created tests, 2 modified production)

## Accomplishments

- **Lifecycle controller ops (Task 1):** `renameProfile`, `archiveProfile`, `duplicateProfile`, `listHistory` with typed results (`RenameProfileResult`, `ArchiveProfileResult` carrying `clearedCurrentSelection`, `DuplicateProfileResult` carrying source + duplicate, `ProfileHistoryResult`). Each validates names/ids before any write, loads the aggregate, and persists via the existing payload-first `saveProfile`. Archive sets `archivedAtEpochMillis` (idempotent — original timestamp retained), preserves every revision, and clears the current selection only when it matches the archived profile.
- **appendRevision hardening (Task 1):** added a non-increasing-ordinal gate (`revisionsStrictlyIncreasing`) — important because `InMemorySessionStore.loadProfile` does not re-validate ordinals the way the file store's decode does — and a `MAX_REVISIONS_PER_PROFILE = 100` bound. The D-15 carry-forward/regeneration logic from 05-06 is unchanged.
- **Both-store preservation gate (Task 1):** `TrackRevisionStoreTest` asserts every lifecycle behavior against BOTH stores via a relaunch-equivalent harness, plus file-only interrupted-write recovery (an injected `profiles/` write failure leaves the prior profile + selection intact) and unsafe-id path-traversal defense.
- **Track-detail exposure (Task 2):** `ProfileLifecycleSection` adds a rename text field plus Duplicate and Archive actions on the Track Review detail, forwarding to pure `renameTrack`/`archiveTrack`/`duplicateTrack` helpers (promoting a V1-only Track to a V2 profile on demand). Archiving the current Track clears the selection and Drive resolves to `CurrentProfileResolution.None` (no replacement). `TrackProfileReviewTest` covers rename/archive/duplicate and the frozen-V1-geometry guarantee for old session Review.

## Task Commits

1. **Task 1 RED — `test(05-08)`** — `96519d5` — failing `TrackRevisionStoreTest` (compile-fails on the missing lifecycle API).
2. **Task 1 GREEN — `feat(05-08)`** — `d0e5ecf` — lifecycle controller ops + typed results + appendRevision hardening; `TrackRevisionStoreTest` and `CurrentTrackSelectionTest` green.
3. **Task 2 — `feat(05-08)`** — `a4f86e8` — Track-detail `ProfileLifecycleSection` + pure helpers + `TrackProfileReviewTest`; `:shared:check` and `:androidApp:assembleDebug` succeed.

## Files Created/Modified

- `track/TrackProfileController.kt` (modified) — added `renameProfile`/`archiveProfile`/`duplicateProfile`/`listHistory`, the four typed result sealed interfaces, the `revisionsStrictlyIncreasing` guard, the `MAX_REVISIONS_PER_PROFILE` companion constant, and the append-time corrupt-history/bounded-history gates.
- `ui/ReviewScreen.kt` (modified) — added `ProfileLifecycleSection` (wired into Track detail) and the pure `renameTrack`/`archiveTrack`/`duplicateTrack` + `trackApp` helpers.
- `storage/TrackRevisionStoreTest.kt` (created) — both-store lifecycle/preservation gate (D-12..D-16, interrupted writes, unsafe ids/names, non-increasing revisions, bounded history).
- `ui/TrackProfileReviewTest.kt` (created) — Track-detail lifecycle helper coverage + frozen-geometry guarantee.

## Decisions Made

- **Lifecycle is pure controller, not a store change.** The store already provides every primitive (`loadProfile`/`saveProfile`/`loadCurrentSelection`/`clearCurrentSelection`), so building rename/archive/duplicate/history on the controller gives file/in-memory parity for free and keeps Plan 05-07's additive `sectorResults` store work untouched. The plan's predicted `files_modified` for the stores and `TrackProfileModels.kt` were therefore not needed (see Deviations).
- **Archive clears, never replaces.** Archiving the current Track clears the selection so Drive returns to the explicit no-selection state — it never falls back to "newest"/"only" (D-01/D-03), matching the fallback-free `resolveCurrent` contract.
- **Duplicate isolates while preserving structure.** Each distinct source compatibility id is remapped to one fresh profile-scoped id, so a sector-only sharing relationship survives the copy but can never collide with the source's Ghost references.
- **Frozen V1 geometry proves old-session stability.** Rather than rewire TimingSession Review onto V2 `CourseSnapshot` (owned by other plans; sessions remain V1 here), the invariant is proven directly: profile edits append to the V2 aggregate and leave the frozen V1 Track payload that Review renders byte-for-byte unchanged.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first Gradle run)
- **Issue:** The parallel worktree does not inherit the main checkout's gitignored `local.properties` (`sdk.dir`), so Gradle cannot configure the Android SDK.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (confirmed gitignored; NOT committed).
- **Commit:** N/A (gitignored build config).

**2. [Rule 2 - Missing critical functionality] appendRevision corrupt-history + bounded-history gates**
- **Found during:** Task 1 (implementing the preservation gate)
- **Issue:** The in-memory store's `loadProfile` does not re-validate revision ordinals (the file store does, via decode), so a corrupt non-increasing history could reach `appendRevision` and produce a colliding ordinal; history was also unbounded.
- **Fix:** Added `revisionsStrictlyIncreasing` rejection and a `MAX_REVISIONS_PER_PROFILE = 100` bound in `appendRevision` (T-05-16 defense in depth).
- **Files modified:** `track/TrackProfileController.kt`
- **Commit:** `d0e5ecf`

### Predicted-file note

- The plan's Task 1 `<files>` predicted edits to `TrackProfileModels.kt`, `LocalSessionStore.kt`, `FileSessionStore.kt`, and `InMemorySessionStore.kt`. None were needed: the lifecycle operations are pure controller logic over the existing store contract, which already supports archive (via `archivedAtEpochMillis` + `listActiveProfiles` filtering), exact load (`loadProfile`), and selection clearing. Per the execution objective, Plan 05-07's additive `sectorResults` param + `TimingSessionPayloadV1.sectorResults` field in the three store files were read fresh and left intact (not reverted). `TrackRevisionStoreTest.kt` was authored by this plan as instructed.

**Total deviations:** 2 (1 Rule 3 environment/blocking, 1 Rule 2 correctness). No architectural change, no new dependency, no deferred scope.

## Verification

- **Task 1:** `:shared:testAndroidHostTest --tests "*TrackRevisionStoreTest*" --tests "*CurrentTrackSelectionTest*"` BUILD SUCCESSFUL (30 lifecycle cases + selection suite, both stores).
- **Task 2:** `:shared:testAndroidHostTest --tests "*TrackRevisionStoreTest*" --tests "*TrackProfileReviewTest*"` BUILD SUCCESSFUL; full `:shared:check` BUILD SUCCESSFUL; `:androidApp:assembleDebug` BUILD SUCCESSFUL.
- iOS link/run tasks remain SKIPPED on Windows (as in every prior plan); iOS source compiles cleanly under `:shared:check`.
- Manual on-device verification of the rename field / Archive confirmation / Duplicate flow and history navigation remains a normal phase-level UAT checkpoint.

## Issues Encountered

- **Test fixture validity:** the initial sector-only-edit fixtures used `sectorsEnabled=true, sectorCount=2` with no boundaries, which `SchemaMigrations.validateCourseSetup` correctly rejects (needs `sectorCount - 1` boundaries). Fixed by adding a valid 1-boundary `sectorEnabledSetup()` helper. This was a test bug, not an implementation bug.
- **Scope note (not a stub):** a duplicated profile is an independent V2 aggregate discoverable via `listActiveProfiles` / the Drive profile selector, but it does NOT appear as a row in the Review LIST, which is still derived from the V1 `ReviewIndex`. Surfacing V2-only profiles in the Review list is outside this plan's write set and belongs to the broader Review/selector wiring. No placeholder data flows to the UI.
- Pre-existing `!!`/always-true warnings in unrelated lap/review test files were left untouched (out of scope).

## Known Stubs

- None. All lifecycle operations are wired to real persistence; no hardcoded empty/placeholder data flows to the UI. The duplicate-not-in-Review-list item above is a documented scope boundary (an independent profile is still created and persisted), not a stub.

## Threat Flags

- None new. Task 1 mitigates T-05-16 (lifecycle id spoofing: unsafe profile/revision ids and names are rejected before any path is built), T-05-17 (compatibility classification reuses the proven D-15 identity comparison), and T-05-18 (archive/duplicate are atomic, deletion-free, with independent ids and retained exact-history loads). No security surface beyond the plan's `<threat_model>`.

## Self-Check: PASSED

- All 4 created/modified files verified present on disk.
- All three task commits verified in git log: `96519d5` (RED), `d0e5ecf` (Task 1 GREEN), `a4f86e8` (Task 2).
- `:shared:check` (all shared tests incl. `TrackRevisionStoreTest`/`TrackProfileReviewTest`/`CurrentTrackSelectionTest`) and `:androidApp:assembleDebug` are BUILD SUCCESSFUL; iOS compiles (link/run SKIPPED on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
