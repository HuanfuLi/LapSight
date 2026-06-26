---
phase: 04-ghost-lap-live-delta
plan: 04
subsystem: ghost-delta
tags: [kotlin-multiplatform, gps-fixtures, simulated-gps, ghost, live-delta, uat, tdd]

# Dependency graph
requires:
  - phase: 04-ghost-lap-live-delta
    plan: 03
    provides: Mounted-phone live delta UI and SessionController.ingestSample()/timingRunSnapshot()
provides:
  - GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT — deterministic variable-pace oval fixture
  - Provider-layer continuous replay coverage through SimulatedGpsProvider
  - Full normal timing path test for provider-starts-before-session ghost UAT
  - Phase 4 Android/device UAT notes
affects: [phase 4 verification, phase 5 track usability planning]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Provider-layer UAT: simulated GPS starts independently and timing consumes the stream later through SessionController.ingestSample"
    - "Variable-pace deterministic replay: fixed loop durations make positive delta, negative delta, and same-session new-best observable without real driving"
    - "Simulated reference isolation: UAT references persist only in the simulated slot after explicit Save"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/fixtures/GhostVariablePaceFixtureTest.kt
    - .planning/phases/04-ghost-lap-live-delta/04-UAT.md
  modified:
    - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibrary.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/SimulatedGpsProviderTest.kt
    - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/session/TimingGhostIntegrationTest.kt

key-decisions:
  - "VARIABLE_PACE_GHOST_UAT is intentionally not added to requiredScenarioIds; the Phase 3 D-04 six-scenario contract remains unchanged"
  - "The variable-pace fixture appends a final top-anchor sample so loop durations can be derived exactly from repeated GPS positions"
  - "The integration test starts the provider before timing and consumes only the remaining stream after Start Timing, matching the intended user/UAT flow"
  - "The variable-pace integration track uses a synthetic start/finish line over the fixture's top anchor and normal LapEngine gates; no special ghost workflow is introduced"

patterns-established:
  - "Pattern: deterministic provider fixtures should be consumed through the same public sample boundary used by real GPS, then asserted through SessionController snapshots/state"

requirements-completed: [GHOST-01, GHOST-02, GHOST-03, GHOST-04]

# Metrics
duration: 45min
completed: 2026-06-26
---

# Phase 4 Plan 04: Variable-Pace Simulator, UAT, and Roadmap Reminder Summary

**Added a deterministic variable-pace simulated GPS scenario and proved the full Phase 4 ghost flow through the normal timing business path: the provider starts before timing, Start Timing consumes the live stream, live delta changes sign, an in-session new best immediately becomes the next reference, and simulated references persist only after Save in the simulated slot.**

## Performance

- **Started:** 2026-06-26T00:00:00-04:00 handoff/resume
- **Completed:** 2026-06-26T00:37:00-04:00
- **Tasks:** 4
- **Files:** 2 created, 3 modified

## Accomplishments

- Added RED Wave 0 tests for `GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT`, validating stable ID, deterministic samples, simulated source metadata, repeated loop anchors, exact loop durations, and provider wrap continuity.
- Implemented `VARIABLE_PACE_GHOST_UAT` with five deterministic oval loops: `24s`, `27s`, `22s`, `20s`, `23s`, plus a closing anchor sample.
- Verified `SimulatedGpsProvider` can replay the variable-pace scenario continuously with strictly increasing timestamps and no session coupling.
- Extended `TimingGhostIntegrationTest` to start the provider before timing, then replay the remaining samples through `SessionController.startTiming()` + `SessionController.ingestSample()`.
- Asserted full ghost behavior through the production timing path: initial reference, negative/faster delta, positive/slower delta, immediate same-session new-best reference update, pre-save non-persistence, post-save simulated persistence, and real-reference isolation.
- Created `04-UAT.md` covering ADB/manual UAT: Start Demo Feed, verify continuous pre-timing feed, normal Start Timing, observe `--` / positive / negative delta, same-session reference update, Stop/Save, Review Demo labeling, and closed-course safety boundary.
- Confirmed ROADMAP already contains the post-MVP telemetry reminder for progress-curve-derived charts/data.

## Task Commits

Each task was committed atomically:

