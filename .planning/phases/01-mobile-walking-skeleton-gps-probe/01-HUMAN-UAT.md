---
status: partial
phase: 01-mobile-walking-skeleton-gps-probe
source:
  - 01-VERIFICATION.md
started: 2026-06-25
updated: 2026-06-25
---

# Human UAT: Phase 1

## Current Test

Awaiting device/Xcode runtime verification.

## Tests

### 1. Android launch and layout

expected: `androidApp-debug.apk` installs and launches; dash is readable in portrait and landscape.
result: pending

### 2. Android probe controls

expected: Start Probe updates simulated speed/accuracy/rate/elapsed/sample count; Stop freezes; Reset clears.
result: pending

### 3. iOS launch and layout

expected: Opening `iosApp/` in Xcode on macOS builds and launches the shared LapSight dash.
result: pending

### 4. Safety copy visibility

expected: Closed-course and phone GPS accuracy limitation text is visible without digging into settings.
result: pending

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps

None yet — awaiting human runtime testing.
