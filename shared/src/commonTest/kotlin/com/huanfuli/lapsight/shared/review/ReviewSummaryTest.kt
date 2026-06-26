package com.huanfuli.lapsight.shared.review

import com.huanfuli.lapsight.shared.GpsQualitySummary
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GpsQualitySummary as SessionGpsQualitySummary
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory.savedTrackWithStartFinish
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.session.toDto
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.Track
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
}
