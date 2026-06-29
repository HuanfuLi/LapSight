package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Coverage for the diagnostic raw-recording seam [RawRecordingController]
 * (D-16, D-17).
 *
 * Proves the seam captures samples + GPS quality WITHOUT any lap/ghost timing
 * state and that its output is structurally excluded from valid-session counting
 * (explicit raw marker, zero laps).
 */
class RawRecordingControllerTest {

    private fun controller(): RawRecordingController = RawRecordingController(
        provider = SimulatedGpsProvider(GpsFixtureLibrary.CLEAN_10_LOOP),
        now = { 1_700_000_000_000L },
    )

    private fun RawRecordingController.record(count: Int) {
        start()
        repeat(count) { tick() }
    }

    @Test
    fun feedingSamplesAccumulatesIntoTheTrace() {
        val controller = controller()
        controller.record(50)
        val snap = controller.snapshot()
        assertEquals(RawRecordingPhase.Recording, snap.phase)
        assertTrue(snap.isActive)
        assertEquals(50, snap.sampleCount)
        assertNotNull(snap.latestSample)
    }

    @Test
    fun snapshotExposesGpsQualityRollup() {
        val controller = controller()
        controller.record(60)
        val quality = controller.snapshot().gpsQuality
        assertNotNull(quality, "raw recording must expose a GPS quality rollup")
        assertEquals(60, quality.sampleCount)
        // A real rollup over a moving fixture yields a positive update rate.
        assertTrue(quality.averageUpdateRateHz > 0.0)
    }

    @Test
    fun tickIsANoOpBeforeStart() {
        val controller = controller()
        assertEquals(null, controller.tick())
        assertEquals(0, controller.snapshot().sampleCount)
    }

    @Test
    fun stopProducesADiagnosticRawPayloadWithNoLapState() {
        val controller = controller()
        controller.record(80)
        val payload = controller.stop()

        // Explicit raw/diagnostic marker + zero laps => can never count as a valid
        // timing session (D-17).
        assertTrue(payload.isDiagnosticRaw)
        assertEquals(0, payload.lapCount)
        // Samples are carried as canonical DTOs (LocationSample.toDto reuse).
        assertEquals(80, payload.samples.size)
        assertEquals(80, payload.gpsQuality.sampleCount)
        assertEquals("raw-1700000000000", payload.id)

        // Stopping transitions out of the active recording phase.
        val snap = controller.snapshot()
        assertEquals(RawRecordingPhase.Stopped, snap.phase)
        assertFalse(snap.isActive)
    }
}
