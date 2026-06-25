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

Android device runtime verification completed on a Pixel 10 Pro via ADB. iOS Xcode runtime verification remains pending.

## Tests

### 1. Android launch and layout

expected: `androidApp-debug.apk` installs and launches; dash is readable in portrait and landscape.
result: passed

evidence:
- Installed `androidApp/build/outputs/apk/debug/androidApp-debug.apk` with `adb install -r -d`.
- Launched `com.huanfuli.lapsight` on attached Pixel 10 Pro.
- Verified portrait UI dump at `rotation="0"` and landscape UI dump at `rotation="1"` after manually rotating the device.
- Fixed and re-tested compact landscape layout so Elapsed/Samples values remain fully visible at 2410x1080.

### 2. Android probe controls

expected: Start Probe updates simulated speed/accuracy/rate/elapsed/sample count; Stop freezes; Reset clears.
result: passed

evidence:
- In portrait, Start Probe changed the state to `SIMULATED GPS`, updated speed/accuracy/rate/elapsed/sample count, and changed the primary button to Stop Probe.
- Stop Probe returned the primary button to Start Probe while preserving sample metrics.
- Reset returned the dash to `IDLE`, cleared speed/accuracy/rate, reset elapsed to `00:00`, and reset samples to `0`.
- Repeated the Start/Stop/Reset flow in landscape at `rotation="1"`.
- No `FATAL EXCEPTION` / `E AndroidRuntime` entries were present for `com.huanfuli.lapsight`; the app process remained running after reset.

### 3. iOS launch and layout

expected: Opening `iosApp/` in Xcode on macOS builds and launches the shared LapSight dash.
result: pending

### 4. Safety copy visibility

expected: Closed-course and phone GPS accuracy limitation text is visible without digging into settings.
result: passed

evidence:
- Safety copy is visible in both portrait and landscape: `Closed-course timing aid. Phone GPS accuracy varies; verify before trusting lap data.`
- Phase 1 simulator/provider limitation copy is visible beside or below controls depending on orientation.

## Summary

total: 4
passed: 3
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps

iOS runtime testing remains pending because the current host is Windows. Run `iosApp/` on macOS/Xcode before marking Phase 1 fully complete.
