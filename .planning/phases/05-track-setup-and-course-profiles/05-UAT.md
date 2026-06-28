---
status: testing
phase: 05-track-setup-and-course-profiles
source: [05-14-SUMMARY.md]
---

## Current Test
<!-- OVERWRITE each test - shows where we are -->

number: 1
name: Android Migration & Backward Compatibility
expected: |
  Start Android app on an older install (or after pushing V1 dummy data). Verify older V1 Tracks and sessions load perfectly. Ghosts from V1 continue to function on those tracks without breaking.
awaiting: user response

## Tests

### 1. Android Migration & Backward Compatibility
expected: Start Android app on an older install (or after pushing V1 dummy data). Verify older V1 Tracks and sessions load perfectly. Ghosts from V1 continue to function on those tracks without breaking.
result: pending

### 2. New Profile Creation
expected: Tap Mark New Track. Complete a lap to save. Enter a custom name. Select it from Drive screen. Verify no errors occur on fresh profile creation without legacy data.
result: passed

### 3. Track Selection & Direction Relaunch
expected: Select a Track. Choose "Reverse" direction. Restart app. Verify the exact track and "Reverse" direction persist and are re-selected without substituting the newest track automatically.
result: passed

### 4. Course Editing (Sector counts and limits)
expected: Go to Review, tap a Track -> Edit Course. Verify you can create a 2, 3, or 6-Sector layout successfully. Verify placing a Sector line at the very end works.
result: passed

### 5. Start/Finish vs Sector Compatibility
expected: Verify that editing ONLY Sector lines preserves Ghost compatibility with previous runs, whereas editing the Start/Finish line breaks compatibility and starts a new history epoch.
result: passed (via code inspection)

### 6. Profile Lifecycle (Archive, Duplicate, History)
expected: On Track detail, tap Archive. Verify it vanishes from Drive selector but remains in Review. Tap Duplicate. Verify an independent copy is made. Observe Revision history table shows immutable history of edits.
result: pending (Fixes for Duplicate/Rename applied in codebase, but UAT via ADB failed due to Compose LazyColumn scroll gesture limitations in the emulator. Needs human physical device verification.)

### 7. Far-Course Override & Review Badge
expected: Select a Track that is physically far away. Tap Start Timing. It blocks with a "far away" warning. Tap Override. Complete session and save. Go to Review. Verify a "Far-course override applied at start." badge is visible on the session detail.
result: pending

### 8. Value -> -- -> Value Excursion
expected: While timing a valid ghost session, physically drive far off-course (or simulate). Verify delta shows `--`. Return to course. Verify delta resumes with a value, and overall lap/raw counts never reset/lose progress.
result: pending

### 9. Safety Language
expected: Confirm that language strictly emphasizes closed-course/private-track use and passive moving-UI. No public-road driving is requested anywhere.
result: passed

### 10. macOS / iOS Cold Launch Gate
expected: (MAC ONLY) Launch iOS Simulator or build in Xcode. Select a track, kill app, restart. Verify selection persists. Make a course revision, kill app, restart. Verify revision persists. Note: Windows runtimes CANNOT pass this; leave unresolved unless on macOS.
result: pending

### 11. V2 Profile Timing Start Regression
expected: Select a Phase 5 V2 Track profile that has a confirmed start/finish, then tap Start Timing. Timing starts without asking the user to mark a new track.
result: passed (Android device and `CourseProfileIntegrationTest`; covers V2-only and V2-plus-stale-V1 storage)

### 12. Drive Fullscreen and Landscape Layout
expected: Start timing in portrait and landscape. The app hides system bars, bottom navigation, track/direction setup, branding, simulated-feed labels, and safety copy. All telemetry and Stop/orientation controls fit without scrolling or clipping.
result: passed (Android device; portrait and 2772x1280 landscape screenshots plus UI hierarchy with zero scrollable nodes)

### 13. Display Settings
expected: Settings exposes speed unit, timing/landscape fullscreen, screen-awake, speed-trace, and GPS-diagnostic controls. Change the speed unit, force-stop, and relaunch; the choice persists and Drive uses it.
result: passed (Android device cold relaunch; Android SharedPreferences and iOS NSUserDefaults implementations compile)

### 14. Draft Checkpoint and Recovery Responsiveness
expected: Run and recover a multi-lap draft without UI stalls or ANR. Recovery rebuilds timing state without rewriting the full draft for every historical sample.
result: passed (batched-checkpoint tests and Android device recovery; no ANR/FATAL log entries)

### 15. Edited V2 Profile Wins Over Legacy Data
expected: When a migrated V1 Track and a newer V2 Profile share an ID, Start Timing uses the latest V2 name, revision, geometry compatibility key, direction, and course setup.
result: passed (`CourseProfileIntegrationTest.startTimingPrefersLatestV2ProfileOverStaleLegacyTrack`)

## Summary

total: 15
passed: 10
issues: 0
pending: 5
skipped: 0

## Gaps
1. **Review List Out of Sync with V2 Profiles**: FIXED. Added lifecycle sync in ReviewListState to combine legacy indices with V2 profiles and merge duplicates based on source session id or profile id.
2. **Missing Name Length Limits**: FIXED. Added strict `require(name.length <= TrackProfile.MAX_NAME_LENGTH)` in `TrackProfileController.saveProfile` and `renameProfile`, preventing huge names from breaking the UI. (Tested via unit tests, ADB UI scroll blocked).
3. **Selected V2 Profile Could Not Start Timing**: FIXED. SessionController now resolves V2 profiles directly and prefers their latest revision over stale V1 Track data.
4. **Timing and Recovery ANR**: FIXED. Draft persistence is batched, recovery replay is write-free, controller snapshots read recorder memory, and file work runs off the UI dispatcher.
5. **Drive Layout Was Not Usable While Moving**: FIXED. Timing is a dedicated fullscreen telemetry surface; landscape and portrait fit without scrolling or clipped controls.
6. **Settings Tab Was an Empty Placeholder**: FIXED. Display controls are functional and persisted on Android and iOS.
