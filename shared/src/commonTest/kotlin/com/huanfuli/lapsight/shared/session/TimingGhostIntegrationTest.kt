package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityKey
import com.huanfuli.lapsight.shared.ghost.DeltaUnavailableReason
import com.huanfuli.lapsight.shared.ghost.LiveDeltaSnapshot
import com.huanfuli.lapsight.shared.ghost.ReferenceLapSelector
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.LapEvent
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory
import com.huanfuli.lapsight.shared.storage.FileSessionStore
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackProfileController
import okio.Buffer
import okio.FileHandle
import okio.FileMetadata
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.Timeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wave 0 coverage for the timing-session ↔ ghost reference integration
 * (Plan 04-02 Task 1).
 *
 * Drives the real [SessionController] / [TimingSessionRecorder] lifecycle over
 * deterministic [ReplayFixtures] and asserts:
 *  - D-01: timing start defaults to the saved Track's persisted fastest reference.
 *  - D-02/D-12: a new fastest valid lap during an active session immediately
 *    becomes the active reference for the following lap.
 *  - D-06/GHOST-02: live delta is fed by the formal recorder (read through the
 *    controller, not `recorderForTest()`).
 *  - D-04/D-24, T-04-06: Save commits the best eligible reference to global
 *    storage; Discard leaves global reference untouched; simulated sessions never
 *    update the real reference.
 */
class TimingGhostIntegrationTest {

    private val store = InMemorySessionStore()
    private val app = AppMetadata(appVersion = "0.4.0", platform = "test")

    private fun controller(
        source: LocationSource,
        engineConfig: LapEngineConfig = LapEngineConfig.lenientForTests(),
        nowMillis: Long = 1_700_000_000_000L,
    ): SessionController = SessionController(
        store = store,
        appMetadata = app,
        engineConfig = engineConfig,
        now = { nowMillis },
        sourceForTrack = { _ ->
            SourceMetadata(
                source = source,
                isSimulated = source == LocationSource.Simulated,
                label = if (source == LocationSource.Simulated) "Demo" else null,
            )
        },
    )

    private fun saveTrack(source: LocationSource): Track {
        val track = TestTrackFactory.savedTrackWithStartFinish(source)
        store.saveTrackBundle(track, TestTrackFactory.markingFor(track, source), app)
        return track
    }

    private fun saveVariablePaceOvalTrack(source: LocationSource = LocationSource.Simulated): Track {
        val samples = GpsFixtureLibrary.scenario(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT).samples
        val anchor = samples.maxByOrNull { it.latitude } ?: error("variable-pace fixture must not be empty")
        val halfLineDegrees = 35.0 / 111_320.0
        val track = Track(
            id = "track-variable-pace-ghost-uat-${source.name.lowercase()}",
            name = "Variable Pace Ghost UAT ${source.name}",
            createdAtEpochMillis = 1_700_000_004_000L,
            sourceMarkingSessionId = "mark-variable-pace-ghost-uat-${source.name.lowercase()}",
            source = SourceMetadata(
                source = source,
                isSimulated = source == LocationSource.Simulated,
                label = if (source == LocationSource.Simulated) "Demo" else null,
            ),
            referenceLine = null,
            startFinish = StartFinishLineDto(
                pointA = GeoPointDto(
                    latitude = anchor.latitude - halfLineDegrees,
                    longitude = anchor.longitude,
                ),
                pointB = GeoPointDto(
                    latitude = anchor.latitude + halfLineDegrees,
                    longitude = anchor.longitude,
                ),
            ),
            sectors = emptyList(),
        )
        val marking = TrackMarkingSession(
            id = track.sourceMarkingSessionId ?: "mark-${track.id}",
            createdAtEpochMillis = track.createdAtEpochMillis,
            source = track.source,
            samples = samples.map { it.toDto() },
        )
        store.saveTrackBundle(track, marking, app)
        return track
    }

    private fun saveProfileAndSelect(track: Track, direction: CourseDirection = CourseDirection.Recorded) {
        val created = TrackProfileController(store).saveProfile(track = track, name = track.name, app = app)
        assertIs<com.huanfuli.lapsight.shared.track.CreateProfileResult.Created>(created)
        store.setCurrentSelection(CurrentTrackSelection(profileId = created.profile.profileId, direction = direction))
    }

