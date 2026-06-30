---
quick_id: 260629-wwd
status: planned
created_at: 2026-06-30T03:41:14.912Z
---

# Quick Task 260629-wwd: Phase 5.1 Field-Test Blocker Hardening

## Goal

Unblock Phase 5.1 Android field testing by relaxing the GPS Ready sample-rate floor from 1.0 Hz to 0.9 Hz, cleaning up only the pre-timing Drive page layout, and replacing the custom rotate icon with a standard material-icons-core candidate.

## Scope

- Change the aggregate Ready gate minimum sample rate to 0.9 Hz.
- Update Ready gate tests for the new boundary.
- Update Phase 5.1 UAT and field-log threshold text from 1.0 Hz to 0.9 Hz.
- Tidy only the pre-timing Drive page: reduce top dead space, make GPS/Ready status more legible, and make orientation a secondary action.
- Do not redesign or modify the full-screen Timing telemetry surface.
- Replace `RotateScreenIcon` with a standard icon from existing `material-icons-core`; do not add `material-icons-extended`.

## Verification

- `.\gradlew.bat :shared:testAndroidHostTest`
- `.\gradlew.bat :androidApp:assembleDebug`
- If an Android device is connected, install the debug build and launch it for manual smoke.

## Commit Plan

- Commit source/test/docs changes atomically.
- Commit quick planning artifacts and `.planning/STATE.md` separately.
