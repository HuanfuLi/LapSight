package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.LocationSource
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
        assertTrue(controller.tick().isEmpty())
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

    @Test
    fun tickDrainsEveryBufferedSampleInASingleCall() {
        // A receiver that buffered a burst of fixes between poll ticks must have
        // ALL of them consumed by one tick, never one-per-tick (which would drop or
        // delay the backlog at the poll rate).
        val provider = BurstQueueProvider()
        val controller = RawRecordingController(provider = provider, now = { 1_700_000_000_000L })
        controller.start()
        provider.enqueue(sampleAt(0), sampleAt(100), sampleAt(200))

        val drained = controller.tick()

        assertEquals(3, drained.size)
        assertEquals(listOf(0L, 100L, 200L), drained.map { it.elapsedMillis })
        assertEquals(3, controller.snapshot().sampleCount)
    }

    private fun sampleAt(elapsedMillis: Long): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = 39.81 + elapsedMillis / 1_000_000.0,
        longitude = -86.10,
        horizontalAccuracyMeters = 4.0,
        speedMetersPerSecond = 20.0,
        headingDegrees = 90.0,
        altitudeMeters = 220.0,
        source = LocationSource.PhoneGps,
    )

    /** Finite-queue provider that buffers a burst and drains it whole per tick. */
    private class BurstQueueProvider : LocationSampleProvider {
        private val queue = ArrayDeque<LocationSample>()
        private var running = false
        override val isRunning: Boolean get() = running
        override fun start() { running = true }
        override fun stop() { running = false }
        override fun reset() { running = false; queue.clear() }
        fun enqueue(vararg samples: LocationSample) { queue.addAll(samples) }
        override fun nextSample(): LocationSample? = queue.removeFirstOrNull()
        override fun drainPending(): List<LocationSample> {
            val drained = queue.toList()
            queue.clear()
            return drained
        }
    }
}
