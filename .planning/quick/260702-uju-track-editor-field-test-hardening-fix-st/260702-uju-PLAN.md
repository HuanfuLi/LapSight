---
quick_id: 260702-uju
slug: track-editor-field-test-hardening-fix-st
status: planned
created: 2026-07-03
---

# Quick Task 260702-uju: Track Editor Field-Test Hardening

## Goal

Fix the field-test blockers in Track setup and Review:

- Start/finish dragging must feel direct and must not conflict with tap placement.
- Sector timing boundaries must be visibly editable after choosing a sector count.
- Saving a revision must immediately refresh the visible track trace and revision history.
- Duplicating a track must immediately refresh the Review list and the duplicated V2 profile must be editable.
- Track illustrations should look like a clear motorsport circuit map instead of thin debugging lines.

## Scope

In scope:

- `TrackEditorScreen` gesture handling and rendering polish.
- `CourseProfileEditor` support needed for direct start/finish progress dragging.
- `ReviewScreen` state refresh and V2 profile editing paths.
- Focused tests for editor drag continuity and duplicate/edit Review helpers.

Out of scope:

- External GNSS, Meta glasses, map tiles, cloud sync.
- Lap engine timing semantics changes.
- Full redesign of the mounted moving Drive dash.

## Plan

1. Add pure editor APIs for dragging start/finish by progress delta and expose progress values needed by UI tests.
2. Replace conflicting tap + drag pointer handlers with a single gesture model:
   - Tap creates start/finish only when it is missing.
   - Dragging a selected handle moves it along the recorded course progress.
   - Sector boundaries remain draggable and visibly numbered.
3. Improve editor canvas rendering:
   - thicker dark outer stroke plus bright inner stroke for the closed course;
   - larger start/finish and sector handles;
   - selected/dragged handle emphasis.
4. Update Review state flow:
   - internal profile actions bump a refresh token;
   - duplicate/rename/archive/revision saves immediately reload Review rows;
   - V2-only duplicate profiles render trace and expose Edit course.
5. Add tests and run:
   - `.\gradlew.bat :shared:testAndroidHostTest --tests "*CourseProfileEditorTest*" --tests "*TrackProfileReviewTest*"`
   - `.\gradlew.bat :androidApp:assembleDebug`

## Acceptance Criteria

- Dragging start/finish along the track changes progress by the drag distance instead of barely moving.
- Tapping after start/finish exists does not unexpectedly replace it.
- Sector handles are clear, numbered, and draggable.
- Revision save updates the displayed trace/history without app restart.
- Duplicate track appears in Review immediately and has Edit course available.
- Build and focused host tests pass.
