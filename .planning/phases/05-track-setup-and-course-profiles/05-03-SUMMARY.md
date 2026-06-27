---
phase: 05-track-setup-and-course-profiles
plan: 3
subsystem: storage
tags: [kotlin-multiplatform, current-selection, no-fallback, course-profiles, drive-controller, typed-resolution]

# Dependency graph
requires:
  - phase: 05-track-setup-and-course-profiles
    plan: 2
    provides: LocalSessionStore V2 profile contract (saveProfile/loadProfile/listActiveProfiles), profiles-index commit marker, SchemaMigrations.migrateTrack mapping
provides:
  - LocalSessionStore.loadCurrentSelection / setCurrentSelection / clearCurrentSelection contract
  - FileSessionStore current-selection.json payload-first atomic persistence with unsafe-id rejection
  - InMemorySessionStore mirror of the current-selection contract
  - TrackProfileController.resolveCurrent + typed CurrentProfileResolution (Selected/None/Stale/Archived/Corrupt/NotTimingReady)
  - DriveMarkingController snapshot resolved via explicit selection (currentTrackName/needsTrackSelection/selectableProfiles) with no newest-Track fallback
affects: [05-04-profile-revisions, 05-07-complete-sector-timing, 05-11-session-bootstrap-preflight, 05-13-wrong-course-preflight]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Explicit current selection persisted as a single payload-first atomic file (current-selection.json), mirrored exactly in-memory"
    - "Typed fallback-free resolution: every unavailable case is a distinct CurrentProfileResolution, never a substituted profile"
    - "Defense in depth: an unsafe persisted profileId is rejected on write AND surfaced as Corrupt on load before any path is built"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackProfileController.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/storage/CurrentTrackSelectionTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingControllerTest.kt

key-decisions:
  - "loadCurrentSelection returns LoadResult<CurrentTrackSelection> (Loaded/NotFound/Corrupt) rather than a bespoke result type, reusing the established typed-load contract so the controller can distinguish absent vs corrupt selection"
  - "A selection naming a now-missing profile resolves to a distinct CurrentProfileResolution.Stale (added beyond the plan's example set) so the UI can say 'your track is gone' vs 'pick a track' (None)"
  - "saveTrack() promotes the just-saved Track to its V2 profile and sets it as the current selection (profileId == track.id via SchemaMigrations.migrateTrack), so the mark -> time flow keeps working under explicit selection with zero changes to SessionController.startTiming"
  - "Timing-ready means the profile's LATEST revision has a confirmed start/finish (D-14 + D-05); older revisions never re-open Timing"

patterns-established:
  - "Pattern: fallback-free current-selection resolution — read the exact persisted id, resolve only that profile's latest timing-ready revision, collapse every other situation into a typed unavailable state"

requirements-completed: [SC-01, SC-03]

# Metrics
duration: ~50min
completed: 2026-06-27
---

# Phase 5 Plan 03: Exact Current-Track Selection (No Fallback) Summary

**Persisted explicit current-Track selection with a typed, fallback-free resolver: a payload-first `current-selection.json` mirrored in both stores, a `TrackProfileController` that resolves only the exactly-named profile's latest timing-ready revision, and a Drive snapshot that drops the newest-Track heuristic for the explicit selection plus an active-profile selector.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 2 (Task 1 TDD RED -> GREEN; Task 2 combined test-inversion + adaptation)
- **Files:** 7 (2 created, 5 modified)

