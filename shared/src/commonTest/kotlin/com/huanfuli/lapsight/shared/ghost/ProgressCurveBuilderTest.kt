package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.lap.LocalProjection
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Wave 0 (RED) tests for [ProgressCurveBuilder].
 *
 * Covers D-05/D-06 (progress curve shape: progress distance, normalized
 * progress, elapsed time, raw lat/lon, local point) and the integrity
 * mitigations T-04-01/T-04-02 (finite coordinates, monotonic timestamps,
 * minimum points, positive total distance, safe interpolation).
 *
 * Geometry is built in a tiny local meter frame around lat/lon = 0 so the
 * equirectangular projection stays linear and expected meters are obvious.
 */
class ProgressCurveBuilderTest {

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

    /** A deterministic constant-speed eastward lap: 10 m/s, sampled each second. */
    private fun constantSpeedLap(): List<LocationSample> = listOf(
        sample(0, eastMeters = 0.0),
        sample(1_000, eastMeters = 10.0),
        sample(2_000, eastMeters = 20.0),
        sample(3_000, eastMeters = 30.0),
        sample(4_000, eastMeters = 40.0),
    )

    private fun curveOf(samples: List<LocationSample>): ProgressCurve {
        val result = ProgressCurveBuilder.build(samples)
        return when (result) {
            is ProgressCurveResult.Success -> result.curve
            is ProgressCurveResult.Failure -> fail("expected success, got ${result.reason}")
        }
    }

    @Test
    fun progressPointsAreTimestampAndDistanceMonotonic() {
        val curve = curveOf(constantSpeedLap())
        assertEquals(5, curve.points.size)
        for (i in 1 until curve.points.size) {
            val prev = curve.points[i - 1]
            val cur = curve.points[i]
            assertTrue(cur.elapsedMillis > prev.elapsedMillis, "timestamps must increase")
            assertTrue(cur.progressMeters > prev.progressMeters, "progress must increase")
            assertTrue(
                cur.normalizedProgress >= prev.normalizedProgress,
                "normalized progress must be non-decreasing",
            )
        }
    }

    @Test
    fun totalDistanceIsPositiveAndProgressNormalizedToUnitRange() {
        val curve = curveOf(constantSpeedLap())
        assertTrue(curve.totalDistanceMeters > 0.0)
        assertTrue(abs(curve.totalDistanceMeters - 40.0) < 0.5)
        assertTrue(abs(curve.points.first().normalizedProgress - 0.0) < 1e-6)
        assertTrue(abs(curve.points.last().normalizedProgress - 1.0) < 1e-6)
    }

    @Test
    fun elapsedAtHalfProgressInterpolatesExpectedTime() {
        val curve = curveOf(constantSpeedLap())
        // Half of ~40 m == ~20 m, which the constant-speed lap reaches at 2000 ms.
        val elapsed = curve.elapsedAtProgress(curve.totalDistanceMeters / 2.0)
        assertNotNull(elapsed)
        assertTrue(abs(elapsed - 2_000L) < 50L, "expected ~2000 ms, got $elapsed")
    }

    @Test
    fun elapsedAtProgressClampsToCurveRange() {
        val curve = curveOf(constantSpeedLap())
        assertEquals(0L, curve.elapsedAtProgress(-100.0))
        assertEquals(4_000L, curve.elapsedAtProgress(curve.totalDistanceMeters + 100.0))
    }

    @Test
    fun progressPointsPreserveRawGeographicAndLocalCoordinates() {
        val curve = curveOf(constantSpeedLap())
        val third = curve.points[2]
        // Raw lat/lon retained for telemetry/replay (D-06).
        assertTrue(abs(third.latitude - 0.0) < 1e-9)
        assertTrue(abs(third.longitude - (20.0 / LocalProjection.METERS_PER_DEGREE)) < 1e-12)
        // Local projected meters retained relative to first-sample origin.
        assertTrue(abs(third.localX - 20.0) < 0.5)
        assertTrue(abs(third.localY - 0.0) < 0.5)
    }

    @Test
    fun fewerThanTwoSamplesIsFailure() {
        val result = ProgressCurveBuilder.build(listOf(sample(0, eastMeters = 0.0)))
        assertTrue(result is ProgressCurveResult.Failure)
        assertEquals(ProgressCurveFailure.TooFewSamples, result.reason)
    }

    @Test
    fun nonFiniteCoordinatesIsFailure() {
        val bad = listOf(
            sample(0, eastMeters = 0.0),
            sample(1_000, eastMeters = 10.0).copy(latitude = Double.NaN),
        )
        val result = ProgressCurveBuilder.build(bad)
        assertTrue(result is ProgressCurveResult.Failure)
        assertEquals(ProgressCurveFailure.NonFiniteCoordinates, result.reason)
    }

    @Test
    fun nonMonotonicTimestampsIsFailure() {
        val bad = listOf(
            sample(0, eastMeters = 0.0),
            sample(2_000, eastMeters = 10.0),
            sample(1_000, eastMeters = 20.0),
        )
        val result = ProgressCurveBuilder.build(bad)
        assertTrue(result is ProgressCurveResult.Failure)
        assertEquals(ProgressCurveFailure.NonMonotonicTimestamps, result.reason)
    }

    @Test
    fun zeroTotalDistanceIsFailure() {
        val stationary = listOf(
            sample(0, eastMeters = 5.0, northMeters = 5.0),
            sample(1_000, eastMeters = 5.0, northMeters = 5.0),
            sample(2_000, eastMeters = 5.0, northMeters = 5.0),
        )
        val result = ProgressCurveBuilder.build(stationary)
        assertTrue(result is ProgressCurveResult.Failure)
        assertEquals(ProgressCurveFailure.ZeroDistance, result.reason)
    }
}
