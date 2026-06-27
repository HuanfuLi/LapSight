# Phase 05: Track Setup and Course Profiles - Pattern Map

**Mapped:** 2026-06-26
**Files/responsibilities classified:** 49
**Analogs found:** 45 / 49

## File Classification

Match quality meanings: **exact** = same role and flow; **role-match** = same role with adjacent semantics; **partial** = reusable primitive only; **none** = no close implementation exists.

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `track/TrackProfileModels.kt` (new) | model | CRUD / transform | `track/TrackModels.kt` | role-match |
| `track/TrackModels.kt` | model / legacy DTO | transform | its frozen V1 payload shapes | exact |
| `track/ClosedReferencePath.kt` (new) | utility | transform | `track/ReferenceLineExtractor.kt` | role-match |
| `track/CourseGeometryBuilder.kt` (new) | service | transform | `lap/TimingLines.kt`, `session/TimingSessionRecorder.kt` | role-match |
| `track/CourseProfileEditor.kt` (new) | store / model | event-driven / transform | `track/TrackReviewState.kt` | role-match |
| `track/TrackProfileController.kt` (new) | controller | CRUD / request-response | `ui/DriveMarkingController.kt` | role-match |
| `storage/SchemaMigrations.kt` (new) | utility | batch / file-I/O | `storage/FileSessionStore.kt` typed load path | partial |
| `storage/TrackProfileRepository.kt` (new) | service | CRUD / file-I/O | `storage/LocalSessionStore.kt` | exact |
| `storage/LocalSessionStore.kt` | service | CRUD / file-I/O | existing interface | exact |
| `storage/FileSessionStore.kt` | service | CRUD / file-I/O | existing atomic payload/index implementation | exact |
| `storage/InMemorySessionStore.kt` | service | CRUD | existing mirror implementation | exact |
| `storage/SchemaVersions.kt` | config | transform | existing schema constants | exact |
| `lap/TimingLines.kt` | model | transform | existing `CourseDefinition` | exact |
| `lap/LapModels.kt` | model | event-driven | existing lap/Sector state | exact |
| `lap/LapEngine.kt` | service | streaming / event-driven | existing clean-room engine | exact |
| `lap/CrossingDetector.kt` | utility | streaming / transform | existing finite-line detector | exact |
| `ghost/GhostCompatibility.kt` (new) | model / utility | transform | real/simulated reference slot checks in stores | role-match |
| `ghost/CourseProgressMatcher.kt` (new) | service | streaming / transform | `ghost/LiveDeltaEngine.kt` | partial |
| `ghost/GhostModels.kt` | model | streaming | existing typed available/unavailable states | exact |
| `ghost/LiveDeltaEngine.kt` | service | streaming / transform | existing live delta engine | exact |
| `ghost/ReferenceLapSelector.kt` | service | batch / transform | existing fastest-reference selector | exact |
| `session/SessionModels.kt` | model / DTO | CRUD / streaming | existing session and Ghost payloads | exact |
| `session/SessionController.kt` | controller | request-response / CRUD | existing timing lifecycle controller | exact |
| `session/TimingSessionRecorder.kt` | service | streaming / file-I/O | existing recorder/checkpoint pipeline | exact |
| `review/TraceProjection.kt` | utility | transform | existing render-only projection | exact |
| `review/ReviewModels.kt` | model / utility | transform | existing summary/layer builders | exact |
| `ui/DriveMarkingController.kt` | controller | request-response | existing snapshot controller | exact |
| `ui/DriveScreen.kt` | component | event-driven / request-response | existing Drive state/action wiring | exact |
| `ui/ReviewScreen.kt` | component | CRUD / request-response | existing list/detail flow | exact |
| `ui/TrackEditorScreen.kt` (new) | component | event-driven / transform | `ui/TraceView.kt`, `ui/DriveScreen.kt` | partial |
| `ui/TraceView.kt` | component | transform | existing Canvas renderer | exact |
| `App.kt` / `ui/AppShell.kt` | provider / component | request-response | existing dependency handoff | exact |
| `iosMain/.../MainViewController.kt` | provider | request-response | Android `MainActivity.kt` store injection | role-match |
| `export/JsonExportService.kt` | service | file-I/O / transform | existing canonical payload export | exact |
| `storage/SchemaMigrationTest.kt` (new) | test | file-I/O / batch | `storage/FileSessionStoreTest.kt` | role-match |
| `storage/CurrentTrackSelectionTest.kt` (new) | test | CRUD / request-response | `ui/DriveMarkingControllerTest.kt` | role-match |
| `storage/TrackRevisionStoreTest.kt` (new) | test | CRUD / file-I/O | `storage/FileSessionStoreTest.kt` | role-match |
| `track/ClosedReferencePathTest.kt` (new) | test | transform | `track/ReferenceLineExtractorTest.kt` | role-match |
| `track/CourseProfileEditorTest.kt` (new) | test | event-driven / transform | `track/ReferenceLineExtractorTest.kt` | role-match |
| `lap/CompleteSectorReplayTest.kt` (new) | test | streaming / event-driven | `lap/LapEngineTest.kt` | exact |
| `lap/CourseDirectionReplayTest.kt` (new) | test | streaming / event-driven | `lap/LapEngineTest.kt` | exact |
| `ghost/GhostCompatibilityTest.kt` (new) | test | CRUD / transform | `session/TimingGhostIntegrationTest.kt` | role-match |
| `ghost/CourseProgressMatcherTest.kt` (new) | test | streaming / transform | `session/TimingGhostIntegrationTest.kt` | role-match |
| `session/WrongCoursePreflightTest.kt` (new) | test | request-response / transform | no close controller decision test | none |
| `session/CourseProfileIntegrationTest.kt` (new) | test | request-response / streaming | `session/TimingGhostIntegrationTest.kt` | exact |
| `export/JsonExportTest.kt` | test | file-I/O / transform | existing test | exact |
| `review/ReviewSummaryTest.kt` | test | CRUD / transform | existing test | exact |
| `ui/DriveMarkingControllerTest.kt` | test | request-response | existing test | exact |
| `session/SessionControllerTest.kt` | test | request-response / streaming | existing test | exact |

