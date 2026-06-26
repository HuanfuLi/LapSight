package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.ghost.DeltaUnavailableReason
import com.huanfuli.lapsight.shared.ghost.LiveDeltaSnapshot
import com.huanfuli.lapsight.shared.ghost.ReferenceLapSelector
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.LapEvent
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.track.Track
import kotlin.test.Test
import kotlin.test.assertEquals
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

    private fun controller(source: LocationSource): SessionController = SessionController(
        store = store,
        appMetadata = app,
        engineConfig = LapEngineConfig.lenientForTests(),
        now = { 1_700_000_000_000L },
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

    // --- D-01: timing start loads the persisted reference -----------------------

    @Test
    fun startTimingLoadsPersistedReferenceForSavedTrack() {
        val track = saveTrack(LocationSource.PhoneGps)
        // Pre-persist a real reference covering one full loop.
        val refLap = ReferenceLapSelector.referenceFromLap(
            trackId = track.id,
            sessionId = "prev-session",
            lap = LapEvent(lapNumber = 1, startMillis = 0L, endMillis = 41_000L),
            allSamples = ReplayFixtures.multiLapLoop(listOf(40_000L)),
            isSimulated = false,
        )
        assertNotNull(refLap, "fixture should build a valid reference lap")
        store.saveReferenceLap(
            refLap.toReferencePayload(SourceMetadata(LocationSource.PhoneGps, isSimulated = false), app),
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
            store.loadReferenceLap(track.id, isSimulated = false),
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

        val loaded = store.loadReferenceLap(track.id, isSimulated = false)
        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(loaded)
        assertEquals(track.id, loaded.value.trackId)
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
        assertIs<LoadResult.NotFound>(store.loadReferenceLap(track.id, isSimulated = false))
        // ...but a simulated reference is allowed for UAT.
        assertIs<LoadResult.Loaded<GhostReferencePayloadV1>>(store.loadReferenceLap(track.id, isSimulated = true))
    }
}
