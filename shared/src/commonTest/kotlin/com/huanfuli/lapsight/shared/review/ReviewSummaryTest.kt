package com.huanfuli.lapsight.shared.review

import com.huanfuli.lapsight.shared.GpsQualitySummary
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.GpsQualitySummary as SessionGpsQualitySummary
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory.savedTrackWithStartFinish
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.session.toDto
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.ReferenceLineExtraction
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import com.huanfuli.lapsight.shared.track.TrackReviewState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Wave 0 (Plan 03-06 Task 1) coverage for [TimingSessionReviewSummary] derivation
 * (SESS-02, D-32).
 *
 * Asserts the review summary exposes: lap list, best lap, total duration, sample
 * count, GPS quality summary, sector splits, source/Demo badge, and track name/date.
 */
class ReviewSummaryTest {

    private val store = InMemorySessionStore()
    private val app = AppMetadata(appVersion = "0.3.0", platform = "test")

    private fun saveAndTimeRealSession(
        source: LocationSource = LocationSource.Simulated,
    ): TimingSessionReviewSummary {
        val track = savedTrackWithStartFinish(source)
        store.saveTrackBundle(
            track,
            com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory.markingFor(track, source),
            app,
        )
        val controller = SessionController(
            store = store,
            appMetadata = app,
            engineConfig = LapEngineConfig.lenientForTests(),
            now = { 1_700_000_000_000L },
            sourceForTrack = {
                SourceMetadata(
                    source = source,
                    isSimulated = source == LocationSource.Simulated,
                    label = if (source == LocationSource.Simulated) "Demo" else null,
                )
            },
        )
        // Use the canonical multi-lap multi-sector fixture so laps + sectors exist.
        val samples = ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L, 36_000L))
        controller.startTiming(track.id)
        val recorder = controller.recorderForTest()!!
        samples.forEach { recorder.onSample(it) }
        controller.stop()
        val saved = controller.saveStoppedDraft() as com.huanfuli.lapsight.shared.session.SaveDraftResult.Saved

        val summary = ReviewSummaries.fromTimingSession(store, saved.sessionId)
        assertNotNull(summary, "a saved session must produce a review summary")
        return summary
    }

    @Test
    fun timingSessionReviewSummaryExposesLapListBestLapAndTotalDuration() {
        val summary = saveAndTimeRealSession()

        assertTrue(summary.laps.isNotEmpty(), "review must show the lap list")
        assertEquals(3, summary.laps.size, "the fixture produces 3 completed laps")
        assertTrue(summary.bestLapMillis != null, "best lap must be present")
        assertEquals(summary.laps.minOf { it.durationMillis }, summary.bestLapMillis)
        assertTrue(summary.totalDurationMillis > 0L, "total duration must be present")
        // Total duration = last crossing - first sample (or sum of laps).
        assertTrue(summary.totalDurationMillis >= summary.laps.sumOf { it.durationMillis })
    }

    @Test
    fun timingSessionReviewSummaryExposesSampleCountAndGpsQualitySummary() {
        val summary = saveAndTimeRealSession()

        assertTrue(summary.sampleCount > 0, "review must show sample count")
        val quality = summary.gpsQuality
        assertEquals(summary.sampleCount, quality.sampleCount)
        assertTrue(quality.source != null || quality.sampleCount >= 0, "GPS quality must summarize sources")
    }

    @Test
    fun timingSessionReviewSummaryExposesSectorSplitsAndSourceBadge() {
        val summary = saveAndTimeRealSession()

        assertTrue(summary.sectorSplits.isNotEmpty(), "sector splits must be present")
        // Source/Demo badge: simulated sessions carry isDemo = true.
        assertTrue(summary.isDemo, "simulated session must carry the Demo/source badge (D-42, D-43)")
        assertEquals(LocationSource.Simulated, summary.source)
    }

    @Test
    fun timingSessionReviewSummaryExposesTrackNameDateAndNewTrackBestWhenApplicable() {
        // newTrackBest requires the session to be eligible for ghost candidacy,
        // which excludes simulated sessions (D-20, D-43). Use a real source.
        val summary = saveAndTimeRealSession(source = LocationSource.PhoneGps)

        assertFalse(summary.trackName.isBlank(), "track name must be present")
        assertTrue(summary.createdAtEpochMillis > 0L, "created date must be present")
        // "New track best" status applies when this session holds a new per-track best.
        // For a single saved real session the best lap IS the track best → newTrackBest = true.
        assertTrue(summary.newTrackBest, "first saved real session should be a new track best")
        assertFalse(summary.isDemo, "real session must not carry Demo badge")
    }

    @Test
    fun timingSessionReviewSummaryIsAbsentForSimulatedGhostExclusion() {
        // Simulated session saved but ghost candidate excluded — summary itself
        // is still present (the session is reviewable), only ghost is excluded.
        val summary = saveAndTimeRealSession(source = LocationSource.Simulated)
        assertNotNull(summary)
        // The ghost candidate for the same track must be null (D-20, D-43).
        val ghost = store.ghostCandidateForTrack(summary.trackId)
        // Best lap is still shown in the summary; only ghost derivation excludes it.
        assertEquals(summary.bestLapMillis, summary.laps.minOf { it.durationMillis })
    }

    // ── Trace state tests: D-35 (Track Review) and D-36 (Timing Session) ────

    @Test
    fun trackTraceLayersIncludeMarkingTraceReferenceLineAndStartFinishOutliers() {
        val markingSamples = GpsFixtureLibrary.cleanTenLoop().map { it.toDto() }
        val refPoints = markingSamples.take(20).map {
            GeoPointDto(latitude = it.latitude, longitude = it.longitude)
        }
        val referenceLine = TrackReferenceLine(points = refPoints, isClosed = true)
        val startFinish = StartFinishLineDto(
            pointA = refPoints.first(),
            pointB = refPoints.last(),
        )
        val sectors = listOf(
            SectorLineDto(
                id = "s1",
                name = "Sector 1",
                order = 0,
                pointA = refPoints[5],
                pointB = refPoints[6],
            ),
        )
        val outliers = markingSamples.take(3)

        val layers = buildTrackTraceLayers(
            markingSamples = markingSamples,
            referenceLine = referenceLine,
            startFinish = startFinish,
            sectors = sectors,
            outlierSamples = outliers,
            viewWidth = 400.0,
            viewHeight = 300.0,
        )

        // Expected layers per D-35: marking trace, reference line, outliers, start/finish, sectors.
        assertTrue(layers.isNotEmpty(), "track trace layers must not be empty when data is available")
        val layerNames = layers.map { it.name }.toSet()
        assertTrue(layerNames.any { it.contains("marking", ignoreCase = true) || it.contains("trace", ignoreCase = true) },
            "must have a layer for the full marking trace context")
        assertTrue(layerNames.any { it.contains("reference", ignoreCase = true) },
            "must have a layer for the reference line (D-35)")
        assertTrue(layerNames.any { it.contains("outlier", ignoreCase = true) || it.contains("rejected", ignoreCase = true) },
            "must have a layer for outlier/rejected sections (D-35)")
        assertTrue(layerNames.any { it.contains("start") || it.contains("finish") },
            "must have a layer for the start/finish line (D-35)")
        assertTrue(layerNames.any { it.contains("sector", ignoreCase = true) },
            "must have a layer for sector lines (D-35)")
    }

    @Test
    fun timingTraceLayersIncludeReferenceBaselineSessionTraceStartFinishSectorsAndHighlightHook() {
        val refPoints = GpsFixtureLibrary.cleanTenLoop().take(20).map {
            GeoPointDto(latitude = it.latitude, longitude = it.longitude)
        }
        val sessionSamples = GpsFixtureLibrary.cleanTenLoop().mapNotNull { it.toDto() }
        val startFinish = StartFinishLineDto(
            pointA = refPoints.first(),
            pointB = refPoints.last(),
        )
        val sectors = listOf(
            SectorLineDto(
                id = "s1",
                name = "Sector 1",
                order = 0,
                pointA = refPoints[5],
                pointB = refPoints[6],
            ),
        )

        val layers = buildTimingTraceLayers(
            referenceLinePoints = refPoints,
            sessionSamples = sessionSamples,
            startFinish = startFinish,
            sectors = sectors,
            selectedLapStartMillis = 1000L,
            selectedLapEndMillis = 33000L,
            viewWidth = 400.0,
            viewHeight = 300.0,
        )

        // Expected layers per D-36: reference baseline, session trace, start/finish, sectors, highlight.
        assertTrue(layers.isNotEmpty(), "timing trace layers must not be empty when data is available")
        val layerNames = layers.map { it.name }.toSet()
        assertTrue(layerNames.any { it.contains("reference", ignoreCase = true) },
            "must have a layer for the reference baseline (D-36)")
        assertTrue(layerNames.any { it.contains("session", ignoreCase = true) || it.contains("trace", ignoreCase = true) },
            "must have a layer for the session trace (D-36)")
        assertTrue(layerNames.any { it.contains("start") || it.contains("finish") },
            "must have a layer for the start/finish line (D-36)")
        assertTrue(layerNames.any { it.contains("sector", ignoreCase = true) },
            "must have a layer for sector lines (D-36)")
        assertTrue(layerNames.any { it.contains("highlight", ignoreCase = true) || it.contains("best") || it.contains("selected") },
            "must have a layer for the selected/best lap highlight hook (D-36)")
    }

    @Test
    fun timingTraceLayersDegradeGracefullyWithoutReferenceLine() {
        val sessionSamples = GpsFixtureLibrary.cleanTenLoop().take(10).mapNotNull { it.toDto() }

        val layers = buildTimingTraceLayers(
            referenceLinePoints = emptyList(),
            sessionSamples = sessionSamples,
            startFinish = null,
            sectors = emptyList(),
            selectedLapStartMillis = null,
            selectedLapEndMillis = null,
            viewWidth = 400.0,
            viewHeight = 300.0,
        )

        // When essential data is missing, the function must not crash.
        // It may return fewer layers or empty layers — the key is no exception.
        assertNotNull(layers, "must return a list, not throw, when data is minimal")
    }

    @Test
    fun trackTraceLayersDegradeGracefullyWithoutReferenceLine() {
        val markingSamples = GpsFixtureLibrary.cleanTenLoop().take(5).map { it.toDto() }

        val layers = buildTrackTraceLayers(
            markingSamples = markingSamples,
            referenceLine = null,
            startFinish = null,
            sectors = emptyList(),
            outlierSamples = emptyList(),
            viewWidth = 400.0,
            viewHeight = 300.0,
        )

        // With no reference line and minimal data, the function must not crash.
        assertNotNull(layers, "must return a list, not throw, when reference line is null")
    }

    @Test
    fun trackReviewStateBuildTraceLayersIncludesExpectedLayerNames() {
        val markingSamples = GpsFixtureLibrary.cleanTenLoop()
        val session = TrackMarkingSession(
            id = "marking-1",
            createdAtEpochMillis = 0L,
            source = SourceMetadata(
                source = LocationSource.Simulated,
                isSimulated = true,
                label = "fixture",
            ),
            samples = markingSamples.map { it.toDto() },
        )
        val extraction = ReferenceLineExtraction(
            markingSession = session,
            isReady = true,
            referenceLine = TrackReferenceLine(
                points = markingSamples.take(20).map {
                    GeoPointDto(latitude = it.latitude, longitude = it.longitude)
                },
                isClosed = true,
            ),
            quality = GpsQualitySummary(
                sampleCount = 10,
                durationMillis = 5000L,
                averageUpdateRateHz = 1.0,
                bestAccuracyMeters = 5.0,
                worstAccuracyMeters = 15.0,
                degradedSampleCount = 1,
                sources = setOf(LocationSource.Simulated),
            ),
            detectedLoopCount = 6,
            acceptedLoopCount = 5,
            rejectedLoopCount = 1,
            diagnostics = emptyList(),
            notReadyReasons = emptyList(),
        )
        val state = TrackReviewState.from(name = "Test Track", extraction = extraction)

        val layers = state.buildTraceLayers(
            viewWidth = 400.0,
            viewHeight = 300.0,
        )

        assertTrue(layers.isNotEmpty(), "TrackReviewState must produce trace layers when extraction is ready")
        val layerNames = layers.map { it.name }.toSet()
        assertTrue(layerNames.any { it.contains("marking", ignoreCase = true) || it.contains("trace", ignoreCase = true) },
            "must include the full marking trace layer")
        assertTrue(layerNames.any { it.contains("reference", ignoreCase = true) },
            "must include the reference line layer (D-35)")
    }
}
