---
status: partial
phase: 02-clean-room-lap-engine-v0
source: [02-VERIFICATION.md]
started: 2026-06-25T00:00:00Z
updated: 2026-06-25T05:00:00Z
---

## Current Test

iOS runtime UAT (test 2) — pending macOS/Xcode.

## Tests

### 1. Android on-device smoke test of the demo lap session
expected: App launches in portrait; Start Timing advances Current Lap; after a replay re-crossing Last/Best/Laps update; a sector split appears in SECTORS; Stop/Reset clear fields; landscape stays readable; no crash in logcat.
result: passed — Verified on-device (HyperOS, model 25053RT47C) on 2026-06-25. Portrait launch clean; Start advanced Current Lap (00:05.5 → 00:10.5); replay completed 3 laps with Last 00:35.786 / Best 00:32.428; SECTOR 1 split 00:07.999 displayed; Reset cleared all fields to IDLE; landscape readable; no app crash/FATAL in logcat. During UAT a sensor-rotation defect was found and fixed (see Gaps G1).

### 2. iOS runtime UAT of the shared dash and demo lap path
expected: iOS app builds and launches on a simulator/device; the shared Compose dash renders; the demo lap timing path advances if the Compose iOS runtime is healthy.
result: [pending] — requires macOS/Xcode; cannot run on Windows.

## Summary

total: 2
passed: 1
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps

### G1. Dash auto-rotated from the accelerometer (unsafe for a mounted racing phone) — RESOLVED
status: resolved
found: 2026-06-25 during Android UAT
detail: The activity used `android:screenOrientation="fullSensor"`, so the mounted dash would flip from device tilt under cornering G-forces. Fixed in commit `6e3077a`: locked the activity orientation and added an in-app "Rotate" toggle backed by a sensor-independent `OrientationController` (Android uses fixed `SCREEN_ORIENTATION_PORTRAIT`/`LANDSCAPE`). Portrait dash also made scrollable so all controls stay reachable. Re-verified on-device: portrait↔landscape round-trip via the button, locked against system auto-rotate, no crash. See memory: orientation-no-sensor-rotation.