    private fun keyFor(
        track: Track,
        isSimulated: Boolean,
        direction: CourseDirection = CourseDirection.Recorded,
        geometryCompatibilityId: String = "${track.id}:g1",
    ): CourseCompatibilityKey = CourseCompatibilityKey(
        profileId = track.id,
        geometryCompatibilityId = geometryCompatibilityId,
        direction = direction,
        isSimulated = isSimulated,
    )

    private fun sourceForKey(key: CourseCompatibilityKey): SourceMetadata = if (key.isSimulated) {
        SourceMetadata(LocationSource.Simulated, isSimulated = true, label = "Demo")
    } else {
        SourceMetadata(LocationSource.PhoneGps, isSimulated = false)
    }

    private fun referencePayload(
        key: CourseCompatibilityKey,
        sessionId: String = "session-${key.profileId}-${key.direction.name}-${key.isSimulated}",
        durationMillis: Long = 1_000L,
    ): GhostReferencePayloadV2 = GhostReferencePayloadV2(
        compatibilityKey = key,
        sessionId = sessionId,
        lapNumber = 1,
        durationMillis = durationMillis,
        source = sourceForKey(key),
        totalDistanceMeters = 100.0,
        samples = listOf(
            com.huanfuli.lapsight.shared.LocationSample(
                elapsedMillis = 0L,
                latitude = 39.8121,
                longitude = -86.1062,
                horizontalAccuracyMeters = 5.0,
                speedMetersPerSecond = 12.0,
                headingDegrees = 90.0,
                altitudeMeters = 200.0,
                source = if (key.isSimulated) LocationSource.Simulated else LocationSource.PhoneGps,
            ).toDto(),
            com.huanfuli.lapsight.shared.LocationSample(
                elapsedMillis = durationMillis,
                latitude = 39.8126,
                longitude = -86.1062,
                horizontalAccuracyMeters = 5.0,
                speedMetersPerSecond = 12.0,
                headingDegrees = 90.0,
                altitudeMeters = 200.0,
                source = if (key.isSimulated) LocationSource.Simulated else LocationSource.PhoneGps,
            ).toDto(),
        ),
        progressPoints = listOf(
            ProgressPointDto(
                elapsedMillis = 0L,
                progressMeters = 0.0,
                normalizedProgress = 0.0,
                latitude = 39.8121,
                longitude = -86.1062,
                localX = 0.0,
                localY = 0.0,
                speedMetersPerSecond = 12.0,
                headingDegrees = 90.0,
                horizontalAccuracyMeters = 5.0,
            ),
            ProgressPointDto(
                elapsedMillis = durationMillis,
                progressMeters = 100.0,
                normalizedProgress = 1.0,
                latitude = 39.8126,
                longitude = -86.1062,
                localX = 0.0,
                localY = 100.0,
                speedMetersPerSecond = 12.0,
                headingDegrees = 90.0,
                horizontalAccuracyMeters = 5.0,
            ),
        ),
        app = app,
    )

    private fun assertExactReferenceIsolation(store: LocalSessionStore) {
        val base = CourseCompatibilityKey(
            profileId = "profile-a",
            geometryCompatibilityId = "profile-a:g1",
            direction = CourseDirection.Recorded,
            isSimulated = false,
        )
        val mismatches = listOf(
            base.copy(isSimulated = true),
            base.copy(direction = CourseDirection.Reverse),
            base.copy(profileId = "profile-a-duplicate", geometryCompatibilityId = "profile-a-duplicate:g1"),
            base.copy(geometryCompatibilityId = "profile-a:g2"),
        )

        store.saveReferenceLap(referencePayload(base), app)

        val loaded = store.loadReferenceLap(base)
        assertIs<LoadResult.Loaded<GhostReferencePayloadV2>>(loaded)
        assertEquals(base, loaded.value.compatibilityKey)

        mismatches.forEach { mismatch ->
            assertIs<LoadResult.NotFound>(
                store.loadReferenceLap(mismatch),
                "reference lookup must not cross-bind through mismatched key $mismatch",
            )
        }
    }