## Pattern Assignments

### Profile models, revisions, compatibility, and session snapshots

**Apply to:**

- `track/TrackProfileModels.kt`
- `track/TrackModels.kt`
- `ghost/GhostCompatibility.kt`
- `session/SessionModels.kt`
- `storage/SchemaVersions.kt`

**Primary analog:** `shared/src/commonMain/kotlin/com/huanfuli/lapsight/shared/track/TrackModels.kt`

**Serializable model style** (lines 20-47, 69-87):

```kotlin
@Serializable
data class StartFinishLineDto(
    val pointA: GeoPointDto,
    val pointB: GeoPointDto,
)

@Serializable
data class TrackReferenceLine(
    val points: List<GeoPointDto> = emptyList(),
    val isClosed: Boolean = true,
)

@Serializable
data class TrackPayloadV1(
    val schemaVersion: Int = CURRENT_TRACK_SCHEMA_VERSION,
    val track: Track,
    val app: AppMetadata,
)
```

Copy the package/import/`@Serializable data class` conventions, but do **not** keep the V1 default tied to a mutable `CURRENT_*` constant. Freeze each existing V1 DTO with literal version `1`; create separate V2 DTOs with literal version `2`.

**Immutable session snapshot seam:** `session/SessionModels.kt` lines 168-195.

```kotlin
@Serializable
data class TimingSession(
    val id: String,
    val trackId: String,
    val trackName: String,
    val createdAtEpochMillis: Long,
    val source: SourceMetadata,
    val startFinish: StartFinishLineDto,
    val sectors: List<SectorLineDto> = emptyList(),
)
```

Replace the loose line snapshot in V2 with one `CourseSnapshot` containing profile ID, exact revision ID, geometry compatibility ID, selected direction, reference line, oriented boundaries, complete Sector definitions, and preflight outcome. Historical Review/export must use this snapshot, not the latest profile.

**Compatibility shape to introduce:**

```kotlin
data class CourseCompatibilityKey(
    val profileId: String,
    val geometryCompatibilityId: String,
    val direction: CourseDirection,
    val isSimulated: Boolean,
)
```

Integration constraints:

- Stable profile identity and immutable revision identity are different values.
- Sector-only revision: carry `geometryCompatibilityId` forward.
- Reference-line or start/finish revision: generate a new compatibility ID.
- Duplicate: generate a new profile ID and compatibility ID even if geometry is identical.
- Keep `revisionId` for exact history navigation, but never use it alone as the Ghost key.
- Keep the source flag in compatibility; derive timing source from the active provider/run, not Track-marking provenance.
- Never hash serialized floating-point geometry to decide compatibility.

---

### Repository contract, explicit selection, and schema dispatch

**Apply to:**

- `storage/TrackProfileRepository.kt`
- `storage/LocalSessionStore.kt`
- `storage/FileSessionStore.kt`
- `storage/InMemorySessionStore.kt`
- `storage/SchemaMigrations.kt`
- `track/TrackProfileController.kt`
- `ui/DriveMarkingController.kt`

**Repository contract analog:** `storage/LocalSessionStore.kt` lines 14-37 and 109-125.

```kotlin
interface LocalSessionStore {
    fun saveTrackBundle(track: Track, marking: TrackMarkingSession, app: AppMetadata): SaveResult
    fun loadTrack(trackId: String): LoadResult<TrackPayloadV1>
    fun readIndex(): ReviewIndex
}

sealed interface LoadResult<out T> {
    data class Loaded<T>(val value: T) : LoadResult<T>
    data object NotFound : LoadResult<Nothing>
    data class Corrupt(val reason: String) : LoadResult<Nothing>
}
```

