package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.export.JsonExportService
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityKey
import com.huanfuli.lapsight.shared.ghost.LiveDeltaSnapshot
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.LapEvent
import com.huanfuli.lapsight.shared.lap.SectorResult
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Standing full-pipeline N-run determinism gate (D-25, D-26, D-27, D-28).
 *
 * Replay determinism is a HARD gate for Phase 5.1: the same raw GPS samples driven
 * through the SAME `SessionController` + `TimingSessionRecorder` pipeline must
 * produce byte-identical algorithmic output every run, and an exported field
 * session must decode back through [SessionReplayDecoder] to that same output
 * (D-25). The deterministic surface asserted here is the full D-26 set — completed
 * lap list, `completedSectorResults`, the ordered [LiveDeltaSnapshot] availability/
 * state-transition sequence, and the resolved [CourseCompatibilityKey]/direction —
 * not just `finalState` (which the legacy `ReplayTest.replayIsDeterministic` was
 * limited to). Only display formatting/rounding may differ (D-27); every value
 * compared here is exact (Long-millis times via data-class structural equality,
 * never a tolerance). Any algorithmic drift fails the suite so it can be diagnosed
 * by replay before a field anomaly is attributed to data quality (D-28).
 *
 * The fixtures all share the canonical [GpsFixtureLibrary] oval, crossed once per
 * loop by a perpendicular start/finish; the oval's natural travel completes laps
 * under the [CourseDirection.Reverse] configuration (the recorded-direction config
 * deterministically rejects the same physical crossings — exercised by
 * [recordedAndReverseDirectionDecisionsAreEachDeterministicAndDistinct]).
 */
class FullPipelineDeterminismTest {

    private val app = AppMetadata(appVersion = "test", platform = "test")
    private val config = LapEngineConfig.lenientForTests()
    private val fixedClock: () -> Long = { 1_700_000_000_000L }

    /**
     * Immutable capture of the full deterministic pipeline surface for one run.
     * Kotlin data-class equality compares every field exactly, so two runs are
     * byte-identical iff this value is equal (no custom comparator, no tolerance).
     */
    private data class PipelineCapture(
        val direction: CourseDirection,
        val compatibilityKey: CourseCompatibilityKey,
        val completedLaps: List<LapEvent>,
        val completedSectorResults: List<SectorResult>,
        val liveDeltaSequence: List<LiveDeltaSnapshot>,
    )

    /** One loop (240 oval points) of the canonical oval as a closed reference path for the live matcher. */
    private fun ovalReferenceLine(): TrackReferenceLine = TrackReferenceLine(
        points = GpsFixtureLibrary.cleanTenLoop().take(240)
            .map { GeoPointDto(latitude = it.latitude, longitude = it.longitude) },
        isClosed = true,
    )

    private fun ovalTrack(): Track = Track(
        id = "oval-1",
        name = "Determinism Oval",
        createdAtEpochMillis = 1_000L,
        sourceMarkingSessionId = "mark-1",
        source = SourceMetadata(source = LocationSource.Simulated, isSimulated = true, label = "Demo"),
        referenceLine = ovalReferenceLine(),
        startFinish = GpsFixtureLibrary.closedCoursePerpendicularStartFinish(),
        sectors = emptyList(),
    )

    private fun newStoreWithTrack(direction: CourseDirection): Pair<InMemorySessionStore, Track> {
        val store = InMemorySessionStore()
        val track = ovalTrack()
        store.saveTrackBundle(
            track,
            TrackMarkingSession(
                id = "mark-1",
                createdAtEpochMillis = 1_000L,
                source = SourceMetadata(source = LocationSource.Simulated, isSimulated = true, label = "Demo"),
                samples = emptyList(),
            ),
            app,
        )
        store.setCurrentSelection(CurrentTrackSelection(profileId = track.id, direction = direction))
        return store to track
    }

