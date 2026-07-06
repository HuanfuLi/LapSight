package com.huanfuli.lapsight.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Deterministic checks over [VelocityAidedGpsFilter]: same input stream, same
 * output, with measurable jitter reduction over a known true path.
 */
class VelocityAidedGpsFilterTest {

    private val baseLatitude = 42.33
    private val baseLongitude = -83.04
    private val metersPerDegreeLatitude = 111_320.0
    private val metersPerDegreeLongitude = 111_320.0 * cos(baseLatitude * PI / 180.0)

    /** A fix positioned [east]/[north] meters from the base point. */
    private fun sample(
        elapsedMillis: Long,
        east: Double,
        north: Double,
        accuracy: Double? = 10.0,
        speed: Double? = null,
        heading: Double? = null,
    ) = LocationSample(
        elapsedMillis = elapsedMillis,
        latitude = baseLatitude + north / metersPerDegreeLatitude,
        longitude = baseLongitude + east / metersPerDegreeLongitude,
        horizontalAccuracyMeters = accuracy,
        speedMetersPerSecond = speed,
        headingDegrees = heading,
        altitudeMeters = null,
        source = LocationSource.PhoneGps,
    )

    private fun errorMeters(filtered: LocationSample, trueEast: Double, trueNorth: Double): Double {
        val east = (filtered.longitude - baseLongitude) * metersPerDegreeLongitude
        val north = (filtered.latitude - baseLatitude) * metersPerDegreeLatitude
        return hypot(east - trueEast, north - trueNorth)
    }

    @Test
    fun firstSamplePassesThroughUnchanged() {
        val filter = VelocityAidedGpsFilter()
        val fix = sample(1_000L, east = 5.0, north = -3.0, speed = 12.0, heading = 45.0)
        assertEquals(fix, filter.update(fix))
    }

    @Test
    fun nonFinitePositionPassesThroughWithoutCorruptingState() {
        val filter = VelocityAidedGpsFilter()
        filter.update(sample(1_000L, 0.0, 0.0, speed = 20.0, heading = 90.0))
        val bad = sample(2_000L, 0.0, 0.0).copy(latitude = Double.NaN)
        assertEquals(bad, filter.update(bad))
        // The stream keeps filtering afterwards.
        val next = filter.update(sample(2_000L, 20.0, 0.0, speed = 20.0, heading = 90.0))
        assertTrue(next.latitude.isFinite() && next.longitude.isFinite())
    }

    @Test
    fun reducesJitterOnAStraightDriveWithDopplerVelocity() {
        val filter = VelocityAidedGpsFilter()
        val random = Random(42)
        val speed = 20.0 // m/s heading due east
        var rawSquaredError = 0.0
        var filteredSquaredError = 0.0
        var scored = 0
        for (i in 0 until 60) {
            val elapsed = i * 1_000L
            val trueEast = speed * i
            val noisyEast = trueEast + random.nextDouble(-8.0, 8.0)
            val noisyNorth = random.nextDouble(-8.0, 8.0)
            val fix = sample(elapsed, noisyEast, noisyNorth, accuracy = 10.0, speed = speed, heading = 90.0)
            val filtered = filter.update(fix)
            if (i >= 5) { // allow convergence
                rawSquaredError += hypot(noisyEast - trueEast, noisyNorth) .let { it * it }
                filteredSquaredError += errorMeters(filtered, trueEast, 0.0).let { it * it }
                scored += 1
            }
        }
        val rawRms = sqrt(rawSquaredError / scored)
        val filteredRms = sqrt(filteredSquaredError / scored)
        assertTrue(
            filteredRms < rawRms * 0.7,
            "expected filtered RMS ($filteredRms m) well under raw RMS ($rawRms m)",
        )
    }

    @Test
    fun reducesJitterWhileParked() {
        val filter = VelocityAidedGpsFilter()
        val random = Random(7)
        var rawSquaredError = 0.0
        var filteredSquaredError = 0.0
        var scored = 0
        for (i in 0 until 40) {
            val noisyEast = random.nextDouble(-6.0, 6.0)
            val noisyNorth = random.nextDouble(-6.0, 6.0)
            val fix = sample(i * 1_000L, noisyEast, noisyNorth, accuracy = 8.0, speed = 0.0, heading = 0.0)
            val filtered = filter.update(fix)
            if (i >= 5) {
                rawSquaredError += hypot(noisyEast, noisyNorth).let { it * it }
                filteredSquaredError += errorMeters(filtered, 0.0, 0.0).let { it * it }
                scored += 1
            }
        }
        assertTrue(sqrt(filteredSquaredError / scored) < sqrt(rawSquaredError / scored) * 0.6)
    }