    // --- D-01: timing start loads the persisted reference -----------------------

    @Test
    fun exactCompatibilityKeyIsTheOnlyReferenceLookupSlotInBothStores() {
        assertExactReferenceIsolation(InMemorySessionStore())
        assertExactReferenceIsolation(FileSessionStore(TimingIntegrationFileSystem(), "/lapsight".toPath()))
    }

    @Test
    fun fileStoreRejectsUnsafeReferenceIdsAndPayloadRequestKeyMismatch() {
        val root = "/lapsight".toPath()
        val fs = TimingIntegrationFileSystem()
        val store = FileSessionStore(fs, root)
        val key = CourseCompatibilityKey(
            profileId = "profile-a",
            geometryCompatibilityId = "profile-a:g1",
            direction = CourseDirection.Recorded,
            isSimulated = false,
        )
        store.saveReferenceLap(referencePayload(key), app)

        val referencePath = fs.allFiles.single {
            it.toString().contains("references-v2") && it.name.endsWith(".json")
        }
        val mismatchedPayload = referencePayload(key.copy(direction = CourseDirection.Reverse))
        fs.write(referencePath) {
            writeUtf8(
                FileSessionStore.canonicalJson.encodeToString(
                    GhostReferencePayloadV2.serializer(),
                    mismatchedPayload,
                ),
            )
        }

        val mismatch = store.loadReferenceLap(key)
        assertIs<LoadResult.Corrupt>(mismatch)
        assertTrue(mismatch.reason.contains("compatibility key mismatch"))

        val unsafe = key.copy(profileId = "../escape")
        assertIs<LoadResult.Corrupt>(store.loadReferenceLap(unsafe))
        assertFailsWith<IllegalArgumentException> {
            store.saveReferenceLap(referencePayload(unsafe), app)
        }
        assertTrue(
            fs.allFiles.all { it.toString().startsWith(root.toString()) },
            "unsafe reference IDs must be rejected before any path can escape the app root",
        )
    }

    @Test
    fun startTimingLoadsPersistedReferenceForSavedTrack() {
        val track = saveTrack(LocationSource.PhoneGps)
        val key = keyFor(track, isSimulated = false)
        // Pre-persist a real reference covering one full loop.
        val refLap = ReferenceLapSelector.referenceFromLap(
            trackId = track.id,
            sessionId = "prev-session",
            lap = LapEvent(lapNumber = 1, startMillis = 0L, endMillis = 41_000L),
            allSamples = ReplayFixtures.multiLapLoop(listOf(40_000L)),
            isSimulated = false,
            compatibilityKey = key,
        )
        assertNotNull(refLap, "fixture should build a valid reference lap")
        store.saveReferenceLap(
            refLap.toReferencePayloadV2(SourceMetadata(LocationSource.PhoneGps, isSimulated = false), app),
            app,
        )

        val controller = controller(LocationSource.PhoneGps)
        assertIs<StartTimingResult.Started>(controller.startTiming(track.id))

        val active = controller.activeReference()
        assertNotNull(active, "timing start must load the saved Track's persisted reference (D-01)")
        assertEquals(track.id, active.trackId)
        assertTrue(!active.isSimulated)
    }

    // --- D-02/D-12: first lap unavailable, following lap uses the new reference --

