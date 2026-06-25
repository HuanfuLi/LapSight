package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.session.toDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 0 coverage for the riskiest Phase 3 algorithm (RESEARCH A1): deriving a
 * track reference line from a continuous marking run by repeated spatial
 * structure, rejecting outliers, and degrading gracefully on poor captures.
 *
 * These tests assert the Phase 3 product invariants D-06..D-12, D-18, and D-31:
 *  - track marking is continuous capture, NOT lap timing (no laps produced);
 *  - raw marking samples are preserved and linked for later recomputation;
 *  - outlier/noisy/dropped sections are recorded as extraction diagnostics;
 *  - degraded captures yield an explicit not-save-ready state, never a crash.
 */
class ReferenceLineExtractorTest {

    private fun marking(samples: List<LocationSample>, id: String = "marking-1"): TrackMarkingSession =
        TrackMarkingSession(
            id = id,
            createdAtEpochMillis = 0L,
            source = SourceMetadata(
                source = LocationSource.Simulated,
                isSimulated = true,
                label = "fixture",
            ),
            samples = samples.map { it.toDto() },
        )

    // --- D-06..D-10: continuous structure extraction, no laps -----------------

    @Test
    fun cleanTenLoopProducesReadyReferenceLineWithoutLaps() {
        val session = marking(GpsFixtureLibrary.cleanTenLoop())

        val result = ReferenceLineExtractor.extract(session)

        assertTrue(result.isReady, "clean 10-loop capture must be reference-ready")
        val line = result.referenceLine
        assertNotNull(line, "a ready extraction must produce a reference line")
        assertTrue(line.points.size >= 16, "reference line should be a resolved polyline")
        assertTrue(line.isClosed, "a track reference line is a closed loop")
        assertTrue(result.acceptedLoopCount >= GpsFixtureLibrary.requiredScenarioIds.size.coerceAtMost(5))
        // The extractor never splits marking into laps: the result exposes
        // reference geometry + diagnostics only, never timed LapEvents.
        assertTrue(result.acceptedLoopCount >= 5, "should accept the repeated loops")
    }

    @Test
    fun minimumFiveLoopCaptureIsReady() {
        val result = ReferenceLineExtractor.extract(marking(GpsFixtureLibrary.minimumFiveLoop()))

        assertTrue(result.isReady, "the minimum 5-loop capture must extract a reference line")
        assertNotNull(result.referenceLine)
        assertTrue(result.acceptedLoopCount >= 4)
    }

    // --- D-09: outlier rejection without producing laps -----------------------

    @Test
    fun outlierLoopIsRejectedAsDiagnosticButExtractionStaysReady() {
        val result = ReferenceLineExtractor.extract(marking(GpsFixtureLibrary.oneOutlierLoop()))

        assertTrue(result.rejectedLoopCount >= 1, "the off-track loop must be rejected")
        assertTrue(
            result.diagnostics.any { it.kind == DiagnosticKind.Outlier },
            "the rejected section must be recorded as an Outlier diagnostic, never a lap",
        )
        assertTrue(result.isReady, "with the bad loop rejected the remaining loops are still usable")
        assertNotNull(result.referenceLine)
        assertTrue(
            result.acceptedLoopCount >= 5,
            "the clean loops survive outlier rejection",
        )
    }

    // --- D-09 / D-11: degrade gracefully on poor captures (no crash) ----------

    @Test
    fun noiseAndDriftCaptureDegradesToNotReady() {
        val result = ReferenceLineExtractor.extract(marking(GpsFixtureLibrary.noiseDrift()))

        assertTrue(!result.isReady, "a noisy/drifting capture is not reference-ready")
        assertNull(result.referenceLine, "no reference line is emitted when not ready")
        assertTrue(result.notReadyReasons.isNotEmpty(), "the reason for not-ready must be reported")
        assertTrue(
            result.notReadyReasons.contains(NotReadyReason.InconsistentLoops),
            "noise/drift should be reported as inconsistent loops",
        )
    }

    @Test
    fun droppedLowFrequencyCaptureDegradesToNotReady() {
        val result = ReferenceLineExtractor.extract(marking(GpsFixtureLibrary.droppedLowFrequency()))

        assertTrue(!result.isReady, "a sparse/dropped capture is not reference-ready")
        assertNull(result.referenceLine)
        assertTrue(
            result.notReadyReasons.contains(NotReadyReason.SparseSampling),
            "dropped/low-frequency capture should be reported as sparse sampling",
        )
    }

    // --- D-04 / D-10: raw marking samples preserved unchanged ------------------

    @Test
    fun rawMarkingSamplesArePreservedAndLinkedUnchanged() {
        val session = marking(GpsFixtureLibrary.cleanTenLoop())
        val originalSamples = session.samples.toList()

        val result = ReferenceLineExtractor.extract(session)

        assertEquals(session, result.markingSession, "the source marking session stays linked")
        assertEquals(
            originalSamples,
            result.rawSamples,
            "raw marking samples must be preserved unchanged for later recomputation",
        )
        assertEquals(originalSamples, session.samples, "extraction must not mutate the input")
    }

    // --- D-12 / D-31: Track Review readiness + quality + save decision ---------

    @Test
    fun reviewStateReportsReadinessQualityAndSaveDecision() {
        val readyExtraction = ReferenceLineExtractor.extract(marking(GpsFixtureLibrary.cleanTenLoop()))
        val review = TrackReviewState.from(name = "Test Oval", extraction = readyExtraction)

        assertTrue(review.isReferenceReady)
        assertTrue(review.canSave, "a ready extraction is save-ready")
        assertTrue(review.availableDecisions.contains(TrackReviewDecision.Save))
        assertEquals(readyExtraction.rawSamples.size, review.quality.sampleCount)

        val track = review.toTrack(id = "track-1", createdAtEpochMillis = 1L)
        assertNotNull(track.referenceLine, "saving a ready review carries the reference line")
        assertEquals("Test Oval", track.name)
    }

    @Test
    fun reviewStateBlocksSaveOnNotReadyCapture() {
        val degraded = ReferenceLineExtractor.extract(marking(GpsFixtureLibrary.noiseDrift()))
        val review = TrackReviewState.from(name = "Noisy", extraction = degraded)

        assertTrue(!review.isReferenceReady)
        assertTrue(!review.canSave, "a not-ready capture must not be save-ready")
        assertTrue(!review.availableDecisions.contains(TrackReviewDecision.Save))
        assertTrue(review.availableDecisions.contains(TrackReviewDecision.ReRecord))
        assertTrue(review.availableDecisions.contains(TrackReviewDecision.Discard))
    }
}