    @Test
    fun singleWildFixIsMostlyRejected() {
        val filter = VelocityAidedGpsFilter()
        // Converge while parked at the origin.
        for (i in 0 until 10) {
            filter.update(sample(i * 1_000L, 0.0, 0.0, accuracy = 5.0, speed = 0.0, heading = 0.0))
        }
        // One 40 m multipath spike.
        val spiked = filter.update(sample(10_000L, 40.0, 0.0, accuracy = 5.0, speed = 0.0, heading = 0.0))
        assertTrue(
            errorMeters(spiked, 0.0, 0.0) < 10.0,
            "spike leaked ${errorMeters(spiked, 0.0, 0.0)} m through the gate",
        )
        // The stream recovers on the next honest fix.
        val recovered = filter.update(sample(11_000L, 0.0, 0.0, accuracy = 5.0, speed = 0.0, heading = 0.0))
        assertTrue(errorMeters(recovered, 0.0, 0.0) < 5.0)
    }

    @Test
    fun persistentDisagreementReanchorsOnTheMeasurement() {
        val filter = VelocityAidedGpsFilter()
        for (i in 0 until 10) {
            filter.update(sample(i * 1_000L, 0.0, 0.0, accuracy = 5.0, speed = 0.0, heading = 0.0))
        }
        // The world really moved (tunnel exit): fixes stay 200 m away.
        var last: LocationSample? = null
        for (i in 0 until 4) {
            last = filter.update(
                sample(10_000L + i * 1_000L, 200.0, 0.0, accuracy = 5.0, speed = 0.0, heading = 0.0),
            )
        }
        assertTrue(errorMeters(last!!, 200.0, 0.0) < 5.0, "filter failed to concede to reality")
    }

    @Test
    fun timeDiscontinuityReanchorsInsteadOfFiltering() {
        val filter = VelocityAidedGpsFilter()
        for (i in 0 until 5) {
            filter.update(sample(i * 1_000L, i * 20.0, 0.0, speed = 20.0, heading = 90.0))
        }
        // Feed restart: elapsed clock rewinds; the fix passes through untouched.
        val restarted = sample(500L, 300.0, 300.0, speed = 0.0, heading = 0.0)
        assertEquals(restarted, filter.update(restarted))
    }

    @Test
    fun longGapReanchorsInsteadOfCoasting() {
        val filter = VelocityAidedGpsFilter()
        filter.update(sample(0L, 0.0, 0.0, speed = 20.0, heading = 90.0))
        filter.update(sample(1_000L, 20.0, 0.0, speed = 20.0, heading = 90.0))
        val afterGap = sample(30_000L, 500.0, 0.0, speed = 20.0, heading = 90.0)
        assertEquals(afterGap, filter.update(afterGap))
    }

    @Test
    fun onlyCoordinatesAreRewritten() {
        val filter = VelocityAidedGpsFilter()
        filter.update(sample(0L, 0.0, 0.0, speed = 15.0, heading = 90.0))
        val fix = sample(1_000L, 18.0, 2.0, accuracy = 7.0, speed = 15.0, heading = 88.0)
            .copy(satellitesInUse = 21, usesDualFrequency = true, altitudeMeters = 190.0)
        val filtered = filter.update(fix)
        assertEquals(fix.copy(latitude = filtered.latitude, longitude = filtered.longitude), filtered)
    }

    @Test
    fun sameStreamProducesIdenticalOutput() {
        val random = Random(11)
        val stream = (0 until 30).map { i ->
            sample(
                i * 1_000L,
                east = i * 15.0 + random.nextDouble(-6.0, 6.0),
                north = random.nextDouble(-6.0, 6.0),
                accuracy = 9.0,
                speed = 15.0,
                heading = 90.0,
            )
        }
        val first = VelocityAidedGpsFilter().let { f -> stream.map(f::update) }
        val second = VelocityAidedGpsFilter().let { f -> stream.map(f::update) }
        assertEquals(first, second)
    }

    @Test
    fun resetForgetsHistory() {
        val filter = VelocityAidedGpsFilter()
        filter.update(sample(0L, 0.0, 0.0, speed = 20.0, heading = 90.0))
        filter.update(sample(1_000L, 20.0, 0.0, speed = 20.0, heading = 90.0))
        filter.reset()
        val fix = sample(2_000L, 500.0, 500.0, speed = 0.0, heading = 0.0)
        assertEquals(fix, filter.update(fix))
    }
}
