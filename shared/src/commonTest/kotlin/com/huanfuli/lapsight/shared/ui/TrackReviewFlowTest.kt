package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 0 (Plan 03-05 Task 2) coverage for the Track Review save flow and the
 * Review list derivation from [com.huanfuli.lapsight.shared.storage.LocalSessionStore.readIndex].
 *
 * Asserts: Save writes Track + marking into the store with Demo/source metadata;
 * Discard keeps the marking out of Review history; Re-record returns to capture;
 * the Review list state is empty with no saves and populated with visible DEMO
 * provenance after a save (D-27, D-28, D-42).
 */
class TrackReviewFlowTest {

    private val store = InMemorySessionStore()
    private val app = AppMetadata(appVersion = "0.3.0", platform = "test")

    private fun controller(): DriveMarkingController =
        DriveMarkingController(
            provider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP),
            store = store,
            appMetadata = app,
            now = { 1_700_000_000_000L },
        )

    private fun captureReady(controller: DriveMarkingController) {
        controller.beginMarking()
        repeat(480) { controller.tick() }
        controller.stopMarking()
    }

    @Test
    fun saveTrackPopulatesReviewIndexWithDemoProvenance() {
        val controller = controller()
        captureReady(controller)
        controller.confirmStartFinish()
        val track = controller.saveTrack()
        assertTrue(track != null)

        val index = store.readIndex()
        // One Track row and one TrackMarking row (D-28).
        assertTrue(index.rows.any { it.type == ReviewEntryType.Track })
        assertTrue(index.rows.any { it.type == ReviewEntryType.TrackMarking })

        val rows = ReviewListState.from(index)
        assertTrue(rows.isNotEmpty())
        // Every saved row carries visible Demo/Simulated provenance (D-42, T-03-10).
        assertTrue(rows.all { it.isDemo })
        assertTrue(rows.all { it.sourceLabel.isNotBlank() })
    }

    @Test
    fun discardKeepsMarkingOutOfReviewHistory() {
        val controller = controller()
        captureReady(controller)
        controller.discard()

        // No save happened: Review history stays empty (D-16, T-03-25).
        val rows = ReviewListState.from(store.readIndex())
        assertTrue(rows.isEmpty())
        assertEquals(DriveMarkingPhase.Idle, controller.snapshot().phase)
    }

    @Test
    fun reRecordReturnsToCapturingAndClearsReview() {
        val controller = controller()
        captureReady(controller)
        controller.reRecord()
        val snap = controller.snapshot()
        assertEquals(DriveMarkingPhase.Capturing, snap.phase)
        // The prior review is replaced; nothing saved yet.
        assertTrue(snap.reviewState == null)
        assertTrue(ReviewListState.from(store.readIndex()).isEmpty())
    }

    @Test
    fun reviewListIsEmptyWhenNoTracksSaved() {
        val rows = ReviewListState.from(store.readIndex())
        assertTrue(rows.isEmpty(), "empty state applies when no tracks are saved")
    }
}