Preserve typed `Loaded` / `NotFound` / `Corrupt` results. Add typed operations for:

```kotlin
saveProfile
loadProfile
listActiveProfiles
appendRevision
renameProfile
archiveProfile
duplicateProfile
loadCurrentSelection
setCurrentSelection
clearCurrentSelection
```

Use domain result types for unavailable/archived/corrupt current selections; do not return a replacement profile.

**Atomic file and index ordering:** `storage/FileSessionStore.kt` lines 59-99 and 293-298.

```kotlin
writeAtomically(markingPath, json.encodeToString(markingPayload))
writeAtomically(trackPath, json.encodeToString(trackPayload))

val updated = upsertRows(readIndex(), rows)
writeAtomically(indexPath, json.encodeToString(updated))

private fun writeAtomically(path: Path, content: String) {
    path.parent?.let { fileSystem.createDirectories(it) }
    val tmp = path.parent!! / "${path.name}$TMP_SUFFIX"
    fileSystem.write(tmp) { writeUtf8(content) }
    fileSystem.atomicMove(tmp, path)
}
```

Copy payload-first/index-last and sibling-temp/atomic-move behavior. Profile aggregate writes and current-selection writes require the same pattern. Archive must preserve profile, revision, session, and Ghost payloads.

**Typed validation/error handling:** `storage/FileSessionStore.kt` lines 257-290.

```kotlin
private inline fun <reified T> load(path: Path, validate: (T) -> String?): LoadResult<T> {
    if (!fileSystem.exists(path)) return LoadResult.NotFound
    return try {
        val text = fileSystem.read(path) { readUtf8() }
        val value = json.decodeFromString<T>(text)
        val problem = validate(value)
        if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(value)
    } catch (e: SerializationException) {
        LoadResult.Corrupt(e.message ?: "malformed JSON")
    } catch (e: IllegalArgumentException) {
        LoadResult.Corrupt(e.message ?: "invalid payload")
    }
}
```

For V2, parse a `JsonElement`, inspect `schemaVersion`, dispatch to a frozen serializer, validate, then map to current domain. Unknown future versions return `Corrupt("unsupported schemaVersion ...")`; never coerce them.

**In-memory mirror requirement:** `storage/InMemorySessionStore.kt` lines 20-30 and 72-87.

```kotlin
/**
 * It mirrors [FileSessionStore]'s contract — payload-before-index upsert order,
 * typed load results, schemaVersion validation — without touching the file system.
 */
class InMemorySessionStore : LocalSessionStore
```

Every profile/revision/selection/reference operation added to the file store must have the same externally observable behavior in the in-memory store.

**Current anti-pattern to replace:** `ui/DriveMarkingController.kt` lines 115-133.

```kotlin
val timingReadyTrack = savedTracks
    .filter { it.startFinish != null }
    .maxByOrNull { it.createdAtEpochMillis }
```

Do not copy this heuristic. Resolve only persisted `CurrentTrackSelection`; if absent, archived, missing, corrupt, or not timing-ready, return a typed unavailable state and direct the user to the selector.

Migration constraints:

- Keep literal V1 Track, index, TimingSession, draft, and Ghost fixtures.
- Decode and validate all source payloads before committing migrated state.
- Write V2 side-by-side and retain V1 originals.
- Make migration idempotent and rebuild indexes from canonical payloads.
- Migrate current selection to `null`; never infer newest/only profile.
- Validate opaque IDs before using them in paths: reject separators, `..`, and control characters.
- Preserve V1 `SectorEventDto` as legacy cumulative-line-split data; do not relabel it as a complete Sector result.

---

### Closed reference path and pure editor state

**Apply to:**

- `track/ClosedReferencePath.kt`
- `track/CourseProfileEditor.kt`
- `review/TraceProjection.kt`
- `ui/TrackEditorScreen.kt`
- `ui/TraceView.kt`

**Closed arc-length analog:** `track/ReferenceLineExtractor.kt` lines 295-350.

```kotlin
val cum = DoubleArray(n + 1)
for (i in 1..n) {
    cum[i] = cum[i - 1] + distance(points[i - 1], points[i % n])
}
val perimeter = cum[n]

val target = (startS + perimeter * (k.toDouble() / count)) % perimeter
pointAtArcLength(points, cum, target)
```

Reuse the explicit final-to-first segment and cumulative arc-length structure. Promote it into one public pure `ClosedReferencePath` used by editor placement, direction-relative progress, whole-course preflight, and Ghost matching.

**Nearest finite-segment projection analog:** `track/ReferenceLineExtractor.kt` lines 306-335.

