---
quick_id: 260702-uju
status: complete
completed: 2026-07-03
code_commit: ed5e81b
---

# Quick Task 260702-uju Summary

## Result

Completed the Track editor field-test hardening pass.

## Changes

- Reworked `CourseProfileEditor` to support relative course-progress dragging for start/finish and sector boundaries.
- Replaced the Track editor's conflicting tap + drag gesture stack with a single handle-driven gesture model:
  - first touch creates start/finish only when missing;
  - existing start/finish and sector handles move by relative progress along the recorded course;
  - pointer input no longer restarts on every editor state update.
- Improved the editor circuit rendering with thicker outer strokes, bright inner strokes, rounded caps, and larger active handles.
- Added visible sector boundary labels after sector count selection.
- Added Review-local refresh after rename, duplicate, archive, and revision-save actions so the list/trace re-read storage immediately.
- Allowed V2-only duplicated profiles to expose the same course-editing path as legacy-backed tracks.
- Added regression coverage for relative dragging, duplicated profile Review visibility, and V2-only profile lifecycle operations.

## Verification

- `.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProfileEditorTest*" --tests "*TrackProfileReviewTest*"` passed.
- `.\gradlew.bat :androidApp:assembleDebug` passed.
- `.\gradlew.bat :shared:testAndroidHostTest` passed.

## Notes

- Existing user-created/untracked `.planning/ui-reviews/` was not touched.
- This stays inside Phase 5/5.1 hardening scope and does not change lap-engine timing semantics.
