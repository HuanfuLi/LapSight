---
phase: 02
name: Clean-Room Lap Engine V0
type: implementation
status: ready_for_execution_pending_user_approval
wave: 1
depends_on:
  - 01-mobile-walking-skeleton-gps-probe
requirements:
  - GPS-05
  - LAP-01
  - LAP-02
  - LAP-03
  - LAP-04
  - LAP-05
  - LAP-06
  - LAP-07
  - SAFE-04
  - ARCH-02
files_modified:
  - shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/**
  - shared/src/commonTest/kotlin/com/huanfuli/lapsight/shared/**
  - shared/src/androidHostTest/kotlin/com/huanfuli/lapsight/shared/**
  - README.md
  - .planning/**
autonomous: true
---

# Phase 2 Plan: Clean-Room Lap Engine V0

## Objective

Build a clean-room shared Kotlin lap engine that can detect start/finish crossings from GPS-like sample streams, estimate interpolated crossing times, reject obvious false positives, replay deterministic fixtures, and expose enough timing state for the existing mounted-phone dash to show current lap, last lap, best lap, lap count, and speed.

The phone app remains the source of truth. This phase does not build ghost delta, maps, session persistence, external GNSS, or the Meta glasses bridge.

## User Decisions Captured

- V0 start/finish setup uses two points as the first version.
- Phase 2 continues to use deterministic simulator/replay data.
- Real Android/iOS GPS provider work stays deferred.
- Sector lines are in scope beyond data modeling: Phase 2 must include sector-line data model, sector crossing detection, sector split timing state, and minimal UI display.

## Product Slice

After this phase, the user should be able to launch the phone app, start a demo timing session, see lap timing fields update from deterministic GPS-like samples, and trust that the same lap engine is covered by replay/unit tests independent of UI and platform APIs.

## Scope

### In Scope

- Shared domain model for lap timing:
  - start/finish line
  - sector-line model
  - lap event
  - sector/split event
  - lap timing state
  - sector timing state
  - lap detection config
  - rejection/quality reason flags
- Local meter projection near session origin.
- Segment crossing detection between consecutive samples.
- Crossing timestamp interpolation.
- Direction, minimum lap time, cooldown, speed, and accuracy filters.
- Deterministic synthetic replay fixtures.
- Shared tests for geometry, interpolation, filters, replay, sector detection, and state updates.
- Minimal Compose dash integration that displays lap timing and sector/split timing state.
- Explicit GPS limitation copy in the Phase 2 UI path.

### Out of Scope

- Real Android Fused Location Provider integration.
- Real iOS Core Location integration.
- Saved sessions, database/schema work, export, GPX, or review screens.
- Ghost lap, delta-to-best, map trace, or animated vehicle ghost.
- External GNSS, BLE, NMEA, telemetry, or glasses HUD.
- Copying implementation code from GPL-licensed lap timer projects.

## Planning Assumptions

- Phase 2 uses existing `LocationSample`-style data from shared code and may refine it only if required by the lap engine contract.
- Start/finish definition for V0 is locked to two GPS points.
- The observable app slice uses simulator-backed deterministic replay data. Real GPS providers remain deferred.
- Sector lines use the same two-point line concept as start/finish, with ordered sector IDs/names and per-lap split state.
- All algorithmic behavior must be deterministic under tests.
- Geometry is implemented from first principles in shared Kotlin; open-source projects remain research inputs only.

## Architecture Direction

### Proposed Shared Packages

```text
shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/
├─ location/
│  ├─ LocationSample.kt
│  └─ LocalProjection.kt
├─ lap/
│  ├─ LapEngine.kt
│  ├─ LapEngineConfig.kt
│  ├─ LapTimingState.kt
│  ├─ LapEvent.kt
│  ├─ SectorEvent.kt
│  ├─ SectorTimingState.kt
│  ├─ StartFinishLine.kt
│  ├─ SectorLine.kt
│  ├─ CrossingDetector.kt
│  └─ ReplayRunner.kt
└─ presentation/
   └─ LapDashState.kt
```

Exact file/package names may be adjusted to fit the current small codebase, but the engine must stay independent from Compose, Android, iOS, and storage.

### Core Data Flow

```text
LocationSample stream
  -> LocalProjection near session origin
  -> consecutive movement segment
  -> CrossingDetector
  -> filter gates
  -> interpolated crossing event
  -> LapEngine state
  -> LapDashState for UI
```

## Implementation Tasks

### Task 1: Establish lap domain models

**Action**

Add shared Kotlin models for:

- `GeoPoint`
- `LocalPoint`
- `StartFinishLine`
- `SectorLine`
- `SectorEvent`
- `SectorTimingState`
- `LapEngineConfig`
- `LapEvent`
- `LapRejectReason`
- `LapTimingState`
- `LapEngineInput` or equivalent sample wrapper

Reuse or migrate the existing `LocationSample` model instead of duplicating parallel sample types.

**Acceptance Criteria**

- Models compile in commonMain.
- Domain models have no Compose, Android, iOS, file system, or database dependency.
- Sector-line model supports ordered sectors with stable IDs/names and two-point line geometry.
- Sector timing state can represent pending, crossed, rejected, and reset-per-lap sector status.
- Config has explicit defaults for:
  - minimum lap duration
  - crossing cooldown
  - maximum accepted horizontal accuracy
  - minimum speed
  - direction tolerance

**Verify**

- `.\gradlew.bat :shared:check`
- Common tests assert default config values and basic model invariants.

### Task 2: Implement local projection and geometry primitives

**Action**

Implement a local meter projection around a session origin and basic geometry helpers:

- latitude/longitude to local x/y meters
- segment intersection
- signed side/cross-product helpers
- interpolation ratio along a movement segment
- optional distance helpers for lap distance estimate

Use a simple local tangent/equirectangular approximation suitable for track-scale distances, with tests documenting expected tolerances.

**Acceptance Criteria**

- Projection is deterministic and reversible enough for test sanity checks.
- Intersection handles:
  - clear crossing
  - no crossing
  - touching endpoint
  - nearly parallel segments
  - movement segment starting near the line
- Interpolation ratio is clamped or rejected explicitly when invalid.

**Verify**

- Unit tests for projection sanity at a fixed origin.
- Unit tests for positive/negative crossing cases.
- Unit tests for interpolation ratio and interpolated timestamp.

### Task 3: Implement clean-room line crossing detector

**Action**

Create a reusable `CrossingDetector` that receives two consecutive projected samples and a timing line, then returns a crossing candidate containing:

- crossing point
- interpolation ratio
- interpolated timestamp
- movement heading/direction metadata
- candidate quality flags

Use this detector for both the start/finish line and sector lines. Start/finish and sectors may have different acceptance rules at the engine level, but the geometry primitive should be shared.

**Acceptance Criteria**

- Detector does not mutate session state.
- Detector reports no crossing when samples do not cross the line.
- Detector reports exactly one candidate for one valid movement segment.
- Detector can identify which timing line was crossed.
- Detector behavior is testable without UI/platform code.

**Verify**

- Tests cover crossing from both directions.
- Tests cover low-frequency samples where the line lies between points.
- Tests cover noisy samples near but not crossing the line.

### Task 4: Implement lap engine state machine and filters

**Action**

Implement `LapEngine` as a deterministic state machine:

1. Before first valid crossing: acquiring/awaiting start.
2. First valid crossing: start lap 1.
3. Subsequent valid crossing: finish current lap, update last/best/lap count, start next lap.
4. Valid sector crossing inside the current lap: record sector split time, update current lap sector state, and prevent duplicate sector splits for the same sector in the same lap.
5. Rejected crossing: preserve state and expose reject reason for diagnostics.

Apply filters before accepting a lap:

- max horizontal accuracy
- minimum speed
- direction gate
- cooldown since last accepted crossing
- minimum lap duration after first lap starts

**Acceptance Criteria**

- Engine has no platform or UI dependency.
- Every rejection reason is observable in state or event output.
- Minimum lap time and cooldown cannot produce duplicate laps from jitter around the line.
- Direction filtering can be disabled or configured for tests.
- Sector splits are recorded only after a lap has started.
- Sector splits reset for each new lap.
- Repeated crossing of the same sector within one lap is rejected or ignored with a diagnostic reason.

**Verify**

- Unit tests for:
  - first crossing starts timing
  - second crossing completes lap
  - best lap updates only when faster
  - cooldown blocks duplicate crossing
  - minimum lap duration blocks false laps
  - accuracy threshold blocks poor samples
  - speed threshold blocks stationary samples
  - direction gate blocks wrong-way crossings
  - sector crossing records a split inside a lap
  - sector crossing before lap start is ignored
  - duplicate sector crossing in one lap is rejected/ignored
  - sector state resets after start/finish completes a lap

### Task 5: Add replay runner and synthetic fixtures

**Action**

Add a replay runner that can feed synthetic or recorded samples through the lap engine deterministically.

Add synthetic fixture builders for:

- simple line crossing
- one-lap loop
- multi-lap loop with variable durations
- jitter near start/finish line
- low-frequency GPS samples
- wrong-direction crossing
- poor-accuracy samples
- one-lap loop with one sector
- multi-lap loop with multiple sectors

Keep fixtures as Kotlin builders or lightweight test resources. Do not introduce persistence/schema decisions in Phase 2.

**Acceptance Criteria**

- Replay tests can run as part of `:shared:check`.
- Fixture timestamps are deterministic.
- Multi-lap fixture produces expected current/last/best/lap count.
- Sector fixtures produce expected per-lap split timings.
- Jitter fixture does not create duplicate laps.

**Verify**

- `.\gradlew.bat :shared:check`
- Replay tests cover GPS-05 and LAP-06.

### Task 6: Integrate lap timing into mounted-phone dash

**Action**

Extend the existing simulator-backed app path so the mounted-phone dash can display lap timing state:

- current lap time
- last lap time
- best lap time
- lap count
- current sector/split summary
- latest sector split
- speed
- fix/source state

Provide a minimal way to run a demo lap session from deterministic replay/simulator data with a preset two-point start/finish line and preset sector lines. Keep controls passive and large:

- Start Timing / Stop Timing
- Reset
- Demo Course label
- compact sector/split readout

Do not add a map, dense settings screen, or ghost visualization.

**Acceptance Criteria**

- Dash remains readable in portrait and landscape.
- Safety/accuracy copy remains visible.
- Lap timing fields update without needing real GPS provider work.
- Sector/split fields update from deterministic demo replay.
- UI consumes presentation state derived from the lap engine rather than reimplementing lap logic.

**Verify**

- Android debug APK builds.
- Manual Android smoke test:
  - launch app
  - start timing demo
  - observe current lap advancing
  - observe last/best/lap count after replay crossing
  - stop/reset
  - verify portrait/landscape readability

### Task 7: Update docs and verification artifacts

**Action**

Update phase documentation with:

- implemented design
- test coverage
- known limitations
- remaining iOS verification status
- clear boundary that real GPS providers/session storage/ghost delta remain future phases

**Acceptance Criteria**

- `02-VERIFICATION.md` records automated checks and Android smoke checks if run.
- `02-SUMMARY.md` records changed files, test commands, deviations, and remaining gaps.
- README or planning docs explain that lap timing is still simulator/replay-backed until real providers are wired.

**Verify**

- Documentation matches code behavior.
- No claim implies pro-grade timing precision.

## Verification Plan

### Automated

Run after implementation:

```powershell
.\gradlew.bat :shared:check --stacktrace
.\gradlew.bat :androidApp:assembleDebug --stacktrace
```

Expected:

- Shared common tests pass.
- Android host tests pass.
- iOS simulator tests may remain skipped on Windows.
- Debug APK builds.

### Manual Android UAT

On attached Android device:

1. Install debug APK.
2. Launch app.
3. Verify portrait dash readability.
4. Start demo timing session.
5. Confirm current lap timer advances.
6. Confirm at least one completed lap updates last/best/lap count.
7. Confirm at least one sector split updates during the lap.
8. Stop and reset.
9. Manually rotate to landscape and repeat readability/control smoke test.
10. Check logcat for app crash markers.

### Future iOS UAT

On macOS/Xcode:

1. Build and launch iOS app.
2. Confirm shared dash renders.
3. Confirm demo lap timing path works if Compose iOS runtime remains healthy.

## Requirement Coverage

| Requirement | Planned Coverage |
|---|---|
| GPS-05 | Replay runner and deterministic synthetic replay fixtures. |
| LAP-01 | Start/finish line model using two GPS points; optional helper for current position plus heading if cheap. |
| LAP-02 | Crossing detector checks consecutive sample movement segment against start/finish line. |
| LAP-03 | Crossing timestamp interpolated from sample timestamps and crossing ratio. |
| LAP-04 | Direction, minimum lap duration, cooldown, speed, and accuracy filters. |
| LAP-05 | Dash shows current lap, last lap, best lap, lap count, sector/split summary, and speed. |
| LAP-06 | Engine runs from synthetic replay fixtures without UI/platform services. |
| LAP-07 | Sector-line data model, detection, per-lap split state, replay tests, and minimal UI included. |
| SAFE-04 | UI/docs explicitly state GPS accuracy limits and simulator/replay status. |
| ARCH-02 | Tests cover geometry, crossing, interpolation, filters, sector detection, and replay scenarios. |

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Phone GPS noise causes duplicate crossings near the line. | Test jitter fixture; enforce cooldown, min lap duration, direction gate, and accuracy filters. |
| Low-frequency samples skip over start/finish at speed. | Detect line crossing between consecutive samples, not point proximity only; interpolate crossing timestamp. |
| Coordinate projection precision degrades on larger areas. | Use local origin per session; document track-scale approximation; test sanity at known coordinates. |
| UI starts owning lap logic. | Keep `LapEngine` in common domain; UI only renders `LapTimingState`/presentation state. |
| GPL contamination from reference projects. | Implement from first principles; do not copy source, tests, naming, or structure from GPL projects. |
| Phase grows into session persistence or ghost delta. | Keep persistence and ghost out of Phase 2; defer to Phases 3 and 4. |
| Sector UI makes the mounted dash dense. | Show only compact current/latest sector split state; defer detailed sector table/review to later session review work. |

## Definition of Done

- Shared lap engine models, projection, crossing detector, and state machine are implemented.
- Automated tests cover geometry, interpolation, filters, sector detection, and replay fixtures.
- Existing dash shows lap timing and sector/split state from the engine in a demo/replay-backed session.
- Android debug APK builds and Android smoke test is documented.
- No GPL code is copied.
- Phase 2 summary and verification docs are written.

## Resolved Review Questions

1. V0 uses a two-point start/finish line.
2. Phase 2 uses deterministic simulator/replay data and does not pull real GPS provider work forward.
3. Sector-line support includes data model, detection, per-lap split timing state, tests, and minimal UI.

---

*Plan drafted: 2026-06-25*
