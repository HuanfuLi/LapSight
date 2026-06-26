---
phase: 03-local-sessions-review-and-export
plan: 05
subsystem: ui
tags: [compose-multiplatform, kmp, ui, track-marking, bottom-nav, tdd]

# Dependency graph
requires:
  - phase: 03-local-sessions-review-and-export
    provides: SimulatedGpsProvider (Plan 03-01), versioned local storage (Plan 03-03), ReferenceLineExtractor + TrackReviewState (Plan 03-04)
provides:
  - AppShell — three-tab Drive/Review/Settings bottom navigation with fullscreen mounted-dash mode
  - DriveMarkingController — pure-Kotlin Mark New Track capture state machine
  - DriveScreen — Mark New Track capture + Track Review save/re-record/discard surface
  - ReviewScreen — saved Track/marking list with DEMO badges and empty state
  - ReviewListState — pure-Kotlin row view model derived from ReviewIndex
  - Clock expect/actual + InMemorySessionStore — defaults for previews/tests/entrypoints
affects: [03-06 timing UI, 03-07 trace UI, 03-08 export UI]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure-Kotlin controllers (DriveMarkingController) extracted from Compose so the marking state machine is unit-testable without a Compose runtime"
    - "Locally-built Material ImageVectors via PathParser for glyphs missing from the approved material-icons-core 1.7.3 (Speed, History) — avoids forbidden material-icons-extended"
    - "expect/actual Clock + injectable LocalSessionStore default (InMemorySessionStore) so App() and @Preview run without platform storage init"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/AppShell.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/AppIcons.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewListState.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/Clock.kt
    - shared/src/androidMain/kotlin/com/huanfuli/lapsight/shared/Clock.android.kt
    - shared/src/iosMain/kotlin/com/huanfuli/lapsight/shared/Clock.ios.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingControllerTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/ui/TrackReviewFlowTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/App.kt
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.kt
    - shared/src/androidMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.android.kt
    - shared/src/iosMain/kotlin/com/huanfuli/lapsight/shared/storage/StoragePaths.ios.kt
    - androidApp/src/main/kotlin/com/huanfuli/lapsight/MainActivity.kt

key-decisions:
  - "DriveMarkingController is plain Kotlin (no Compose) so the Mark New Track capture state machine is unit-testable; DriveScreen is a thin wrapper over its snapshot"
  - "ReviewListState derives row view models from ReviewIndex in pure Kotlin so Review list rendering and Demo provenance are testable without Compose"
  - "Custom ImageVectors for Speed/History glyphs reproduce canonical Material path data; the approved material-icons-core 1.7.3 ships Settings but not Speed/History (those are in the forbidden material-icons-extended)"
  - "InMemorySessionStore mirrors FileSessionStore payload-before-index + upsert semantics so previews/tests/entrypoints behave identically against either backend"
  - "Clock expect/actual is the single platform seam for save timestamps; injectable in DriveMarkingController for deterministic tests"

patterns-established:
  - "Pure-Kotlin controller + Composable snapshot pattern for stateful UI surfaces"
  - "MainActivity wires StoragePaths.initialize(context) + FileSessionStore before App(); App() defaults to InMemorySessionStore so previews need no platform storage"

requirements-completed: [SESS-02]

# Metrics
duration: 25min
completed: 2026-06-25
---

# Phase 3 Plan 05: Three-Tab Shell, Mark New Track, and Track Review UI Summary

**A three-tab Drive/Review/Settings shell with a Mark New Track capture flow that runs the normal SimulatedGpsProvider feed into ReferenceLineExtractor, produces a TrackReviewState for explicit Save / Re-record / Discard, and surfaces saved Demo tracks in Review with visible DEMO provenance — Start Timing stays blocked until a saved Track has a confirmed start/finish line.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-06-25
- **Tasks:** 2 (TDD: 2 RED test commits + 2 GREEN implementation commits)
- **Files modified:** 17 (12 created, 5 modified)

## Accomplishments
- `AppShell` three-tab bottom navigation (Drive / Review / Settings) over the existing dark theme, with fullscreen mounted-dash mode that hides bottom navigation on Drive (D-29) and an app-wide window orientation lock driven only by the explicit Drive toggle (no sensor rotation).
- `DriveMarkingController` — pure-Kotlin Mark New Track capture state machine over `LocationSampleProvider`: begins continuous capture, ticks samples, stops into a `TrackReviewState` via `ReferenceLineExtractor` (D-06..D-11), confirms start/finish from the reference line, saves through `LocalSessionStore.saveTrackBundle()`, re-records, and discards. Start Timing is blocked until a saved Track has a confirmed start/finish (D-19) with the exact 03-UI-SPEC copy.
- `DriveScreen` renders the demo feed panel, Mark New Track capture, and Track Review with Save Track / Set start/finish / Re-record / Discard using exact 03-UI-SPEC confirmation dialogs. Track Review never shows lap times for marking samples (D-08).
- `ReviewScreen` lists saved Tracks/markings with visible DEMO badges and source metadata (D-27, D-28, D-42), re-reads the index when Drive saves, and shows the empty state when nothing is saved.
- `ReviewListState` derives row view models from `ReviewIndex` in pure Kotlin so Demo provenance and source labels are testable without Compose.
- `Clock` expect/actual + `InMemorySessionStore` defaults so `App()` and `@Preview` run without platform storage init; `MainActivity` wires `StoragePaths.initialize(this)` + `FileSessionStore`.
- Wave 0 tests cover D-06..D-12, D-19, D-27, D-28, D-42; `:shared:testAndroidHostTest` passes; `:androidApp:assembleDebug` succeeds. No new dependency added.