```kotlin
for (j in 0 until n) {
    val a = points[j]
    val b = points[(j + 1) % n]
    val t = projectionParameter(anchor, a, b)
    val px = a.x + (b.x - a.x) * t
    val py = a.y + (b.y - a.y) * t
    // retain the nearest candidate
}
```

The new projection result should also carry absolute recorded progress, lateral distance, segment identity, and ambiguity information. Ignore zero-length segments and reject non-finite/degenerate paths with typed results.

**Canonical projection:** `lap/LocalProjection.kt` lines 22-45.

```kotlin
fun toLocal(point: GeoPoint): LocalPoint = LocalPoint(
    x = (point.longitude - origin.longitude) * metersPerDegLon,
    y = (point.latitude - origin.latitude) * metersPerDegLat,
)

fun toGeo(point: LocalPoint): GeoPoint = ...
```

Canonical saved geometry remains latitude/longitude. Local meters are algorithm state; pixels/Compose `Offset` are rendering state only.

**Render transform analog:** `review/TraceProjection.kt` lines 54-71 and 120-134.

```kotlin
fun project(
    layers: List<List<GeoPointDto>>,
    width: Double,
    height: Double,
    padding: Double,
): List<List<TracePoint>>
```

Refactor toward a reusable viewport object with both geo/local-to-screen and screen-to-local conversion. Preserve common bounding-box projection across all layers.

**Pure state analog:** `track/TrackReviewState.kt` lines 36-68.

```kotlin
data class TrackReviewState(
    val trackName: String,
    val extraction: ReferenceLineExtraction,
    val startFinish: StartFinishLineDto? = null,
    val sectors: List<SectorLineDto> = emptyList(),
) {
    val canSave: Boolean
        get() = isReferenceReady && extraction.referenceLine != null
}
```

Keep editor constraints in a plain immutable Kotlin state object. Compose converts drag coordinates through the viewport and sends a candidate point/progress; the editor snaps to the path, enforces cyclic spacing, recomputes tangent/normal, and returns copied state.

**Canvas rendering analog:** `ui/TraceView.kt` lines 38-88.

```kotlin
Canvas(modifier = Modifier.fillMaxWidth().heightIn(...)) {
    for (layer in layers) {
        if (layer.points.size < 2) continue
        drawTraceLines(...)
    }
}
```

Extend this rendering style for handles and selected boundaries. Do not persist endpoint drags or screen coordinates. Users drag only the boundary's reference progress.

No exact editor analog exists. The planner should use the `05-RESEARCH.md` clean-room formulas for smoothed tangent, perpendicular line generation, drag snap, minimum cyclic spacing, and finite-line self-intersection validation.

---

### Direction-specific course construction and complete Sector timing

**Apply to:**

- `track/CourseGeometryBuilder.kt`
- `lap/TimingLines.kt`
- `lap/LapModels.kt`
- `lap/LapEngine.kt`
- `lap/CrossingDetector.kt`
- `session/TimingSessionRecorder.kt`

**Course-definition seam:** `lap/TimingLines.kt` lines 10-43.

```kotlin
data class CourseDefinition(
    val startFinish: StartFinishLine,
    val sectors: List<SectorLine> = emptyList(),
) {
    val orderedSectors: List<SectorLine>
        get() = sectors.sortedBy { it.order }
}
```

Refactor this seam so persisted intermediate boundaries and derived complete `SectorDefinition`s are distinct. For `N` Sectors persist `N - 1` intermediate placements and derive `N` intervals:

```text
Start/Finish -> Sector 1 -> Boundary 1 -> ... -> Sector N -> Start/Finish
```

Direction-specific course construction must:

- Use recorded progress `wrap(s - start, L)` and reverse progress `wrap(start - s, L)`.
- Reverse Sector order.
- Swap `pointA`/`pointB` on start/finish and every intermediate boundary.
- Supply an explicit accepted approach side from the first crossing; do not learn direction at runtime.

**Finite line detection primitive:** `lap/CrossingDetector.kt` lines 78-106.

```kotlin
val crossing = SegmentGeometry.intersectMovementWithLine(
    moveStart = movement.startLocal,
    moveEnd = movement.endLocal,
    lineA = projection.toLocal(lineA),
    lineB = projection.toLocal(lineB),
) ?: return null

return CrossingCandidate(
    crossingMillis = SegmentGeometry.interpolateTimestamp(...),
    signedSide = crossing.signedSideBefore,
    ...
)
```

Keep detection stateless and geometry-only. Apply explicit boundary orientation, expected-order, quality, cooldown, and timing policy in `LapEngine`.

**Streaming crossing ordering:** `lap/LapEngine.kt` lines 88-114.

