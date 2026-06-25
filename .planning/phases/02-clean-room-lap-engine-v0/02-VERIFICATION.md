# Phase 2 Verification: Clean-Room Lap Engine V0

**Date:** 2026-06-25
**Platform run on:** Windows 11 (Git Bash + Gradle wrapper)

## Automated Checks

| Command | Result | Notes |
|---|---|---|
| `.\gradlew.bat :shared:check` | PASS (exit 0) | Android host tests pass; iOS simulator tasks SKIPPED on Windows (expected). Only non-fatal `!!` style warnings emitted. |
| `.\gradlew.bat :androidApp:assembleDebug` | PASS (exit 0) | Debug APK produced at `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. |

### Test Coverage Summary

All lap engine tests run on the `androidHostTest` JVM target via `:shared:check`.

| Test class | Focus | Status |
|---|---|---|
| `LapModelsTest` | Config defaults, lenient test config, model invariants, sector ordering | PASS |
| `GeometryTest` | Projection sanity/reversibility, segment intersection cases, interpolation, heading | PASS |
| `CrossingDetectorTest` | Both directions, low-frequency, near-miss, sector identity, statelessness | PASS |
| `LapEngineTest` | First/second crossing, best lap, cooldown, min lap, accuracy, speed, direction, sector splits/dedup/reset, reset | PASS |
| `ReplayTest` | Determinism, one-lap, multi-lap counters, jitter, low-frequency, wrong-direction, poor-accuracy, sector splits (GPS-05, LAP-06) | PASS |
| `LapDashStateTest` | Lap-time formatting, demo session lifecycle, sector summaries | PASS |

iOS simulator test execution requires macOS/Xcode and is **deferred**; the shared
code compiles to the iOS klib targets as part of the multiplatform `commonTest`
compilation, but Kotlin/Native test binaries cannot run on Windows.

## Manual Android UAT

Status: **Deferred to human verification.** The debug APK builds successfully but
on-device smoke testing was not performed by the automated executor.

Suggested manual steps on an attached Android device:

1. Install `androidApp-debug.apk`.
2. Launch the app; confirm portrait dash readability and visible safety/accuracy copy.
3. Tap **Start Timing**; confirm the current lap timer advances.
4. After the replay crosses the start/finish line again, confirm **Last**, **Best**, and **Laps** update.
5. Confirm at least one sector split appears in the SECTORS readout during a lap.
6. Tap **Stop Timing**, then **Reset**; confirm fields clear.
7. Rotate to landscape and repeat the readability/control smoke test.
8. Check logcat for crash markers.

## Future iOS UAT

Deferred to a macOS/Xcode environment:

1. Build and launch the iOS app.
2. Confirm the shared dash renders.
3. Confirm the demo lap timing path advances if the Compose iOS runtime is healthy.

## Requirement Coverage

| Requirement | Evidence |
|---|---|
| GPS-05 | `ReplayRunner` + `ReplayFixtures`; `ReplayTest` replays sessions through the engine. |
| LAP-01 | `StartFinishLine` two-point model; `LapTimingState.initial`. |
| LAP-02 | `CrossingDetector` + `SegmentGeometry.intersectMovementWithLine`; `CrossingDetectorTest`. |
| LAP-03 | `SegmentGeometry.interpolateTimestamp`; `GeometryTest`, crossing timestamp assertions. |
| LAP-04 | `LapEngineConfig` filters in `LapEngine`; `LapEngineTest` gate cases. |
| LAP-05 | `LapDashState` + dash UI; `LapDashStateTest`. |
| LAP-06 | `ReplayRunner` headless replay; `ReplayTest`. |
| LAP-07 | `SectorLine`, sector detection, per-lap splits, dash readout; `LapEngineTest`/`ReplayTest`/`LapDashStateTest`. |
| SAFE-04 | Header copy: "not pro-grade timing"; control copy: simulator/replay status. |
| ARCH-02 | Geometry, crossing, interpolation, filters, sector, replay tests all present and passing. |

## Known Limitations

- Lap timing is simulator/replay-backed; no real GPS provider yet.
- Direction gating learns the expected crossing sign from the first accepted crossing; a symmetric course needs `enforceDirection = false`.
- Equirectangular projection is track-scale only; accuracy degrades far from the session origin.
- No persistence, ghost/delta, maps, external GNSS, or glasses bridge in this phase (by design).
