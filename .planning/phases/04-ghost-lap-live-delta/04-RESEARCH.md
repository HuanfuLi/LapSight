# Phase 4: Ghost Lap + Live Delta — Research

**Researched:** 2026-06-25
**Domain:** shared Kotlin ghost reference model, progress-distance delta engine, mounted-phone timing UI
**Confidence:** HIGH for integration points and deterministic validation; MEDIUM for exact confidence thresholds until fixture tests tune them

---

## User Constraints from Phase Context

Source: `.planning/phases/04-ghost-lap-live-delta/04-CONTEXT.md`.

- **D-01:** Default reference is the fastest valid lap for the current saved Track.
- **D-02:** A new fastest valid lap during an active session immediately becomes the reference for following laps.
- **D-03:** References are Track-scoped.
- **D-04:** Demo/Simulated laps must not become reference laps for real Track data.
- **D-05:** Store raw best-lap samples and a precomputed progress curve.
- **D-06:** Progress curve includes progress distance/normalized progress, elapsed time, lat/lon/local point, speed, heading, accuracy, and optional sector metadata.
- **D-07:** The model should support future telemetry charts, but charts are out of scope now.
- **D-08:** Current-lap progress is built realtime from incoming samples.
- **D-09:** Delta = current lap elapsed time minus reference elapsed time at equivalent progress.
- **D-10:** Positive delta means slower and shows `+`; negative means faster and shows `-`.
- **D-11:** Suppress delta when there is no current lap, no reference, poor GPS quality, or no confident progress match.
- **D-12:** A completed new fastest lap rebuilds the reference immediately for the next lap.
- **D-13:** Delta is the second core readout after Current Lap.
- **D-14:** Moving dashboard shows only sign plus seconds, e.g. `-0.218s`, `+0.421s`.
- **D-15:** Negative/faster is green; positive/slower is orange/red; unavailable is neutral.
- **D-16:** No map ghost, telemetry chart, or dense UI in Phase 4.
- **D-17:** No-reference display is `--`.
- **D-18:** Do not keep stale delta.
- **D-19:** Other timing metrics continue when delta is unavailable.
- **D-20:** Reuse the Phase 3 provider-layer simulator.
- **D-21:** Simulated GPS is an independent continuous source.
- **D-22:** Normal TimingSession flow consumes provider samples after session start.
- **D-23:** Deterministic continuous variable-pace simulation must cover positive delta, negative delta, and in-session new best update.
- **D-24:** Simulated artifacts stay visibly Demo/Simulated and isolated from real ghost state.

---

## Existing Code Facts

| Area | Current state | Phase 4 implication |
|------|---------------|---------------------|
| Samples | `LocationSample` has elapsedMillis, lat/lon, accuracy, speed, heading, altitude, source | Ghost curve can be built in shared Kotlin without platform APIs |
| Lap engine | `LapEngine` emits `LapTimingState`, `LapEvent`, sector events | Do not create a second lap detector; consume completed lap intervals from existing engine |
| Timing recorder | `TimingSessionRecorder` stores private sample list and completed laps, checkpoints drafts | Add ghost/delta state here or via a pure collaborator injected into it |
| Storage | `LocalSessionStore` persists tracks, sessions, drafts; `ghostCandidateForTrack()` returns fastest summary only | Need full reference-lap payload, not just `GhostCandidate` summary |
| UI | `DriveScreen` has full timing surface but currently shows lap count/speed/accuracy/sample count | Add current lap and delta readouts from recorder/controller state |
| Simulator | `SimulatedGpsProvider` continuously wraps deterministic fixture samples | Add a variable-pace fixture; do not add ghost-specific controls |
| Review/export | Timing payloads already store raw samples and laps | Existing saved sessions are enough to derive historical best lap samples |

---

## Recommended Architecture

Add a shared `ghost` domain package:

