---
phase: 6
slug: external-gnss-and-sensor-ingestion
status: approved
nyquist_compliant: true
hardware_validation: deferred
created: 2026-07-07
---

# Phase 6 - Validation Strategy

Per-phase validation contract for the protocol-first external GNSS preview.
This phase can be accepted as **protocol-complete, hardware-unvalidated** only.

## Test Infrastructure

| Property | Value |
|----------|-------|
| Framework | Kotlin `kotlin.test` / JUnit through KMP host tests |
| Quick run command | `./gradlew :shared:testAndroidHostTest` |
| Android build command | `./gradlew :androidApp:assembleDebug` |
| Connected device tests | Not part of default validation; run only with explicit user approval on a disposable device/emulator |
| Hardware validation | Deferred until a real receiver or user feedback is available |

## Sampling Rate

- After every task commit: run the narrow test named by the task.
- After every plan wave: run `./gradlew :shared:testAndroidHostTest`.
- Before Phase 6 closeout: run `./gradlew :shared:testAndroidHostTest :androidApp:assembleDebug`.
- Do not run destructive Android connected tests, `adb uninstall`, or `pm clear`
  unless the user explicitly authorizes that exact data-destructive action.

## Per-Plan Verification Map

| Plan | Wave | Requirements | Main Verification | Hardware Boundary |
|------|------|--------------|-------------------|-------------------|
| 06-01 | 1 | EXT-01, EXT-02, EXT-03 | `Nmea0183ParserTest`, `ExternalGnssReplayProviderTest`, full shared host suite | NMEA parser/replay only; no receiver claim |
| 06-02 | 2 | EXT-01, EXT-03 | `RaceBoxFrameParserTest`, `*RaceBox*` host tests | Synthetic RaceBox corpus only; clean-room implementation |
| 06-03 | 3 | EXT-01, EXT-02 | `ExternalGnssSettingsTest`, shared host suite, Android debug build | BLE shell can be injection-tested, not hardware-proven |
| 06-04 | 4 | EXT-01, EXT-02, EXT-03 | `ExternalGnssTimingPipelineTest`, full shared host suite, Android debug build | Closeout must say protocol-complete, hardware-unvalidated |

## Requirement Coverage

| Requirement | Coverage |
|-------------|----------|
| EXT-01: connect external GNSS | 06-01 provides normalized protocol/replay foundation; 06-02 adds RaceBox parser; 06-03 adds Android provider/source UX. Real connection remains unvalidated without hardware. |
| EXT-02: prefer external GNSS over phone GPS | 06-03 adds selectable External GNSS source and timing-source locking semantics; 06-04 verifies timing pipeline provenance. |
| EXT-03: ingest basic IMU or vehicle telemetry when available | Scoped by D-09: 06-01 models optional telemetry metadata, 06-02 parses/carries basic telemetry only when protocol fields are available, 06-04 verifies capture/provenance and documents that telemetry is not timing input. |

## Manual-Only Verifications

| Behavior | Why Manual | Status |
|----------|------------|--------|
| RaceBox Mini/Mini S/Micro real BLE service discovery and notifications | Requires physical receiver and firmware-specific behavior | Deferred |
| Real NMEA-over-BLE/TCP receiver connection | Requires receiver/network endpoint | Deferred |
| On-track precision improvement claim | Requires receiver, closed course, video/timing reference | Deferred |
| IMU/vehicle telemetry fidelity | Requires real protocol frames and receiver model | Deferred |

## Plan Gate Results

- Phase directory has 4 plans and 0 summaries: correct for pre-execution state.
- `gsd-sdk query check.decision-coverage-plan ...` passed after context/plan fixes:
  9/9 trackable Phase 6 decisions are covered by plans.
- Requirements are assigned across plans, including the narrowed EXT-03 telemetry
  boundary.
- The plan set has no checkpoint prompts and is executable wave-by-wave.

## Validation Sign-Off

- [x] All plans have automated verification commands or explicit non-automatable hardware boundaries.
- [x] No plan claims real hardware validation.
- [x] No connected/device-destructive test is required by default.
- [x] RaceBox implementation is clean-room; GPL/unlicensed code is research signal only.
- [x] `LocationSampleProvider` remains the ingestion seam.
- [x] EXT-03 is scoped to optional telemetry capture/provenance, not timing fusion.

**Approval:** approved for execution as a protocol-first compatibility preview.