```kotlin
crossings.sortedWith(
    compareBy({ it.candidate.crossingMillis }, { it.candidate.ratio }),
).forEach { pending ->
    if (pending.sector == null) {
        handleStartFinish(pending.candidate, movement)
    } else {
        handleSectorCrossing(pending.sector, pending.candidate, movement)
    }
}
```

Retain interpolation-time/ratio ordering because one low-frequency movement may cross multiple boundaries.

**Legacy behavior to replace:** `lap/LapEngine.kt` lines 195-242.

```kotlin
val splitMillis = candidate.crossingMillis - lapStart
val event = SectorEvent(
    crossingMillis = candidate.crossingMillis,
    splitMillis = splitMillis,
)
```

V2 output needs separate duration and cumulative split:

```kotlin
data class SectorResult(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val cumulativeSplitMillis: Long,
)
```

The first intermediate crossing closes Sector 1. Each later expected crossing subtracts the prior accepted boundary time. The accepted start/finish crossing closes the final Sector and lap at the same interpolated timestamp. Out-of-order, duplicate, and opposite-direction crossings do not advance Sector state. A missed boundary must not prevent lap completion; represent incomplete Sector coverage explicitly.

**Recorder fan-out pattern:** `session/TimingSessionRecorder.kt` lines 168-229.

```kotlin
fun onSample(sample: LocationSample) {
    val state = engine.onSample(sample)
    samples.add(sample)
    ...
    if (currentLapNumber != null) {
        deltaEngine.update(sample)
    }
    checkpoint()
}
```

Preserve the unconditional lap-engine and raw-sample path. Course matching may suppress Ghost output, but it must never block `engine.onSample`, `samples.add`, lap completion, Sector calculation, or checkpointing.

---

### Ghost compatibility and course-progress matching

**Apply to:**

- `ghost/GhostCompatibility.kt`
- `ghost/CourseProgressMatcher.kt`
- `ghost/GhostModels.kt`
- `ghost/LiveDeltaEngine.kt`
- `ghost/ReferenceLapSelector.kt`
- reference operations in both stores

**Typed suppression pattern:** `ghost/GhostModels.kt` lines 127-167.

```kotlin
enum class DeltaUnavailableReason {
    NoCurrentLap,
    NoReference,
    InsufficientCurrentSamples,
    InsufficientReference,
    PoorGpsQuality,
    UnmatchedProgress,
}

sealed interface LiveDeltaSnapshot {
    data class Available(...) : LiveDeltaSnapshot
    data class Unavailable(val reason: DeltaUnavailableReason) : LiveDeltaSnapshot
}
```

Use the same typed-state approach for `CourseMatchResult.Matched` / `Unmatched` and preflight `Ready` / `Blocked` / `Unavailable`. Never throw from the realtime matching path.

**Stale-value prevention:** `ghost/LiveDeltaEngine.kt` lines 79-96 and 144-147.

```kotlin
if (!lapActive) return emit(Unavailable(NoCurrentLap))
val ref = reference ?: return emit(Unavailable(NoReference))
if (accuracy != null && accuracy > maxHorizontalAccuracyMeters) {
    return emit(Unavailable(PoorGpsQuality))
}

private fun emit(next: LiveDeltaSnapshot): LiveDeltaSnapshot {
    snapshot = next
    return next
}
```

Every sample must overwrite live delta state. An unmatched sample emits `--`; a later matched sample naturally emits `Available` again without resetting timing.

**Legacy progress behavior to replace:** `ghost/LiveDeltaEngine.kt` lines 98-139.

```kotlin
accumulatedMeters += sqrt(dx * dx + dy * dy)
val progress = accumulatedMeters
val referenceElapsed = curve.elapsedAtProgress(clamped)
```

Replace traveled-distance accumulation with direction-relative `ClosedReferencePath` progress. Matching needs lateral-distance, ambiguity, and continuity gates. Backward progress is valid when a driver reverses; it is not a pause condition.

**Fastest-selection pattern:** `ghost/ReferenceLapSelector.kt` lines 31-55 and 63-67.

```kotlin
fun fasterOf(current: ReferenceLap?, candidate: ReferenceLap?): ReferenceLap? {
    if (current == null) return candidate
    if (candidate == null) return current
    return if (candidate.durationMillis < current.durationMillis) candidate else current
}
```

Keep the strict-faster/tie-preserves-existing rule, but require equal `CourseCompatibilityKey` before comparing candidates.

**Storage defense in depth:** `storage/FileSessionStore.kt` lines 257-270.

```kotlin
payload.trackId != trackId -> "track id mismatch"
payload.source.isSimulated != isSimulated -> "source boundary mismatch"
payload.progressPoints.size < 2 -> "reference progress curve too short"
```

Expand these checks so payload profile, geometry compatibility, direction, and source exactly match the requested key.