## Accomplishments
- Added `loadCurrentSelection` / `setCurrentSelection` / `clearCurrentSelection` to the `LocalSessionStore` contract and implemented them in both stores: the file store writes a single `current-selection.json` with the same sibling-temp/atomic-move payload-first pattern as every other payload; the in-memory store mirrors the identical observable behavior.
- Hardened the selection boundary against T-05-05: `setCurrentSelection` rejects a non-null unsafe `profileId` before any write, and `loadCurrentSelection` returns `Corrupt` for an unsafe persisted id so no `../`-escaped Okio path is ever built (proven by a raw-injection path-traversal test).
- Implemented `TrackProfileController.resolveCurrent()` returning a typed `CurrentProfileResolution` — `Selected` / `None` / `Stale` / `Archived` / `Corrupt` / `NotTimingReady` — that reads only the persisted selection and the exactly-named profile's latest timing-ready revision and NEVER derives a Track (D-01..D-04).
- Replaced the `DriveMarkingController.snapshot()` `maxByOrNull(createdAtEpochMillis)` newest-ready heuristic with the controller resolution; added `currentTrackName`, `needsTrackSelection`, and `selectableProfiles` (active profiles, latest revision only) so blocked Timing routes to an explicit selector.
- Wired `saveTrack()` to promote the saved Track to its V2 profile and set it as the current selection, keeping the existing mark -> save -> time flow intact (profileId equals the V1 track id, so `SessionController.startTiming` is unchanged).
- Inverted the former newest-ready regression test into D-01..D-04 / D-14 / D-16 assertions and proved no newest-Track derivation remains.

## Task Commits

1. **Task 1 RED — `test(05-3)`** - `f306a04` — contract additions + RED stubs in both stores + `TrackProfileController` + `CurrentTrackSelectionTest` (7 of 9 cases failing against the stubs).
2. **Task 1 GREEN — `feat(05-3)`** - `a162e6a` — real current-selection persistence in both stores; `CurrentTrackSelectionTest` green across file + in-memory.
3. **Task 2 — `feat(05-3)`** - `76a7aef` — Drive snapshot resolves via `TrackProfileController`, newest-Track heuristic removed, `saveTrack()` auto-selects, regression test inverted to D-01..D-04; `:shared:check` green.

## Files Created/Modified
- `track/TrackProfileController.kt` (created) - fallback-free `resolveCurrent()` and the typed `CurrentProfileResolution` sealed interface.
- `storage/CurrentTrackSelectionTest.kt` (created) - dual-store (file + in-memory) selection contract: persistence across relaunch-equivalent recreation, no-newest fallback, stale/archived/not-timing-ready/corrupt unavailability, clear, unsafe-id rejection, and path-traversal defense.
- `storage/LocalSessionStore.kt` (modified) - current-selection contract + `CurrentTrackSelection` import.
- `storage/FileSessionStore.kt` (modified) - `current-selection.json` path/constant, atomic load/set/clear, unsafe-id rejection on write and load.
- `storage/InMemorySessionStore.kt` (modified) - mirrored in-memory current-selection state and contract.
- `ui/DriveMarkingController.kt` (modified) - snapshot resolved via `TrackProfileController`; new `currentTrackName` / `needsTrackSelection` / `selectableProfiles` fields + `TrackProfileRow`; `saveTrack()` profile promotion + auto-selection.
- `ui/DriveMarkingControllerTest.kt` (modified) - newest-ready regression inverted into explicit-selection D-01..D-04 / D-14 / D-16 coverage.

## Decisions Made
- Reused `LoadResult<CurrentTrackSelection>` for `loadCurrentSelection` rather than introducing a bespoke result type, so the controller cleanly distinguishes `NotFound` (None) from `Corrupt`.
- Added a distinct `CurrentProfileResolution.Stale` for a selection that names a deleted profile (beyond the plan's example list of Selected/None/Archived/Corrupt/NotTimingReady) so the UI can differentiate a vanished current Track from never having chosen one. The plan worded the set as "such as", so this is an additive refinement, not a contract change.
- `saveTrack()` auto-selects the newly marked Track. This is required for the existing mark -> time flow to keep functioning once Timing readiness is driven solely by explicit selection; it uses the canonical `SchemaMigrations.migrateTrack` mapping so the profile identity is deterministic and `startTiming(profileId)` is byte-equivalent to the prior `startTiming(trackId)`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing critical functionality] saveTrack() did not establish a current selection**
- **Found during:** Task 2
- **Issue:** Once `snapshot()` resolves Timing readiness exclusively from the explicit current selection (D-03, no fallback), the existing mark -> save -> time flow would leave a freshly saved Track un-timable because nothing ever set it current. Two pre-existing tests depended on saving a Track unblocking Timing.
- **Fix:** `saveTrack()` now also persists the V2 profile via `SchemaMigrations.migrateTrack` and calls `setCurrentSelection`, making the just-saved Track the explicit current selection. `profileId == track.id`, so `SessionController.startTiming` is unchanged.
- **Files modified:** `ui/DriveMarkingController.kt`
- **Commit:** `76a7aef`

