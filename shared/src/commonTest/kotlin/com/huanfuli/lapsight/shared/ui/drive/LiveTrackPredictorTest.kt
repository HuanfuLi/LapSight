package com.huanfuli.lapsight.shared.ui.drive

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Deterministic coverage for the display-only [LiveTrackPredictor]. */
class LiveTrackPredictorTest {

    private fun sample(
        elapsedMillis: Long,
        latitude: Double,
        longitude: Double,
        speed: Double? = null,
        heading: Double? = null,
    ): LocationSample = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = latitude,
        longitude = longitude,
        horizontalAccuracyMeters = 4.0,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = null,
        source = LocationSource.PhoneGps,
    )

    @Test
    fun interpolatesHalfwayBetweenTwoFixes() {
        val previous = sample(1_000, latitude = 10.0, longitude = 20.0)
        val latest = sample(2_000, latitude = 10.0 + 0.002, longitude = 20.0 + 0.004)

        // Halfway back from latest toward previous (offset -500ms of a 1000ms seg).
        val fix = LiveTrackPredictor.predict(previous, latest, atElapsedMillis = 1_500)

        assertEquals(10.0 + 0.001, fix.latitude, 1e-9)
        assertEquals(20.0 + 0.002, fix.longitude, 1e-9)
    }

    @Test
    fun extrapolatesForwardAlongTheSameVelocity() {
        val previous = sample(1_000, latitude = 10.0, longitude = 20.0)
        val latest = sample(2_000, latitude = 10.0 + 0.002, longitude = 20.0 + 0.004)

        // 500ms past the latest fix continues the segment velocity.
        val fix = LiveTrackPredictor.predict(previous, latest, atElapsedMillis = 2_500)

        assertEquals(10.0 + 0.003, fix.latitude, 1e-9)
        assertEquals(20.0 + 0.006, fix.longitude, 1e-9)
    }

    @Test
    fun cappsExtrapolationAtTheHorizon() {
        val previous = sample(1_000, latitude = 10.0, longitude = 20.0)
        val latest = sample(2_000, latitude = 10.0 + 0.001, longitude = 20.0)

        // Ask far into the future; projection stops at MAX_EXTRAPOLATION_MILLIS.
        val fix = LiveTrackPredictor.predict(previous, latest, atElapsedMillis = 60_000)

        val cappedOffset = LiveTrackPredictor.MAX_EXTRAPOLATION_MILLIS
        val latPerMillis = 0.001 / 1_000
        assertEquals(10.0 + 0.001 + latPerMillis * cappedOffset, fix.latitude, 1e-9)
    }

    @Test
    fun passesThroughWhenNoPreviousFix() {
        val latest = sample(2_000, latitude = 10.0, longitude = 20.0, speed = 30.0, heading = 90.0)

        val fix = LiveTrackPredictor.predict(previous = null, latest = latest, atElapsedMillis = 5_000)

        assertEquals(10.0, fix.latitude, 1e-9)
        assertEquals(20.0, fix.longitude, 1e-9)
        assertEquals(30.0, fix.speedMetersPerSecond)
        assertEquals(90.0, fix.headingDegrees)
    }

    @Test
    fun passesThroughWhenTimestampsAreNotOrdered() {
        val previous = sample(2_000, latitude = 10.0, longitude = 20.0)
        val latest = sample(2_000, latitude = 11.0, longitude = 21.0)

        val fix = LiveTrackPredictor.predict(previous, latest, atElapsedMillis = 2_500)

        assertEquals(11.0, fix.latitude, 1e-9)
        assertEquals(21.0, fix.longitude, 1e-9)
    }

    @Test
    fun prefersLatestSpeedAndHeadingOverPrevious() {
        val previous = sample(1_000, latitude = 10.0, longitude = 20.0, speed = 10.0, heading = 45.0)
        val latest = sample(2_000, latitude = 10.001, longitude = 20.0, speed = 25.0, heading = 80.0)

        val fix = LiveTrackPredictor.predict(previous, latest, atElapsedMillis = 2_000)

        assertEquals(25.0, fix.speedMetersPerSecond)
        assertEquals(80.0, fix.headingDegrees)
    }

    @Test
    fun historyOverloadReturnsNullWhenEmpty() {
        assertNull(LiveTrackPredictor.predict(emptyList(), atElapsedMillis = 0))
    }
}