1. **Task 1: Wave 0 tests for variable-pace ghost UAT fixture** — `ea01cad` (test)
2. **Task 2: Implement variable-pace simulator scenario** — `8d7614c` (feat)
3. **Task 3: Verify normal timing business path** — `03d1e56` (test)
4. **Task 4: Add Phase 4 UAT notes** — `a64ba2f` (docs)

_TDD gate: RED `ea01cad` introduced failing tests for the missing fixture; GREEN `8d7614c` implemented the fixture and provider scenario._

## Files Created/Modified

- `shared/.../fixtures/GhostVariablePaceFixtureTest.kt` (new) — deterministic fixture contract for stable ID, loop durations, repeated geometry anchors, and simulated source.
- `shared/.../fixtures/GpsFixtureLibrary.kt` — added `VARIABLE_PACE_GHOST_UAT`, `variablePaceGhostUat()`, and private variable-pace oval generation.
- `shared/.../SimulatedGpsProviderTest.kt` — added provider continuity test for the variable-pace scenario.
- `shared/.../session/TimingGhostIntegrationTest.kt` — added full normal-flow UAT integration coverage using `SessionController.ingestSample()`.
- `.planning/phases/04-ghost-lap-live-delta/04-UAT.md` (new) — Android ADB/manual UAT checklist and safety boundary.

## Decisions Made

- Kept `VARIABLE_PACE_GHOST_UAT` out of `requiredScenarioIds` so the existing six Phase 3 fixture IDs remain a stable contract.
- Used exact loop durations instead of random pace variation so UAT can deterministically exercise slower/faster/new-best behavior.
- In the integration test, consumed 12 samples before Start Timing to prove the provider is independent and already moving before the timing session begins.
- Used normal `SessionController.ingestSample()` for replay; no test-only ghost shortcut or UI-only simulator workflow was added.

## Deviations from Plan

None. `SimulatedGpsProvider.kt` did not require production changes because its existing scenario constructor and wrap logic already supported arbitrary fixture IDs and continuous replay.

## Issues Encountered

- The start/finish line for the variable-pace fixture has samples exactly on the line at loop anchors. The integration test uses realistic minimum lap duration/cooldown gates with direction disabled so duplicate on-line sample transitions are rejected like production timing would reject jitter near the line.
- Windows cannot execute iOS simulator tests; `:shared:check` skips iOS simulator execution as expected while compiling iOS source sets.

## Verification

Passed:

```powershell
.\gradlew.bat :shared:testAndroidHostTest --tests "*GhostVariablePaceFixtureTest*" --tests "*SimulatedGpsProviderTest*"
.\gradlew.bat :shared:testAndroidHostTest --tests "*TimingGhostIntegrationTest*" --tests "*GhostVariablePaceFixtureTest*"
.\gradlew.bat :shared:testAndroidHostTest
.\gradlew.bat :shared:check
.\gradlew.bat :androidApp:assembleDebug
```

## Known Stubs

None for Phase 4. Map ghost animation, telemetry charts, coordinate-nearest matching, external GNSS, and Meta glasses HUD remain intentionally deferred.

## Threat Flags

None open. T-04-08/T-04-09/T-04-12 mitigations are covered:

- deterministic tests pin the variable-pace fixture and loop order;
- UAT uses provider-layer simulation and does not require public-road driving;
- simulated reference persistence is isolated from real reference persistence.

## User Setup Required

For UAT, install the debug APK on Android and follow `.planning/phases/04-ghost-lap-live-delta/04-UAT.md`. ADB can install/launch and collect screenshots/XML, but visual confirmation is still required for moving delta values.

## Next Phase Readiness

- Phase 4 is ready for `$gsd-verify-work 4`.
- GHOST-01 through GHOST-04 are implemented and covered by deterministic shared tests.
- Post-MVP telemetry/chart ideas derived from progress curves remain captured in ROADMAP backlog.

## Self-Check: PASSED

All created/modified files exist. Task commits (`ea01cad`, `8d7614c`, `03d1e56`, `a64ba2f`) are present in git history. Targeted tests, full shared host tests, `:shared:check`, and `:androidApp:assembleDebug` are green.

---
*Phase: 04-ghost-lap-live-delta*
*Completed: 2026-06-26*