    @Test
    fun firstLapHasNoReferenceThenFollowingLapUsesNewReference() {
        val track = saveTrack(LocationSource.PhoneGps)
        val controller = controller(LocationSource.PhoneGps)
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!

        val samples = ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L))
        val deltasDuringLap1 = mutableListOf<LiveDeltaSnapshot>()
        var sawAvailableAfterLap1 = false

        samples.forEach { sample ->
            recorder.onSample(sample)
            if (recorder.lapCount == 0) {
                deltasDuringLap1.add(controller.liveDelta())
            } else if (controller.liveDelta() is LiveDeltaSnapshot.Available) {
                sawAvailableAfterLap1 = true
            }
        }

        // With no completed lap there is no reference yet (D-11/D-17).
        assertTrue(deltasDuringLap1.isNotEmpty())
        assertTrue(
            deltasDuringLap1.all { it is LiveDeltaSnapshot.Unavailable },
            "live delta must be unavailable before any lap completes",
        )
        assertTrue(
            deltasDuringLap1.any {
                it is LiveDeltaSnapshot.Unavailable && it.reason == DeltaUnavailableReason.NoReference
            },
            "the first active lap with no reference must report NoReference",
        )
        assertNotNull(controller.activeReference(), "a completed lap must produce an active reference")
        assertTrue(
            sawAvailableAfterLap1,
            "the lap following a completed lap must produce an available delta against the new reference (D-12)",
        )
    }

    // --- D-02/D-12: a faster in-session lap replaces the active reference --------

    @Test
    fun fasterInSessionLapBecomesActiveReferenceImmediately() {
        val track = saveTrack(LocationSource.PhoneGps)
        val controller = controller(LocationSource.PhoneGps)
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!

        // lap1 = 40s, lap2 = 32s (faster), lap3 = 36s.
        val samples = ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L, 36_000L))
        val referenceDurationByLapCount = mutableMapOf<Int, Long>()
        samples.forEach { sample ->
            recorder.onSample(sample)
            controller.activeReference()?.let { ref ->
                referenceDurationByLapCount[recorder.lapCount] = ref.durationMillis
            }
        }

        val afterLap1 = referenceDurationByLapCount[1]
        val afterLap2 = referenceDurationByLapCount[2]
        assertNotNull(afterLap1, "a reference must exist after lap 1")
        assertNotNull(afterLap2, "a reference must exist after lap 2")
        assertTrue(
            afterLap2 < afterLap1,
            "a faster lap 2 must immediately replace the active reference (was $afterLap1, now $afterLap2)",
        )
    }

    // --- T-04-06: Discard leaves global reference unchanged ---------------------

    @Test
    fun discardingStoppedDraftDoesNotPersistGlobalReference() {
        val track = saveTrack(LocationSource.PhoneGps)
        val controller = controller(LocationSource.PhoneGps)
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!
        ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L)).forEach { recorder.onSample(it) }
        controller.stop()

        controller.discardDraft()

        assertIs<LoadResult.NotFound>(
            store.loadReferenceLap(keyFor(track, isSimulated = false)),
        )
    }

    // --- T-04-06: Save commits the best eligible reference to global storage -----

    @Test
    fun savingStoppedDraftPersistsNewBestAsGlobalReference() {
        val track = saveTrack(LocationSource.PhoneGps)
        val controller = controller(LocationSource.PhoneGps)
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!
        ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L, 36_000L)).forEach { recorder.onSample(it) }
        controller.stop()

        val saved = controller.saveStoppedDraft()
        assertIs<SaveDraftResult.Saved>(saved)

        val loaded = store.loadReferenceLap(keyFor(track, isSimulated = false))
        assertIs<LoadResult.Loaded<GhostReferencePayloadV2>>(loaded)
        assertEquals(keyFor(track, isSimulated = false), loaded.value.compatibilityKey)
        assertTrue(!loaded.value.source.isSimulated)
        assertTrue(loaded.value.progressPoints.size >= 2, "persisted reference must carry a real progress curve (D-05)")

        val savedSession = store.loadTimingSession(saved.sessionId)
        assertIs<LoadResult.Loaded<TimingSessionPayloadV1>>(savedSession)
        val fastest = savedSession.value.laps.minOf { it.durationMillis }
        assertEquals(fastest, loaded.value.durationMillis, "the persisted reference must be the fastest valid lap")
    }

    // --- D-04/D-24: simulated session save must not pollute real reference -------

    @Test
    fun simulatedSessionSaveDoesNotCreateRealReference() {
        val track = saveTrack(LocationSource.Simulated)
        val controller = controller(LocationSource.Simulated)
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!
        ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L)).forEach { recorder.onSample(it) }
        controller.stop()
        controller.saveStoppedDraft()

        // The real reference slot stays empty (D-04/D-24)...
        assertIs<LoadResult.NotFound>(store.loadReferenceLap(keyFor(track, isSimulated = false)))
        // ...but a simulated reference is allowed for UAT.
        assertIs<LoadResult.Loaded<GhostReferencePayloadV2>>(store.loadReferenceLap(keyFor(track, isSimulated = true)))
    }

    @Test
    fun realProviderRunOnDemoCreatedProfilePersistsRealReference() {
        val track = saveTrack(LocationSource.Simulated)
        saveProfileAndSelect(track)
        val controller = controller(LocationSource.PhoneGps)
        assertIs<StartTimingResult.Started>(controller.startTiming(track.id))
        val recorder = controller.recorderForTest()!!
        ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L)).forEach { recorder.onSample(it) }
        controller.stop()

        assertIs<SaveDraftResult.Saved>(controller.saveStoppedDraft())

        val realKey = keyFor(track, isSimulated = false)
        val simulatedKey = keyFor(track, isSimulated = true)
        val realReference = store.loadReferenceLap(realKey)
        assertIs<LoadResult.Loaded<GhostReferencePayloadV2>>(realReference)
        assertFalse(realReference.value.source.isSimulated)
        assertEquals(realKey, realReference.value.compatibilityKey)
        assertIs<LoadResult.NotFound>(
            store.loadReferenceLap(simulatedKey),
            "a real provider run on a Demo-created profile must not save into the Demo slot",
        )
    }

    // --- D-20..D-24: provider-layer UAT trace through normal timing path --------

    @Test
    fun variablePaceProviderFeedsNormalTimingFlowAndPersistsSimulatedReferenceOnlyOnSave() {
        val track = saveVariablePaceOvalTrack(LocationSource.Simulated)
        val provider = SimulatedGpsProvider(GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT)
        provider.start()

        // The feed is already physically "moving" before the user starts the
        // timing session. Timing must consume the same provider stream from its
        // current position, not a special session-coupled simulator path.
        val preTimingSamples = 12
        repeat(preTimingSamples) {
            assertNotNull(provider.nextSample(), "provider must run before timing starts")
        }
        assertTrue(provider.isRunning)

        val controller = controller(
            source = LocationSource.Simulated,
            engineConfig = LapEngineConfig(
                minLapDurationMillis = 8_000L,
                crossingCooldownMillis = 3_000L,
                maxHorizontalAccuracyMeters = null,
                minSpeedMetersPerSecond = 0.0,
                enforceDirection = false,
            ),
            nowMillis = 1_700_000_004_000L,
        )
        assertIs<StartTimingResult.Started>(controller.startTiming(track.id))

        val referenceDurationByLapCount = mutableMapOf<Int, Long>()
        var sawPositiveDelta = false
        var sawNegativeDelta = false
        repeat(provider.sampleCount - preTimingSamples) {
            val sample = assertNotNull(provider.nextSample(), "provider must keep emitting during timing")
            controller.ingestSample(sample)

            when (val delta = controller.liveDelta()) {
                is LiveDeltaSnapshot.Available -> {
                    if (delta.deltaMillis > 0L) sawPositiveDelta = true
                    if (delta.deltaMillis < 0L) sawNegativeDelta = true
                }
                is LiveDeltaSnapshot.Unavailable -> Unit
            }

            controller.activeReference()?.let { reference ->
                referenceDurationByLapCount[controller.timingRunSnapshot().lapCount] =
                    reference.durationMillis
            }
        }

        assertTrue(
            controller.timingRunSnapshot().lapCount >= 4,
            "starting timing after the feed has begun should still produce completed laps",
        )
        assertEquals(
            27_000L,
            referenceDurationByLapCount[1],
            "first completed lap after timing start becomes the initial in-session reference",
        )
        assertEquals(
            22_000L,
            referenceDurationByLapCount[2],
            "a faster second completed lap must immediately replace the active reference",
        )
        assertEquals(
            20_000L,
            referenceDurationByLapCount[3],
            "the new-best third completed lap must immediately become the next reference",
        )
        assertTrue(sawNegativeDelta, "faster laps must produce negative live delta through the controller path")
        assertTrue(sawPositiveDelta, "slower laps must produce positive live delta through the controller path")

        val activeReference = assertNotNull(controller.activeReference())
        assertEquals(20_000L, activeReference.durationMillis)
        assertTrue(activeReference.isSimulated, "active UAT reference must remain simulated")
        assertIs<LoadResult.NotFound>(
            store.loadReferenceLap(keyFor(track, isSimulated = true)),
            "in-session reference must not persist globally before explicit Save",
        )

        controller.stop()
        val saved = controller.saveStoppedDraft()
        assertIs<SaveDraftResult.Saved>(saved)

        val simulatedReference = store.loadReferenceLap(keyFor(track, isSimulated = true))
        assertIs<LoadResult.Loaded<GhostReferencePayloadV2>>(simulatedReference)
        assertEquals(20_000L, simulatedReference.value.durationMillis)
        assertTrue(simulatedReference.value.source.isSimulated)
        assertEquals("Demo", simulatedReference.value.source.label)
        assertIs<LoadResult.NotFound>(
            store.loadReferenceLap(keyFor(track, isSimulated = false)),
            "simulated UAT reference must not pollute the real reference slot",
        )
    }
}