```text
shared/commonMain/.../ghost/
  GhostModels.kt
  ProgressCurveBuilder.kt
  LiveDeltaEngine.kt
  ReferenceLapSelector.kt
```

Responsibilities:

- `ProgressCurveBuilder`: converts a completed lap's samples into a monotonic progress curve.
- `LiveDeltaEngine`: consumes current-lap samples and a `ReferenceLap`, interpolates equivalent reference elapsed time, and emits a `LiveDeltaSnapshot`.
- `ReferenceLapSelector`: picks the fastest valid persisted candidate for a Track and updates the active in-memory reference when a faster lap completes.
- Storage DTOs remain serializable in the session/storage boundary; pure ghost algorithms do not depend on serialization, Compose, or platform APIs.

This keeps the lap engine independent and makes all algorithmic behavior testable with deterministic fixtures.

---

## Reference Lap Data Model

Recommended domain model:

```kotlin
data class ReferenceLap(
    val trackId: String,
    val sessionId: String,
    val lapNumber: Int,
    val durationMillis: Long,
    val source: SourceMetadata,
    val rawSamples: List<LocationSample>,
    val progressCurve: ProgressCurve,
)

data class ProgressCurve(
    val totalDistanceMeters: Double,
    val points: List<ProgressPoint>,
)

data class ProgressPoint(
    val elapsedMillis: Long,
    val progressMeters: Double,
    val normalizedProgress: Double,
    val latitude: Double,
    val longitude: Double,
    val localX: Double,
    val localY: Double,
    val speedMetersPerSecond: Double?,
    val headingDegrees: Double?,
    val horizontalAccuracyMeters: Double?,
)
```

Recommended persisted payload:

```kotlin
@Serializable
data class GhostReferencePayloadV1(
    val schemaVersion: Int,
    val trackId: String,
    val sessionId: String,
    val lapNumber: Int,
    val lapDurationMillis: Long,
    val source: SourceMetadata,
    val rawSamples: List<LocationSampleDto>,
    val progressPoints: List<ProgressPointDto>,
    val totalDistanceMeters: Double,
    val app: AppMetadata,
)
```

Persist references keyed by `trackId + source.isSimulated`. Real Track reads must not load simulated references; simulated UAT may load simulated references for the same simulated Track.

---

## Progress-Distance Delta Algorithm

Build reference curve:

1. Select samples whose timestamps fall inside a completed `LapEvent` interval.
2. Insert or approximate start/end boundary points using neighboring samples when necessary.
3. Project lat/lon into local meters using existing local projection helpers.
4. Accumulate segment distance to create monotonic `progressMeters`.
5. Normalize progress by total distance.
6. Drop or mark unusable points when coordinates are non-finite, timestamps are non-monotonic, or accuracy exceeds configured threshold.

Realtime current progress:

1. Reset current progress when lap engine opens a new lap.
2. Append samples while `LapTimingState.phase == Timing`.
3. Accumulate current-lap distance from the current lap's sample path.
4. Clamp progress to the reference curve's distance range.
5. Interpolate reference elapsed time at current progress with binary search over reference progress points.
6. Emit `deltaMillis = currentLapElapsedMillis - referenceElapsedMillis`.

Suppression rules:

- No active current lap -> unavailable.
- No loaded reference -> unavailable.
- Fewer than two current-lap samples -> unavailable.
- Reference curve has fewer than two points -> unavailable.
- Current or reference GPS quality is poor -> unavailable.
- Progress cannot be matched monotonically -> unavailable.

The UI must render unavailable as `--`, not a stale previous delta.

---

## Same-Session Best Update

The controller/recorder should maintain two reference layers:

1. **Persisted reference:** loaded at timing start from storage for the current Track/source boundary.
2. **Session-local active reference:** updated immediately when a completed lap is faster than the active reference.

When a new fastest lap completes:

