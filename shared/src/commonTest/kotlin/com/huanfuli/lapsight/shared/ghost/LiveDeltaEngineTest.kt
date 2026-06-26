package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.lap.LocalProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wave 0 (RED) tests for [LiveDeltaEngine].
 *
 * Covers D-08/D-09 (realtime current progress and delta = current elapsed minus
 * reference elapsed at equivalent progress), D-10 (positive = slower, negative =
 * faster), D-11/D-17/D-18/D-19 (suppression to unavailable for no current lap,
 * no reference, too few samples, poor GPS, unmatched progress, and clearing of
 * stale deltas) and threat T-04-03 (no false precision under low confidence).
 */
class LiveDeltaEngineTest {

    private fun sample(
        elapsedMillis: Long,
        eastMeters: Double,
        northMeters: Double = 0.0,
        accuracy: Double? = 5.0,
        speed: Double? = 10.0,
        heading: Double? = 90.0,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = northMeters / LocalProjection.METERS_PER_DEGREE,
        longitude = eastMeters / LocalProjection.METERS_PER_DEGREE,
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = 200.0,
        source = LocationSource.Simulated,
    )

    /** Reference lap: constant 10 m/s east over ~40 m in 4 s. */
    private fun referenceLap(): ReferenceLap {
        val samples = listOf(
            sample(0, eastMeters = 0.0),
            sample(1_000, eastMeters = 10.0),
            sample(2_000, eastMeters = 20.0),
            sample(3_000, eastMeters = 30.0),
            sample(4_000, eastMeters = 40.0),
        )
        val curve = when (val r = ProgressCurveBuilder.build(samples)) {
            is ProgressCurveResult.Success -> r.curve
            is ProgressCurveResult.Failure -> fail("reference build failed: ${r.reason}")
        }
        return ReferenceLap(
            trackId = "track-1",
            sessionId = "session-1",
            lapNumber = 1,
            durationMillis = 4_000,
            isSimulated = true,
            rawSamples = samples,
            progressCurve = curve,
        )
    }

    private fun available(snapshot: LiveDeltaSnapshot): LiveDeltaSnapshot.Available =
        snapshot as? LiveDeltaSnapshot.Available
            ?: fail("expected Available, got $snapshot")

    private fun unavailable(snapshot: LiveDeltaSnapshot): LiveDeltaSnapshot.Unavailable =
        snapshot as? LiveDeltaSnapshot.Unavailable
            ?: fail("expected Unavailable, got $snapshot")

    @Test
    fun updateBeforeLapStartedIsUnavailableNoCurrentLap() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        val snapshot = engine.update(sample(0, eastMeters = 0.0))
        assertEquals(DeltaUnavailableReason.NoCurrentLap, unavailable(snapshot).reason)
    }

    @Test
    fun noReferenceIsUnavailable() {
        val engine = LiveDeltaEngine()
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        val snapshot = engine.update(sample(1_000, eastMeters = 10.0))
        assertEquals(DeltaUnavailableReason.NoReference, unavailable(snapshot).reason)
    }

    @Test
    fun singleCurrentSampleIsUnavailableTooFewSamples() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        val snapshot = engine.update(sample(0, eastMeters = 0.0))
        assertEquals(
            DeltaUnavailableReason.InsufficientCurrentSamples,
            unavailable(snapshot).reason,
        )
    }

    @Test
    fun slowerLapProducesPositiveDelta() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        engine.update(sample(2_000, eastMeters = 15.0))
        // Reaches 30 m of progress only at 4000 ms; reference reaches 30 m at 3000 ms.
        val snapshot = available(engine.update(sample(4_000, eastMeters = 30.0)))
        assertEquals(4_000L, snapshot.currentElapsedMillis)
        assertTrue(snapshot.referenceElapsedMillis in 2_900L..3_100L)
        assertTrue(snapshot.deltaMillis > 0, "slower lap must be positive, got ${snapshot.deltaMillis}")
        assertEquals(
            snapshot.currentElapsedMillis - snapshot.referenceElapsedMillis,
            snapshot.deltaMillis,
        )
    }

    @Test
    fun fasterLapProducesNegativeDelta() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        // Reaches 30 m of progress at 1500 ms; reference reaches 30 m at 3000 ms.
        engine.update(sample(1_000, eastMeters = 20.0))
        val snapshot = available(engine.update(sample(1_500, eastMeters = 30.0)))
        assertEquals(1_500L, snapshot.currentElapsedMillis)
        assertTrue(snapshot.deltaMillis < 0, "faster lap must be negative, got ${snapshot.deltaMillis}")
    }

    @Test
    fun poorAccuracySampleIsUnavailable() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        val snapshot = engine.update(sample(1_000, eastMeters = 10.0, accuracy = 250.0))
        assertEquals(DeltaUnavailableReason.PoorGpsQuality, unavailable(snapshot).reason)
    }

    @Test
    fun progressFarBeyondReferenceIsUnavailableUnmatched() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        // Reference is only ~40 m long; 400 m of progress cannot be matched.
        val snapshot = engine.update(sample(1_000, eastMeters = 400.0))
        assertEquals(DeltaUnavailableReason.UnmatchedProgress, unavailable(snapshot).reason)
    }

    @Test
    fun staleDeltaClearedAfterUnavailableUpdate() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        val good = available(engine.update(sample(1_000, eastMeters = 10.0)))
        assertTrue(good.deltaMillis == good.currentElapsedMillis - good.referenceElapsedMillis)

        // A poor-accuracy sample must clear the previous live value, not reuse it.
        val cleared = engine.update(sample(2_000, eastMeters = 20.0, accuracy = 250.0))
        assertTrue(cleared is LiveDeltaSnapshot.Unavailable)
        assertTrue(engine.snapshot is LiveDeltaSnapshot.Unavailable)
        assertEquals(DeltaUnavailableReason.PoorGpsQuality, unavailable(engine.snapshot).reason)
    }

    @Test
    fun startingNewLapResetsProgressAndDelta() {
        val engine = LiveDeltaEngine()
        engine.setReference(referenceLap())
        engine.startLap()
        engine.update(sample(0, eastMeters = 0.0))
        available(engine.update(sample(2_000, eastMeters = 20.0)))

        engine.startLap()
        assertTrue(engine.snapshot is LiveDeltaSnapshot.Unavailable)
        // First sample of the new lap is again "too few samples", not a stale delta.
        val first = engine.update(sample(5_000, eastMeters = 0.0))
        assertEquals(
            DeltaUnavailableReason.InsufficientCurrentSamples,
            unavailable(first).reason,
        )
    }
}