No exact course matcher analog exists. Use the single `ClosedReferencePath.project()` primitive and the centralized thresholds from `05-RESEARCH.md`; cover hairpins/parallel straights, temporary excursion, backward movement, and rematch.

---

### Session bootstrap, wrong-course preflight, and provider provenance

**Apply to:**

- `session/SessionController.kt`
- `session/SessionModels.kt`
- `session/TimingSessionRecorder.kt`
- `track/TrackProfileController.kt`

**Controller construction/start pattern:** `session/SessionController.kt` lines 85-134.

```kotlin
fun startTiming(trackId: String): StartTimingResult {
    val track = loadTrackForTiming(trackId)
        ?: return StartTimingResult.Blocked(START_TIMING_BLOCKED_COPY)
    val course = courseFromTrack(track.startFinish, track.sectors)
        ?: return StartTimingResult.Blocked(START_TIMING_BLOCKED_COPY)
    ...
    store.saveTimingDraft(...)
    return StartTimingResult.Started(session.id)
}
```

Change the input from arbitrary `trackId` to resolved current profile/revision/direction plus latest usable GPS fix and active-provider provenance. Start flow:

1. Resolve persisted current profile without fallback.
2. Resolve latest active timing-ready revision.
3. Build direction-specific `CourseDefinition`.
4. Run whole-reference-line preflight.
5. Return typed blocked state with explicit override action when clearly far.
6. On override, persist `CoursePreflightSnapshot` in the draft/session.
7. Construct recorder from immutable `CourseSnapshot`.

**Current provenance bug:** `session/SessionController.kt` lines 44-50.

```kotlin
private val sourceForTrack: (Track) -> SourceMetadata = { track ->
    SourceMetadata(
        source = track.source.source,
        isSimulated = track.source.isSimulated,
        label = track.source.label,
    )
}
```

Do not copy this. Pass source metadata from the active provider/run. A real run on a Track created with simulated marking data is still real.

**Typed start result pattern:** `session/SessionModels.kt` lines 301-311.

```kotlin
sealed interface StartTimingResult {
    data class Started(val sessionId: String) : StartTimingResult
    data class Blocked(val message: String) : StartTimingResult
}
```

Extend this sealed result rather than using exceptions or booleans. Distinguish no selection, unavailable selection, missing confirmed start/finish, poor GPS readiness, wrong-course block, and override-ready state.

Wrong-course preflight has no close existing analog. It must compare the fix with every segment including last-to-first; subtract horizontal-accuracy uncertainty; return `Unavailable` for stale/non-finite/unreliable fixes; and run only before Timing starts.

---

### Drive, Review, editor, and platform bootstrap

**Apply to:**

- `ui/DriveMarkingController.kt`
- `ui/DriveScreen.kt`
- `ui/ReviewScreen.kt`
- `ui/TrackEditorScreen.kt`
- `review/ReviewModels.kt`
- `App.kt` / `ui/AppShell.kt`
- `iosMain/.../MainViewController.kt`

**Plain-controller snapshot pattern:** `ui/DriveMarkingController.kt` lines 35-53 and 95-103.

```kotlin
data class DriveMarkingSnapshot(
    val phase: DriveMarkingPhase,
    ...
    val canStartTiming: Boolean,
    val startTimingBlockedReason: String,
)

class DriveMarkingController(
    private val provider: LocationSampleProvider,
    private val store: LocalSessionStore,
    ...
)
```

Expose profile rows, exact current selection, selected direction, preflight state, and direct selection action in immutable plain-Kotlin snapshots. Compose should render and forward actions, not resolve revisions or choose Tracks.

**Drive state/action wiring:** `ui/DriveScreen.kt` lines 65-96 and 198-266.

```kotlin
val controller = remember { DriveMarkingController(provider = provider, store = sessionStore) }
var snapshot by remember { mutableStateOf(controller.snapshot()) }

LaunchedEffect(Unit) {
    controller.refreshSavedTracks()
    snapshot = controller.snapshot()
}
```

Use this event-forwarding pattern for selection/direction/preflight override. Add a compact selector that always displays the selected Track name. If no timing-ready selection exists, keep Timing blocked and present a direct selector action.

**Passive moving surface and safety:** `ui/DriveScreen.kt` lines 367-376 and 540-565.

```kotlin
Text(
    text = "Closed-course use only. Phone GPS accuracy varies — this is not pro-grade timing. Verify before trusting lap data.",
)
```

Keep explicit closed-course/private-track language and keep profile/editor controls off the moving fullscreen timing surface.

**Review detail pattern:** `ui/ReviewScreen.kt` lines 135-179.

```kotlin
if (expanded) {
    if (row.type == ReviewEntryType.TimingSession) {
        TimingSessionReviewDetail(...)
    } else {
        RowDetail(...)
    }
}
```

