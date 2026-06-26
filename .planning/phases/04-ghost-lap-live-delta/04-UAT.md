---
phase: 04-ghost-lap-live-delta
plan: 04
status: ready-for-uat
created: 2026-06-26
updated: 2026-06-26
source: 04-04-PLAN.md
---

# Phase 4 UAT — Ghost Lap + Live Delta

## Scope

Validate the Phase 4 product flow with provider-layer simulated GPS:

- Demo GPS feed runs continuously before timing starts.
- Normal Start Timing consumes whatever provider samples arrive after the user starts timing.
- Delta initially shows `--`, then shows value-only positive/negative seconds.
- A same-session new best silently becomes the next reference.
- Stop/Save persists the simulated reference only in the simulated slot.
- Review remains visibly Demo/Simulated for simulated artifacts.

No map ghost animation, telemetry chart, or special ghost-test workflow is required in Phase 4.

## Automated Coverage

Covered by shared tests:

- `GhostVariablePaceFixtureTest` verifies deterministic variable-pace GPS data.
- `SimulatedGpsProviderTest` verifies continuous provider wrapping with increasing timestamps.
- `TimingGhostIntegrationTest.variablePaceProviderFeedsNormalTimingFlowAndPersistsSimulatedReferenceOnlyOnSave` verifies:
  - provider starts before timing;
  - timing reads the normal provider stream through `SessionController.ingestSample`;
  - positive and negative live delta occur;
  - in-session new best updates immediately;
  - reference persists only after explicit Save;
  - simulated reference does not pollute the real reference slot.

Required final checks:

```powershell
.\gradlew.bat :shared:check
.\gradlew.bat :androidApp:assembleDebug
```

## Android ADB UAT

ADB can automate install/launch/navigation taps and collect screenshots/UI XML. Visual confirmation is still required for delta value changes because the simulated feed advances over time and Compose UI timing is best checked from screenshots or the physical device.

Recommended flow:

1. Install and launch the debug APK.
2. On Drive, tap `Start Demo Feed`.
3. Wait several seconds and verify the feed continues before any timing session starts.
4. If no saved demo track exists, use the normal Mark New Track flow, save the track, and set/confirm start-finish.
5. Return to Drive and tap `Start Timing`.
6. Observe timing surface:
   - first state may show delta `--`;
   - later slower/faster laps should show only signed seconds, e.g. `+0.421s` or `-0.218s`;
   - no words like ahead/behind/faster/slower should appear beside the moving delta.
7. Keep timing running long enough for a new best lap. The reference update is silent; the following lap should compare against the new best without starting a new session.
8. Tap `Stop`, then `Save Session`.
9. Open Review and verify saved simulated artifacts remain visibly Demo/Simulated.

## Manual Checks

- Confirm current lap remains the largest readout.
- Confirm delta is the second core readout and value-only.
- Confirm Last/Best/Laps/Speed/Accuracy continue to render when delta is `--`.
- Confirm `DEMO — simulated GPS` remains visible during simulated timing.
- Confirm safety posture remains closed-course/private-track and passive while moving.

## Safety Boundary

Do not perform Phase 4 UAT on public roads. The deterministic provider-layer simulation exists so positive/negative delta and same-session reference update can be validated without driving/riding real laps.