private class TimingIntegrationFileSystem : FileSystem() {

    private val files = LinkedHashMap<Path, okio.ByteString>()
    private val directories = LinkedHashSet<Path>()

    val allFiles: Set<Path> get() = files.keys.toSet()

    override fun canonicalize(path: Path): Path = path

    override fun metadataOrNull(path: Path): FileMetadata? = when {
        files.containsKey(path) -> FileMetadata(
            isRegularFile = true,
            isDirectory = false,
            size = files.getValue(path).size.toLong(),
        )
        directories.contains(path) -> FileMetadata(isRegularFile = false, isDirectory = true)
        else -> null
    }

    override fun list(dir: Path): List<Path> =
        (files.keys + directories).filter { it.parent == dir }

    override fun listOrNull(dir: Path): List<Path>? =
        if (directories.contains(dir)) list(dir) else null

    override fun source(file: Path): Source {
        val bytes = files[file] ?: throw IOException("no such file: $file")
        return Buffer().apply { write(bytes) }
    }

    override fun sink(file: Path, mustCreate: Boolean): Sink {
        if (mustCreate && files.containsKey(file)) throw IOException("file already exists: $file")
        return StoringSink(file)
    }

    override fun appendingSink(file: Path, mustExist: Boolean): Sink {
        if (mustExist && !files.containsKey(file)) throw IOException("no such file: $file")
        return StoringSink(file, initial = files[file])
    }