Extend Track detail with `Set as current track`, revision history, rename, archive, and duplicate. Archived profiles leave active selectors but remain navigable from history. Confirmation is required for archive; there is no destructive delete.

**Historical geometry anti-pattern:** `ui/ReviewScreen.kt` lines 331-346.

```kotlin
val trackResult = remember(trackId) { sessionStore.loadTrack(trackId) }
val refPoints = track?.referenceLine?.points ?: emptyList()
val startFinish = track?.startFinish ?: sessionPayload?.session?.startFinish
val sectors = track?.sectors ?: sessionPayload?.session?.sectors ?: emptyList()
```

Do not load latest profile geometry for TimingSession Review. Render the session's `CourseSnapshot`; exact-revision lookup may supplement metadata only.

**Complete Sector review shape:** `review/ReviewModels.kt` lines 34-43 and 81-112.

Current `ReviewSectorSplit(splitMillis)` mirrors legacy cumulative semantics. Add duration and cumulative split as separate fields for V2 and explicitly label V1 rows as legacy cumulative splits.

**iOS injection analog:** Android `MainActivity.kt` lines 33-43.

```kotlin
StoragePaths.initialize(this)
App(
    orientationController = orientationController,
    sessionStore = StoragePaths.fileSessionStore(),
    exportShareTarget = shareTarget,
)
```

Change `iosMain/.../MainViewController.kt` from `App()` with the default in-memory store to `App(sessionStore = StoragePaths.fileSessionStore(), ...)`. This is required before claiming profile/current-selection persistence on iOS.

---

### Deterministic tests and fixtures

**Apply to all new Phase 5 tests and extensions.**

**Injected filesystem/fault pattern:** `storage/FileSessionStoreTest.kt` lines 71-147 and 169-197.

```kotlin
val fs = InMemoryFileSystem()
val failing = FailingWritesFileSystem(fs) { path ->
    path.name.startsWith("index.json")
}
val store = FileSessionStore(failing, root)

assertFailsWith<IOException> {
    store.saveTrackBundle(track(), marking(), app)
}
assertTrue(fs.exists(root / "tracks" / "track-1.json"))
assertFalse(fs.exists(root / "index.json"))
```

Use this for idempotent V1→V2 migration, interruption between payload/index/current-selection writes, retained originals, and index rebuild. Literal historical JSON resources are mandatory; constructing a V1 Kotlin object is not enough.

**Pure geometry fixture pattern:** `track/ReferenceLineExtractorTest.kt` lines 41-56 and 113-128.

```kotlin
val result = ReferenceLineExtractor.extract(session)
assertTrue(result.isReady)
assertTrue(result.referenceLine!!.isClosed)
assertEquals(originalSamples, result.rawSamples)
assertEquals(originalSamples, session.samples)
```

Assert deterministic values and input immutability for perimeter, wrapped `pointAt`, nearest projection, equal arc placement, tangent/normal, drag constraints, and invalid geometry.

**Streaming replay pattern:** `lap/LapEngineTest.kt` lines 169-185.

```kotlin
val engine = LapEngine(sectorCourse, lenient)
engine.onSample(sample(0, -10.0))
engine.onSample(sample(2_000, 10.0))
engine.onSample(sample(4_000, 40.0))
val state = engine.onSample(sample(6_000, 60.0))
```

Use timestamped synthetic crossings for all `N = 2..6`, final-Sector closure at start/finish, separate duration/cumulative split, missed boundary, out-of-order/duplicate rejection, recorded/reverse orientation, and turnaround false-short-lap prevention.

**Full lifecycle integration pattern:** `session/TimingGhostIntegrationTest.kt` lines 267-359.

```kotlin
val provider = SimulatedGpsProvider(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT)
provider.start()
assertIs<StartTimingResult.Started>(controller.startTiming(track.id))

repeat(provider.sampleCount - preTimingSamples) {
    controller.ingestSample(assertNotNull(provider.nextSample()))
}

controller.stop()
assertIs<SaveDraftResult.Saved>(controller.saveStoppedDraft())
```

Use the normal provider → controller → recorder → store path. Add assertions that an off-course/ambiguous interval changes delta `Available -> Unavailable -> Available` while sample count, elapsed time, lap state, and draft checkpoints continue.

**Selection regression to invert:** `ui/DriveMarkingControllerTest.kt` lines 113-132 currently asserts newest-ready selection. Replace it with tests proving:

- migration/no-selection does not choose any profile;
- selecting exact profile/direction survives a new controller/store instance;
- archiving current clears selection and does not choose another profile;
- corrupt/stale selection is unavailable;
- non-current newly created profiles do not steal selection.

Required test ownership:

