---
quick_id: 260702-vn1
slug: unify-track-detail-trace-and-edit-course
status: complete
completed: 2026-07-03T02:53:32.581Z
code_commit: ee84fef
---

# Quick Task 260702-vn1 Summary

## Result

Unified the Track detail course map so the original `Trace` surface now uses the improved thick circuit styling, and `Edit course` switches that same map into edit mode instead of adding a second duplicate map.

## Changes

- Extracted the editor's circuit rendering into `TrackCourseMapCanvas`.
- Added browse/edit mode support to the shared canvas.
- Replaced the Track detail `Trace` + separate `TrackEditorScreen` pair with one `TrackCourseDetailSection`.
- Kept revision history and save/cancel controls below the same map.
- Preserved relative dragging for start/finish and sector boundaries.

## Verification

- `.\gradlew.bat :shared:testAndroidHostTest` passed.
- `.\gradlew.bat :androidApp:assembleDebug` passed.
- `.\gradlew.bat :androidApp:installDebug` installed on device `25053RT47C`.
- ADB launch confirmed current focus: `com.huanfuli.lapsight/com.huanfuli.lapsight.MainActivity`.

## Commits

- Code: `ee84fef fix(quick-260702-vn1): unify track detail course map`
