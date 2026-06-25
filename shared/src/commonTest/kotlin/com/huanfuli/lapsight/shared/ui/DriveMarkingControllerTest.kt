package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.ReferenceLineExtractor
import com.huanfuli.lapsight.shared.track.TrackReviewDecision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 0 (Plan 03-05 Task 1) coverage for [DriveMarkingController].
 *
 * Exercises the marking-capture state machine in plain Kotlin (no Compose):
 * the controller collects samples from a [SimulatedGpsProvider], stops into a
 * [com.huanfuli.lapsight.shared.track.TrackReviewState] produced by
 * [ReferenceLineExtractor], and keeps Start Timing blocked until a saved Track
 * has a confirmed start/finish line (D-19).
 */
class DriveMarkingControllerTest {

    private val store = InMemorySessionStore()
    private val app = AppMetadata(appVersion = "0.3.0", platform = "test")

    private fun controller(scenarioId: String = GpsFixtureLibrary.CLEAN_10_LOOP): DriveMarkingController =
        DriveMarkingController(
            provider = SimulatedGpsProvider(scenarioId),
            store = store,
            appMetadata = app,
            now = { 1_700_000_000_000L },
        )

    private fun DriveMarkingController.captureSamples(count: Int) {
        repeat(count) { tick() }
    }

    @Test
    fun beginMarkingTransitionsToCapturingAndDoesNotRequireStartAtFinish() {
        val controller = controller()
        // The clean fixture starts mid-oval; marking must not require starting at
        // the (yet-unknown) start/finish line (D-07).
        controller.beginMarking()
        assertEquals(DriveMarkingPhase.Capturing, controller.snapshot().phase)
        controller.captureSamples(480) // ~10 loops of the clean fixture
        controller.stopMarking()
        val snap = controller.snapshot()
        assertEquals(DriveMarkingPhase.Review, snap.phase)
        val review = snap.reviewState
        assertNotNull(review)
        // Extraction produced a reference-ready capture via ReferenceLineExtractor.
        assertTrue(review.isReferenceReady, "clean capture should be reference-ready")
        assertNotNull(review.extraction.referenceLine)
        // Marking never carries lap times (D-08): the extraction has no laps.
        // (ReferenceLineExtraction has no lap field by construction; asserted via type.)
        assertEquals(LocationSource.Simulated, review.extraction.markingSession.source.source)
    }

    @Test
    fun startTimingIsBlockedUntilASavedTrackHasConfirmedStartFinish() {
        val controller = controller()
        // No saved track yet: Start Timing is blocked with the exact UI-SPEC copy.
        var snap = controller.snapshot()
        assertFalse(snap.canStartTiming)
        assertEquals(
            "Mark a track first. Timing needs a saved start/finish line.",
            snap.startTimingBlockedReason,
        )

        // Capture + stop into review (ready), but do NOT confirm start/finish yet.
        controller.beginMarking()
        controller.captureSamples(480)
        controller.stopMarking()
        snap = controller.snapshot()
        assertTrue(snap.reviewState?.canSave == true)
        // Even a ready review is not yet a saved track with confirmed start/finish.
        assertFalse(snap.canStartTiming)

        // Confirm start/finish from the reference line, then save.
        controller.confirmStartFinish()
        controller.saveTrack()
        snap = controller.snapshot()
        // Now a saved Track with a confirmed start/finish exists: timing unblocks.
        assertTrue(snap.canStartTiming, "timing should unblock after saved track with start/finish")
        assertNull(snap.reviewState, "review cleared after save")
        assertEquals(DriveMarkingPhase.Idle, snap.phase)
        assertEquals(1, snap.savedTrackCount)
    }

    @Test
    fun stopMarkingOnNoisyCaptureDegradesToNotSaveReadyReview() {
        val controller = controller(scenarioId = GpsFixtureLibrary.NOISE_DRIFT)
        controller.beginMarking()
        controller.captureSamples(8 * 48) // noise-drift fixture
        controller.stopMarking()
        val review = controller.snapshot().reviewState
        assertNotNull(review)
        assertFalse(review.canSave, "noisy capture must not be save-ready (D-31)")
        assertTrue(review.notReadyReasons.isNotEmpty())
        // Save is refused; only Re-record/Discard offered.
        assertFalse(TrackReviewDecision.Save in review.availableDecisions)
        assertTrue(TrackReviewDecision.ReRecord in review.availableDecisions)
        assertTrue(TrackReviewDecision.Discard in review.availableDecisions)
    }
}