## Task Commits

Each task was committed atomically (TDD):

1. **Task 1 RED: Add failing marking-capture controller tests** - `b70c428` (test)
2. **Task 1 GREEN: Three-tab shell and Mark New Track capture** - `873bd36` (feat)
3. **Task 2 RED: Add failing Track Review save flow tests** - `0d88695` (test)
4. **Task 2 GREEN: Track Review save flow and Review list rows** - `2190d40` (feat)

## Files Created/Modified
- `.../ui/AppShell.kt` - Three-tab Scaffold + NavigationBar with fullscreen mode and orientation lock.
- `.../ui/AppIcons.kt` - Custom ImageVectors for Speed/History (approved material-icons-core 1.7.3 omits these).
- `.../ui/DriveScreen.kt` - Demo feed panel, Mark New Track capture, Track Review Save/Re-record/Discard.
- `.../ui/DriveMarkingController.kt` - Pure-Kotlin marking state machine; snapshot-driven Compose binding.
- `.../ui/ReviewScreen.kt` - Saved rows with DEMO badges, empty state, inline detail summary.
- `.../ui/ReviewListState.kt` - Pure-Kotlin row view model from ReviewIndex.
- `.../shared/Clock.kt` (+ android/ios actuals) - `nowEpochMillis()` expect/actual.
- `.../storage/InMemorySessionStore.kt` - Default store for previews/tests.
- `.../storage/StoragePaths.kt` (+ android/ios actuals) - `fileSessionStore()` factory.
- `.../shared/App.kt` - Shrunk to theme + AppShell handoff.
- `androidApp/.../MainActivity.kt` - Wires StoragePaths + FileSessionStore before App().
- `.../ui/DriveMarkingControllerTest.kt`, `.../ui/TrackReviewFlowTest.kt` - Wave 0 coverage.

## Decisions Made
- **Pure-Kotlin controllers over Compose state holders:** `DriveMarkingController` and `ReviewListState` are plain Kotlin so the marking state machine, save/discard/re-record decisions, and Review row derivation are unit-testable without a Compose runtime. Composables are thin snapshot readers.
- **Custom ImageVectors instead of material-icons-extended:** The approved coordinate set (Plan 03-02) pins `material-icons-core` to `1.7.3`, which ships `Settings` but not `Speed`/`History`. `AppIcons.kt` reproduces the canonical Material path data via `PathParser` so no forbidden dependency is added.
- **InMemorySessionStore as the App() default:** Previews, tests, and entrypoints that don't call `StoragePaths.initialize` get a safe in-memory store that mirrors `FileSessionStore`'s payload-before-index + upsert semantics; `MainActivity` injects a real `FileSessionStore`.

## Deviations from Plan

None — plan executed as written. Both tasks completed with the specified files, exact 03-UI-SPEC copy, and no new dependency. The supporting `Clock`, `InMemorySessionStore`, `AppIcons`, and `ReviewListState` files are required infrastructure for the UI to compile and run in previews/tests; they are within the plan's scope (the plan's `files_modified` lists the UI files and `App.kt`).

### Auto-fixed Issues

**1. [Rule 3] Unresolved `Modifier.width` import in ReviewScreen DetailLine**
- **Found during:** Task 2 GREEN verification (`:shared:compileAndroidMain`)
- **Issue:** `DetailLine` used `Modifier.width(72.dp)` but the `width` import was missing.
- **Fix:** Added `import androidx.compose.foundation.layout.width`.
- **Files modified:** shared/src/commonMain/.../ui/ReviewScreen.kt
- **Verification:** `:shared:testAndroidHostTest` BUILD SUCCESSFUL.
- **Committed in:** `2190d40`

## Issues Encountered
- The previous orchestrator attempt left a stranded, uncommitted worktree (`worktree-agent-a81cefed053aa527c`) with partial AppShell/App/InMemorySessionStore/Clock work but no DriveScreen, ReviewScreen, SUMMARY, or commits. It was removed and the plan was re-executed cleanly from the main checkout.

## Known Stubs
- Review row detail is an inline expanded summary for this plan; full Track Review and Timing Session Review detail screens arrive in Plans 03-06 and 03-07.

## User Setup Required
None - `MainActivity` wires `StoragePaths.initialize(this)` automatically before `App()`.

## Next Phase Readiness
- Plan 03-06 (timing session lifecycle) can bind `DriveMarkingController.canStartTiming` to the real `SessionController.startTiming(trackId)`, and Drive's Start Timing button becomes the trigger once a saved Track exists.
- ReviewScreen's row detail hook is in place for 03-06/03-07 to replace the inline summary with full Track Review and Timing Session Review detail screens.
- `InMemorySessionStore` and `FileSessionStore` both implement `LocalSessionStore`; the timing draft persistence in 03-06 extends this interface.

## Self-Check: PASSED

All 12 created files and 5 modified files verified on disk; commits `b70c428`, `873bd36`, `0d88695`, `2190d40` verified in git log. `:shared:testAndroidHostTest` BUILD SUCCESSFUL (host tests pass); `:androidApp:assembleDebug` BUILD SUCCESSFUL.

---
*Phase: 03-local-sessions-review-and-export*
*Completed: 2026-06-25*