    /**
     * Run the full `SessionController` pipeline once over [scenarioId] under
     * [direction] and capture the complete deterministic surface. The ordered
     * [LiveDeltaSnapshot] sequence is captured with the `ReplayResult.sectorEvents`
     * change-detection idiom — a snapshot is recorded whenever it DIFFERS from the
     * previous one, never de-duplicating a sticky field (WR-05) — so availability/
     * state transitions are preserved and identical-looking steps are not collapsed.
     */
    private fun runOnce(scenarioId: String, direction: CourseDirection): PipelineCapture {
        val (store, track) = newStoreWithTrack(direction)
        val controller = SessionController(
            store = store,
            appMetadata = app,
            engineConfig = config,
            now = fixedClock,
        )
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()
            ?: error("recorder must be available after start")

        val deltas = mutableListOf<LiveDeltaSnapshot>()
        var previous: LiveDeltaSnapshot? = null
        for (sample in GpsFixtureLibrary.scenario(scenarioId).samples) {
            recorder.onSample(sample)
            val snap = controller.liveDelta()
            if (snap != previous) deltas.add(snap)
            previous = snap
        }

        val state = recorder.timingState
        return PipelineCapture(
            direction = direction,
            compatibilityKey = recorder.session.courseCompatibilityKey,
            completedLaps = state.completedLaps,
            completedSectorResults = state.completedSectorResults,
            liveDeltaSequence = deltas,
        )
    }

    // --- One @Test per GPS regime: two runs must be structurally identical -------

    @Test
    fun cleanLoopsReplayDeterministically() {
        val a = runOnce(GpsFixtureLibrary.CLEAN_10_LOOP, CourseDirection.Reverse)
        val b = runOnce(GpsFixtureLibrary.CLEAN_10_LOOP, CourseDirection.Reverse)
        assertEquals(a, b, "clean-loop full pipeline must be byte-identical across runs (D-25/D-26)")
        assertTrue(
            a.completedLaps.isNotEmpty(),
            "clean loops must complete laps so the determinism check is meaningful",
        )
        // Lap times compared as exact Long-millis (D-27: no tolerance on algorithmic output).
        assertEquals(
            a.completedLaps.map { it.durationMillis },
            b.completedLaps.map { it.durationMillis },
            "lap times must be exactly equal millis across runs",
        )
    }

    @Test
    fun noiseAndDriftReplaysDeterministically() {
        val a = runOnce(GpsFixtureLibrary.NOISE_DRIFT, CourseDirection.Reverse)
        val b = runOnce(GpsFixtureLibrary.NOISE_DRIFT, CourseDirection.Reverse)
        assertEquals(a, b, "noise/drift full pipeline must be byte-identical across runs (D-26)")
        assertTrue(a.completedLaps.isNotEmpty(), "noise/drift regime must still complete laps")
    }

    @Test
    fun droppedLowFrequencyReplaysDeterministically() {
        val a = runOnce(GpsFixtureLibrary.DROPPED_LOW_FREQUENCY, CourseDirection.Reverse)
        val b = runOnce(GpsFixtureLibrary.DROPPED_LOW_FREQUENCY, CourseDirection.Reverse)
        assertEquals(a, b, "dropped/low-frequency full pipeline must be byte-identical across runs (D-26)")
        assertTrue(a.completedLaps.isNotEmpty(), "low-frequency regime must reconstruct laps")
    }

    @Test
    fun degradationDuringTimingReplaysDeterministically() {
        val a = runOnce(GpsFixtureLibrary.DEGRADATION_DURING_TIMING, CourseDirection.Reverse)
        val b = runOnce(GpsFixtureLibrary.DEGRADATION_DURING_TIMING, CourseDirection.Reverse)
        assertEquals(
            a,
            b,
            "good->poor->good mid-lap degradation must replay identically, including the " +
                "ordered live-delta suppression/recovery transitions (D-24/D-26)",
        )
        assertTrue(a.completedLaps.isNotEmpty(), "the lap still closes through the degraded span")
    }

    @Test
    fun closedCoursePerpendicularReplaysDeterministically() {
        val a = runOnce(GpsFixtureLibrary.CLOSED_COURSE_PERPENDICULAR, CourseDirection.Reverse)
        val b = runOnce(GpsFixtureLibrary.CLOSED_COURSE_PERPENDICULAR, CourseDirection.Reverse)
        assertEquals(a, b, "interpolated perpendicular crossings must replay identically (D-19/D-26)")
        assertTrue(a.completedLaps.isNotEmpty(), "perpendicular low-frequency loops must count laps")
    }