**2. [Rule 3 - Blocking] Worktree missing gitignored `local.properties` (Android SDK location)**
- **Found during:** Task 1 (first test run)
- **Issue:** `:shared:testAndroidHostTest` needs `local.properties` (`sdk.dir`); the parallel worktree does not inherit the main checkout's gitignored copy.
- **Fix:** Copied `local.properties` from the main checkout into the worktree (gitignored; NOT committed).
- **Committed in:** N/A (gitignored build config).

---

**Total deviations:** 2 (1 Rule 2 critical functionality, 1 Rule 3 blocking environment). No architectural change, no new dependency.

## TDD Gate Compliance
- **Task 1** followed the strict gate: RED commit `f306a04` (`test(...)`) lands `CurrentTrackSelectionTest` with both stores stubbed so 7 of 9 cases fail at runtime; GREEN commit `a162e6a` (`feat(...)`) implements persistence until the focused class is green.
- **Task 2** (`tdd="true"`) was committed as a single `feat(...)` (`76a7aef`) rather than separate RED/GREEN commits: the test inversion and the snapshot contract change are co-dependent — the new D-01..D-04 assertions reference new snapshot fields (`currentTrackName` / `needsTrackSelection` / `selectableProfiles`) that cannot exist before the controller change compiles. The behavior change (newest-ready heuristic removed, asserted absent) is fully covered by the inverted test, which is green.

## Issues Encountered
- None beyond the two deviations above. `:shared:check` is green; iOS test compilation succeeds (link/run skipped on Windows, as in prior plans). Pre-existing compiler warnings in unrelated files (lap/review tests, `ReviewScreen`, `StoragePaths` expect/actual) were left untouched (out of scope).

## Known Stubs
- None. The current-selection contract is fully implemented and exercised in both stores; the Drive selector surfaces real active-profile rows from `listActiveProfiles()`. The Compose `DriveScreen` selector UI affordance (rendering `selectableProfiles` / `needsTrackSelection` and a "Set as current track" action) is intentionally out of scope for this shared-logic plan and is owned by a later UI plan.

## Threat Flags
None — no security surface beyond the plan's `<threat_model>`. T-05-05 (validate exact opaque id, never fall back) and T-05-06 (typed unavailable state blocks Timing, explicit recovery action) are both implemented and tested.

## Next Phase Readiness
- `TrackProfileController.resolveCurrent()` and the persisted selection are ready for 05-11 session bootstrap (resolve current profile/revision/direction before `startTiming`) and 05-13 wrong-course preflight (operate on the resolved revision).
- `selectableProfiles` / `needsTrackSelection` / `currentTrackName` give the later Drive/Review UI plans everything needed to render the compact selector and a `Set as current track` action (D-02) without re-deriving selection in Compose.

## Self-Check: PASSED
- All 7 created/modified files verified present on disk.
- All three task commits verified in git log: `f306a04` (RED test), `a162e6a` (GREEN feat), `76a7aef` (Task 2 feat).
- `:shared:testAndroidHostTest` (CurrentTrackSelectionTest + DriveMarkingControllerTest) and full `:shared:check` BUILD SUCCESSFUL; iOS test compilation succeeds (run skipped on Windows as documented).

---
*Phase: 05-track-setup-and-course-profiles*
*Completed: 2026-06-27*