- Build a `ReferenceLap` from that completed lap's samples.
- If faster than the active reference, replace the in-memory reference immediately for the following lap.
- Do not retroactively change the delta for the lap that just completed.
- Persist the new reference only when the stopped draft is saved as a formal session. If the user discards the session, the in-session reference must not become long-term history.
- On draft resume, rebuild the session-local best from the saved draft samples/laps before continuing.

This satisfies D-02/D-12 without violating Phase 3's explicit Save/Discard semantics.

---

## Storage Strategy

Extend `LocalSessionStore` with full reference payload methods:

- `loadReferenceLap(trackId: String, isSimulated: Boolean): LoadResult<GhostReferencePayloadV1>`
- `saveReferenceLap(payload: GhostReferencePayloadV1, app: AppMetadata): SaveResult`

File layout suggestion:

```text
<root>/ghosts/real/<trackId>.json
<root>/ghosts/simulated/<trackId>.json
```

`ghostCandidateForTrack()` may remain as a summary helper, but Phase 4 live delta should load full reference payloads. It should also be possible to rebuild the payload by scanning saved sessions if the reference file is missing and a valid session payload exists.

---

## UI Strategy

Update `SessionControllerSnapshot` or add `TimingRunSnapshot` so UI does not inspect recorder internals directly. Suggested fields:

- `currentLapMillis`
- `lastLapMillis`
- `bestLapMillis`
- `lapCount`
- `speedMetersPerSecond`
- `accuracyMeters`
- `delta: LiveDeltaSnapshot`
- `source: SourceMetadata`

`DriveScreen` renders:

- Current lap as primary display.
- Delta as second display, value-only.
- Last/best/laps/speed/accuracy as compact metrics.
- `DEMO — simulated GPS` unchanged.

---

## Deterministic UAT Simulation

Add `GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT`:

- Continuous loop geometry compatible with saved start/finish detection.
- Loop 1 establishes a reference.
- Later loops vary pace so current lap first shows positive/slower delta, then negative/faster delta.
- At least one completed lap beats the prior reference; the following lap must use the new reference immediately.
- `SimulatedGpsProvider` should continue wrapping samples with increasing timestamps.

No new product flow is needed: developer starts the existing demo feed, marks/saves a Track if needed, presses Start Timing, and observes live delta.

---

## Validation Architecture

Automated coverage should be pure shared Kotlin first:

- Progress-curve construction from completed lap samples.
- Reference elapsed interpolation by progress distance.
- Live delta sign/format/unavailable-state semantics.
- Storage round trip for full reference payload and real/simulated isolation.
- Recorder integration: load persisted reference, update same-session active reference, save/discard persistence behavior.
- Variable-pace fixture replay covers positive delta, negative delta, and immediate new-best reference update.
- UI state formatting tests cover `-0.218s`, `+0.421s`, and `--`.

Device UAT should verify the existing Android app flow by ADB where possible: demo feed runs continuously, normal Start Timing consumes samples, delta appears without a special ghost button, and saved sessions keep Demo labeling.

---

## Risks and Mitigations

| Risk | Mitigation |
|------|------------|
| Delta implies false precision on phone GPS | Suppress under poor confidence; keep `--` instead of stale values; retain safety/accuracy copy |
| Discarded session becomes persisted ghost | Keep new best in memory during active session; commit global reference only on explicit Save |
| Simulated data pollutes real reference | Key storage by source boundary and keep real reads excluding simulated references |
| Current-lap distance diverges from reference path | Phase 4 uses progress-distance MVP; future coordinate-nearest/map matching can reuse stored lat/lon and local points |
| UI becomes too dense | Delta is value-only and secondary to current lap; charts/maps deferred |

---

## Deferred Roadmap Reminder

The progress curve created in Phase 4 is intentionally telemetry-ready. After MVP, revisit:

- lap-over-lap progress curves
- speed trace, braking/acceleration zones
- sector delta graph
- map ghost animation
- lap comparison charts

This remains backlog/roadmap material, not Phase 4 implementation.
