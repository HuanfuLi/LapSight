---
status: diagnosed
phase: 03-local-sessions-review-and-export
source:
  - .planning/phases/03-local-sessions-review-and-export/03-01-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-02-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-03-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-04-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-05-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-06-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-07-SUMMARY.md
  - .planning/phases/03-local-sessions-review-and-export/03-08-SUMMARY.md
started: 2026-06-25T22:28:52-04:00
updated: 2026-06-25T22:28:52-04:00
---

## Current Test

[testing paused — 3 items blocked by diagnosed gap]

## Tests

### 1. Cold launch Drive shell
expected: Fresh launch opens LapSight on the Drive tab, shows closed-course/passive-use safety copy, shows Drive/Review/Settings bottom navigation, and blocks timing until a saved track with start/finish exists.
result: pass
verified_by: ADB install/clear/start plus UIAutomator dump
artifacts:
  - .planning/phase3-uat-01-launch.png
  - .planning/tmp-lapsight-ui.xml

### 2. Continuous demo GPS feed
expected: Pressing Start Demo Feed starts simulated GPS through the normal provider path, shows DEMO — simulated GPS, updates speed/accuracy/sample/rate fields, and can keep running before a user starts marking or timing.
result: pass
verified_by: ADB tap on Start Demo Feed plus UIAutomator dump
artifacts:
  - .planning/phase3-uat-02-demo-feed.png

### 3. Track marking and review
expected: Pressing Mark New Track while the demo feed is running captures continuous samples; pressing Stop Marking opens Track Review with detected loops, accepted/rejected loop counts, sample/degraded counts, no lap times, and a required start/finish state.
result: pass
verified_by: ADB-driven 150-second simulated capture and UIAutomator dump
artifacts:
  - .planning/phase3-uat-03-track-review-ready.png

### 4. Save reviewed track and inspect Review/export
expected: After setting start/finish and saving, Review lists both the marking and saved track; opening the track detail shows canonical metadata, vector trace, and Export JSON handoff to the Android share sheet.
result: pass
verified_by: ADB taps through Save Track, Review tab, row expansion, and Export JSON
artifacts:
  - .planning/phase3-uat-04-track-saved-drive.png
  - .planning/phase3-uat-05-review-list.png
  - .planning/phase3-uat-06-track-detail-export.png

### 5. Saved track remains timing-ready and starts formal timing
expected: A saved Track with confirmed start/finish remains available to Drive after tab navigation and cold restart; Drive shows Start Timing, and tapping it starts the formal Timing surface using the real saved track id.
result: issue
reported: "ADB UAT found that Review can still see the saved Track, and app-private storage contains tracks/track-1782440616852.json plus index.json with a confirmed startFinish, but Drive returns to Mark New Track / 'Mark a track first...' after Review navigation and cold relaunch. The implementation also starts timing with track-dummy-N instead of the saved track id, so the formal timing flow cannot reliably start even when the button is visible immediately after save."
severity: blocker
verified_by: ADB tab navigation, share-sheet cancel, force-stop/relaunch, app-private storage inspection, and source inspection
artifacts:
  - .planning/tmp-lapsight-drive-lost-track.xml
  - .planning/tmp-lapsight-drive-after-relaunch.xml
  - .planning/tmp-lapsight-timing-started.xml
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt

### 6. Timing session lifecycle
expected: From a saved timing-ready track, the user can start timing, receive passive mounted-phone timing UI, stop, then explicitly Save Session or Discard; only saved sessions enter Review.
result: blocked
blocked_by: other
reason: "Blocked by Test 5. Start Timing cannot reliably load a saved Track from Drive, so a real timing draft cannot be created through the user flow."

### 7. Saved timing session review and export
expected: A saved timing session appears in Review with lap/sample metadata, vector trace, and JSON/GPX export through platform share handoff.
result: blocked
blocked_by: other
reason: "Blocked by Test 6. No timing session can be created from the current Drive user flow."

### 8. Draft recovery from app restart
expected: If the app restarts with an unfinished timing draft, the app surfaces Resume / Save / Discard recovery and never silently promotes a draft to Review history.
result: blocked
blocked_by: other
reason: "Blocked by Test 6. The current user flow cannot create a timing draft from a saved Track."

### 9. Settings safety copy
expected: Settings tab is reachable from bottom navigation and repeats the closed-course / phone-GPS-not-pro-grade safety language.
result: pass
verified_by: ADB tap on Settings tab plus UIAutomator dump
artifacts:
  - .planning/phase3-uat-07-settings.png
  - .planning/tmp-lapsight-settings.xml

## Summary

total: 9
passed: 5
issues: 1
pending: 0
skipped: 0
blocked: 3

## Gaps

- truth: "A saved Track with confirmed start/finish remains available to Drive after tab navigation and cold restart; Drive starts formal timing using the real saved track id."
  status: failed
  reason: "ADB UAT found that Review and app-private storage retain the saved Track, but Drive loses timing readiness after navigation/relaunch and uses a placeholder track-dummy-N id for Start Timing."
  severity: blocker
  test: 5
  root_cause: "DriveMarkingController derives canStartTiming only from an in-memory savedTracks list populated by saveTrack(), never hydrates saved Track rows from LocalSessionStore.readIndex()/loadTrack(), and DriveScreen calls SessionController.startTiming(trackId = \"track-dummy-$savedTrack\") instead of the real saved Track id. Review works because it reads the persisted index directly."
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveMarkingController.kt"
      issue: "savedTracks is a private in-memory list; snapshot() uses it for canStartTiming; no persisted-track hydration path exists."
    - path: "shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt"
      issue: "Start Timing calls SessionController.startTiming with track-dummy-N instead of a persisted track id."
    - path: "shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/ReviewScreen.kt"
      issue: "ReviewScreen re-reads sessionStore.readIndex(), explaining why Review shows data while Drive does not."
    - path: "ADB app-private storage"
      issue: "files/lapsight/index.json and files/lapsight/tracks/track-1782440616852.json exist and include startFinish, proving persistence is present."
  missing:
    - "Expose the latest timing-ready saved Track id/name from DriveMarkingController snapshot."
    - "Hydrate timing-ready saved tracks from LocalSessionStore on Drive controller initialization or tab entry/cold start."
    - "Start formal timing with the real saved Track id and surface a visible blocked state if SessionController.startTiming returns Blocked."
    - "Add plain Kotlin tests for persisted saved-track hydration and Start Timing id selection."
    - "Add/adjust Android UAT path to verify saved-track timing readiness survives Review navigation and cold relaunch."
  debug_session: ".planning/debug/phase3-drive-track-readiness.md"