| Test File | Primary Assertions |
|---|---|
| `storage/SchemaMigrationTest.kt` | Literal V1 dispatch, unknown future fail-closed, side-by-side/idempotent migration, fault recovery |
| `storage/CurrentTrackSelectionTest.kt` | Exact persisted selection/direction, no fallback, archived/corrupt unavailable |
| `storage/TrackRevisionStoreTest.kt` | Append-only revisions, metadata update, archive preservation, independent duplicate |
| `track/ClosedReferencePathTest.kt` | Closing segment, wrapping, arc placement, tangent, nearest/ambiguous projection |
| `track/CourseProfileEditorTest.kt` | 2–6/default 3, `N-1` boundaries, constrained drag, spacing/validation |
| `lap/CompleteSectorReplayTest.kt` | Full-lap coverage, final Sector, duration vs Split, missed boundary behavior |
| `lap/CourseDirectionReplayTest.kt` | Reversed order/orientation, opposite crossing rejection, turnaround integrity |
| `ghost/GhostCompatibilityTest.kt` | Sector-only reuse; geometry/direction/source/duplicate isolation |
| `ghost/CourseProgressMatcherTest.kt` | Parallel ambiguity, excursion suppression, rematch, backward progress without timing pause |
| `session/WrongCoursePreflightTest.kt` | Whole-loop distance, pit allowance, far block, stale/poor fix unavailable, persisted override |
| `session/CourseProfileIntegrationTest.kt` | Selection → preflight → timing → snapshot → save → Review across revisions |
| existing `JsonExportTest.kt` / `ReviewSummaryTest.kt` | V1 legacy semantics and V2 immutable snapshot/complete Sectors/override metadata |

## Shared Patterns

### Validation and error handling

Source: `storage/FileSessionStore.kt` lines 279-290 and `track/ReferenceLineExtractor.kt` lines 120-126.

- Validate at construction for programmer-controlled configuration.
- Return typed `Corrupt`, `Unavailable`, or `Unmatched` for persisted/realtime input failures.
- Validate finite coordinates, bounded list sizes, valid sector count, `N-1` boundaries, revision ordering, ID safety, and identity agreement before geometry work.
- Unknown schema versions fail closed.

### Dependency boundaries

Source: `lap/LapEngine.kt` lines 6-31 and `ui/DriveMarkingController.kt` lines 79-103.

- Lap, direction, closed-path, preflight, revision compatibility, and matching logic stay in common pure Kotlin.
- Compose renders immutable state and forwards actions.
- Platform code provides storage roots and location providers only.
- Storage DTOs remain separate from lap-engine domain types.

### Raw evidence and historical truth

Source: `track/ReferenceLineExtractor.kt` lines 63-85 and `session/TimingSessionRecorder.kt` lines 168-175.

- Preserve source marking samples and raw timing samples.
- Never mutate historical revision geometry.
- Persist exact course snapshot and preflight override with every V2 draft/session.
- Review/export read the session snapshot, not current profile state.

### Real/simulated isolation

Source: `storage/FileSessionStore.kt` lines 257-270 and `session/TimingGhostIntegrationTest.kt` lines 247-263.

- Source is part of compatibility and storage lookup.
- Validate requested source against payload source.
- Active provider/run determines session source.
- Demo data may have its own Ghost but never updates a real slot.

### Safety presentation

Source: `ui/DriveScreen.kt` lines 367-376 and 540-565.

- Keep closed-course/private-track language visible.
- Keep the timing dash passive while moving.
- Track selection/editor/revision actions happen before Timing or in Review.
- Do not add map tiles, nearby-track recommendation, external GNSS, or glasses output in this phase.

## No Close Analog Found

| File / Responsibility | Reason | Planner Fallback |
|---|---|---|
| `storage/SchemaMigrations.kt` | Current store decodes one concrete serializer; no explicit version-dispatch/migration layer exists | Use frozen DTO and `JsonElement` dispatch pattern from `05-RESEARCH.md`, retaining typed `LoadResult` |
| `ui/TrackEditorScreen.kt` gesture handling | Existing `TraceView` renders but has no pointer/drag interaction | Use Compose `pointerInput`/`detectDragGestures`; pass candidates into pure editor state |
| `ghost/CourseProgressMatcher.kt` | Existing delta uses traveled distance and has no course projection ambiguity/continuity model | Reuse `ClosedReferencePath.project()` and typed `Unavailable` pattern |
| wrong-course preflight decision | No whole-course, one-time, override-capable preflight exists | Build a pure typed decision over `ClosedReferencePath`; integrate before recorder construction only |

## Metadata

**Analog search scope:** `shared/src/commonMain`, `shared/src/commonTest`, `shared/src/androidMain`, `shared/src/iosMain`, `androidApp/src/main`

**Primary analog files read:** 32 production/platform files and 5 test files

**Project-local skills:** no `.codex/skills/` or `.agents/skills/` directory exists

**Pattern extraction date:** 2026-06-26
