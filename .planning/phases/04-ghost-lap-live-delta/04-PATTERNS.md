# Phase 4: Ghost Lap + Live Delta — Pattern Map

**Mapped:** 2026-06-25
**Files analyzed:** current Phase 2/3 lap, session, storage, fixture, and Drive UI code

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Existing Analog | Match Quality |
|---|---|---|---|---|
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/GhostModels.kt` | model | transform | `lap/LapModels.kt`, `session/SessionModels.kt` | role-match |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ProgressCurveBuilder.kt` | algorithm | batch transform | `review/TraceProjection.kt`, `lap/LocalProjection.kt`, `track/ReferenceLineExtractor.kt` | strong |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/LiveDeltaEngine.kt` | algorithm | streaming transform | `lap/LapEngine.kt`, `lap/LapDashState.kt` | strong |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ghost/ReferenceLapSelector.kt` | service | selection/update | `storage/*ghostCandidateForTrack*`, `review/ReviewModels.kt` | partial |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionModels.kt` | DTO/model | CRUD | existing `TimingSessionPayloadV1`, `GhostCandidate` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/TimingSessionRecorder.kt` | service | streaming | existing recorder over `LapEngine` | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/session/SessionController.kt` | service | event-driven | existing controller snapshot/recovery | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/LocalSessionStore.kt` | interface | CRUD | existing track/session methods | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/FileSessionStore.kt` | file storage | file-I/O | existing canonical JSON payload storage | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/storage/InMemorySessionStore.kt` | test/preview storage | in-memory CRUD | existing maps for sessions/tracks | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/fixtures/GpsFixtureLibrary.kt` | fixture | generated samples | existing clean/outlier/multi-session builders | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/SimulatedGpsProvider.kt` | provider | streaming | existing continuous wrapping feed | exact |
| `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/ui/DriveScreen.kt` | UI | render state | existing `TimingRunSurface`, `MetricCard`, `DemoBadge` | exact |
| `shared/src/commonTest/kotlin/.../ghost/*Test.kt` | tests | deterministic algorithms | `lap/*Test.kt`, `review/TraceProjectionTest.kt` | exact |
| `shared/src/commonTest/kotlin/.../session/TimingGhostIntegrationTest.kt` | tests | recorder/controller integration | `SessionControllerTest.kt`, `DraftRecoveryTest.kt` | strong |

---

## Existing Patterns to Preserve

### Pure shared algorithms

Existing lap and track algorithms live in `commonMain`, have no Compose/platform dependency, and are validated with `commonTest`. Phase 4 ghost math should follow the same boundary.

Apply to:

- progress curve construction
- reference interpolation
- delta sign/suppression rules
- variable-pace fixture replay

### DTO/domain split

`LocationSample` / `LocationSampleDto`, `LapEvent` / `LapDto`, and Track DTOs keep serialization out of the clean lap engine. Phase 4 should keep `ReferenceLap` as domain and `GhostReferencePayloadV1` / `ProgressPointDto` as storage DTOs.

### Storage layout

`FileSessionStore` writes canonical payload JSON before index/state updates and uses typed `LoadResult`. Add ghost reference payloads with the same pattern:

- write full payload atomically
- validate schema on load
- return `LoadResult.NotFound` / `LoadResult.Corrupt`
- do not crash UI on corrupt ghost file

### Simulated source isolation

Phase 3 already labels simulated source and excludes simulated sessions from real `GhostCandidate` derivation. Phase 4 must extend this from summary candidates to full reference payloads.

### UI rendering owns no timing logic

`DriveScreen` should render a snapshot from `SessionController` / recorder state. It should not compute progress distance, choose reference laps, or inspect saved payloads directly.

---

## Implementation Seams

| Seam | Current code | Phase 4 insertion |
|------|--------------|-------------------|
| Timing start | `SessionController.startTiming(trackId)` builds `TimingSessionRecorder` | Load persisted reference payload for track/source and pass it to recorder |
| Sample processing | `TimingSessionRecorder.onSample(sample)` feeds `LapEngine`, stores samples/laps | Also feed `LiveDeltaEngine`, expose latest `LiveDeltaSnapshot`, build reference on newly completed laps |
| Draft save | `SessionController.saveStoppedDraft()` saves `TimingSessionPayloadV1` | On Save, persist the best reference built from the stopped draft if eligible |
| Draft discard | `SessionController.discardDraft()` clears draft | Do not persist a session-local new best on discard |
| Review summary | `ReviewModels` uses `ghostCandidateForTrack` | May continue summary logic; not required for live delta UI |
| Drive timing UI | `TimingRunSurface` reads basic draft counts/latest sample | Read a richer timing snapshot including current/last/best/delta |

---

## Test Patterns

Use existing test style:

- `kotlin.test.Test`, `assertEquals`, `assertTrue`, `assertNull`, `assertNotNull`
- deterministic fixture builders from `GpsFixtureLibrary`
- `LapEngineConfig.lenientForTests` for replay-heavy integration tests
- `InMemorySessionStore` for controller tests
- Okio fake/in-memory patterns for file store tests where existing tests use them

---

## Known Pitfalls

| Pitfall | Avoidance |
|---------|-----------|
| Applying a new best before the next lap | Update reference after the completed lap event, then reset current delta for the next lap |
| Persisting discarded-session reference | Keep session-local references in memory/draft-derived state; write global reference only on Save |
| Using simulated reference for real Track | Storage/load APIs include `isSimulated`; real reads reject simulated payloads |
| Leaving stale delta visible | `LiveDeltaSnapshot.Unavailable` must replace previous value whenever suppression applies |
| Overloading Drive UI | Value-only delta; no chart/map/telemetry screen in Phase 4 |