    @Test
    fun outlierRecoveryReplaysDeterministically() {
        // One off-track outlier loop then recovery: the engine must drop the outlier
        // and resume timing identically on every run (D-26/D-28).
        val a = runOnce(GpsFixtureLibrary.ONE_OUTLIER_LOOP, CourseDirection.Reverse)
        val b = runOnce(GpsFixtureLibrary.ONE_OUTLIER_LOOP, CourseDirection.Reverse)
        assertEquals(a, b, "outlier-then-recovery full pipeline must be byte-identical across runs")
        assertTrue(a.completedLaps.isNotEmpty(), "recovery regime must still complete the clean laps")
    }

    @Test
    fun recordedAndReverseDirectionDecisionsAreEachDeterministicAndDistinct() {
        // Each direction config is internally deterministic across runs (D-26: course/
        // profile/direction decisions are deterministic)...
        val reverseA = runOnce(GpsFixtureLibrary.CLEAN_10_LOOP, CourseDirection.Reverse)
        val reverseB = runOnce(GpsFixtureLibrary.CLEAN_10_LOOP, CourseDirection.Reverse)
        assertEquals(reverseA, reverseB, "Reverse direction must be deterministic across runs")

        val recordedA = runOnce(GpsFixtureLibrary.CLEAN_10_LOOP, CourseDirection.Recorded)
        val recordedB = runOnce(GpsFixtureLibrary.CLEAN_10_LOOP, CourseDirection.Recorded)
        assertEquals(recordedA, recordedB, "Recorded direction must be deterministic across runs")

        // ...and the resolved direction/compatibility-key is part of that surface.
        assertEquals(CourseDirection.Reverse, reverseA.compatibilityKey.direction)
        assertEquals(CourseDirection.Recorded, recordedA.compatibilityKey.direction)

        // The SAME physical replay produces a DIFFERENT (but deterministic) outcome
        // per direction: Reverse accepts the oval's crossings and completes laps,
        // Recorded rejects every one of them (D-21). A determinism gate that ignored
        // direction would miss this; structural inequality proves it does not.
        assertNotEquals(
            reverseA,
            recordedA,
            "direction must change the algorithmic outcome (Reverse completes, Recorded rejects)",
        )
        assertTrue(reverseA.completedLaps.isNotEmpty(), "Reverse completes the oval laps")
        assertTrue(recordedA.completedLaps.isEmpty(), "Recorded rejects the opposite-direction crossings")
    }

    // --- D-25/D-28: export -> decode -> replay is identical across decodes -------

    @Test
    fun exportedSessionDecodesToIdenticalReplayAcrossRuns() {
        val (store, track) = newStoreWithTrack(CourseDirection.Reverse)
        val controller = SessionController(
            store = store,
            appMetadata = app,
            engineConfig = config,
            now = fixedClock,
        )
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!
        GpsFixtureLibrary.scenario(GpsFixtureLibrary.CLEAN_10_LOOP).samples
            .forEach { recorder.onSample(it) }
        controller.stop()
        val saved = controller.saveStoppedDraft() as SaveDraftResult.Saved

        val bytes = JsonExportService(store).exportTimingSession(saved.sessionId)

        // Decode the exact same exported field bytes twice; the engine output must be
        // identical (lap count + ordered sector events + final state) — the export ->
        // replay link that lets every field anomaly be reproduced (D-25/D-28).
        val first = SessionReplayDecoder.decodeForReplay(bytes, config)
        val second = SessionReplayDecoder.decodeForReplay(bytes, config)
        val r1 = (first as? ReplayDecodeResult.Decoded)?.result
            ?: error("expected Decoded, got $first")
        val r2 = (second as? ReplayDecodeResult.Decoded)?.result
            ?: error("expected Decoded, got $second")

        assertEquals(r1.finalState.lapCount, r2.finalState.lapCount, "decoded lap count must be identical")
        assertEquals(r1.sectorEvents, r2.sectorEvents, "decoded sector events must be identical")
        assertEquals(r1.finalState, r2.finalState, "decoded final timing state must be identical")
        assertTrue(
            r1.finalState.lapCount > 0,
            "the exported field session must replay real laps so the link is meaningful (D-25/D-28)",
        )
    }
}
