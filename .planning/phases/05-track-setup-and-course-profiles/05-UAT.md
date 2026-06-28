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
result: pending

### 3. Track Selection & Direction Relaunch
expected: Select a Track. Choose "Reverse" direction. Restart app. Verify the exact track and "Reverse" direction persist and are re-selected without substituting the newest track automatically.
result: pending

### 4. Course Editing (Sector counts and limits)
expected: Go to Review, tap a Track -> Edit Course. Verify you can create a 2, 3, or 6-Sector layout successfully. Verify placing a Sector line at the very end works.
result: pending

### 5. Start/Finish vs Sector Compatibility
expected: Verify that editing ONLY Sector lines preserves Ghost compatibility with previous runs, whereas editing the Start/Finish line breaks compatibility and starts a new history epoch.
result: pending

### 6. Profile Lifecycle (Archive, Duplicate, History)
expected: On Track detail, tap Archive. Verify it vanishes from Drive selector but remains in Review. Tap Duplicate. Verify an independent copy is made. Observe Revision history table shows immutable history of edits.
result: pending

### 7. Far-Course Override & Review Badge
expected: Select a Track that is physically far away. Tap Start Timing. It blocks with a "far away" warning. Tap Override. Complete session and save. Go to Review. Verify a "Far-course override applied at start." badge is visible on the session detail.
result: pending

### 8. Value -> -- -> Value Excursion
expected: While timing a valid ghost session, physically drive far off-course (or simulate). Verify delta shows `--`. Return to course. Verify delta resumes with a value, and overall lap/raw counts never reset/lose progress.
result: pending

### 9. Safety Language
expected: Confirm that language strictly emphasizes closed-course/private-track use and passive moving-UI. No public-road driving is requested anywhere.
result: pending

### 10. macOS / iOS Cold Launch Gate
expected: (MAC ONLY) Launch iOS Simulator or build in Xcode. Select a track, kill app, restart. Verify selection persists. Make a course revision, kill app, restart. Verify revision persists. Note: Windows runtimes CANNOT pass this; leave unresolved unless on macOS.
result: pending

## Summary

total: 10
passed: 0
issues: 0
pending: 10
skipped: 0

## Gaps