    override fun createDirectory(dir: Path, mustCreate: Boolean) {
        if (mustCreate && directories.contains(dir)) throw IOException("directory already exists: $dir")
        directories.add(dir)
    }

    override fun atomicMove(source: Path, target: Path) {
        val bytes = files[source] ?: throw IOException("no such file: $source")
        files[target] = bytes
        files.remove(source)
    }

    override fun delete(path: Path, mustExist: Boolean) {
        val removed = (files.remove(path) != null) || directories.remove(path)
        if (mustExist && !removed) throw IOException("no such file: $path")
    }

    override fun createSymlink(source: Path, target: Path) {
        throw UnsupportedOperationException("symlinks are not supported in tests")
    }

    override fun openReadOnly(file: Path): FileHandle =
        throw UnsupportedOperationException("file handles are not supported in tests")

    override fun openReadWrite(file: Path, mustCreate: Boolean, mustExist: Boolean): FileHandle =
        throw UnsupportedOperationException("file handles are not supported in tests")

    private inner class StoringSink(
        private val path: Path,
        initial: okio.ByteString? = null,
    ) : Sink {
        private val buffer = Buffer().apply { if (initial != null) write(initial) }
        private var closed = false

        override fun write(source: Buffer, byteCount: Long) {
            buffer.write(source, byteCount)
        }

        override fun flush() {}

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() {
            if (closed) return
            closed = true
            files[path] = buffer.readByteString()
        }
    }
}
