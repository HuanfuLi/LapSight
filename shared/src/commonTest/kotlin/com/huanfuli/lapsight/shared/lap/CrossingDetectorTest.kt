package com.huanfuli.lapsight.shared.lap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrossingDetectorTest {

    private val origin = GeoPoint(latitude = 0.0, longitude = 0.0)
    private val projection = LocalProjection(origin)
    private val detector = CrossingDetector(projection)

    // A vertical start/finish line near the origin (runs north-south at x ~ 0).
    private val line = StartFinishLine(
        pointA = GeoPoint(latitude = -0.0001, longitude = 0.0),
        pointB = GeoPoint(latitude = 0.0001, longitude = 0.0),
    )

    private fun localOfLon(lonMeters: Double): LocalPoint =
        LocalPoint(x = lonMeters, y = 0.0)

    @Test
    fun crossingFromWestToEastIsDetected() {
        val movement = MovementSegment(
            startLocal = localOfLon(-10.0),
            endLocal = localOfLon(10.0),
            startMillis = 1_000,
            endMillis = 3_000,
            horizontalAccuracyMeters = 4.0,
            speedMetersPerSecond = 10.0,
        )
        val candidate = detector.detectStartFinish(line, movement)
        assertTrue(candidate != null)
        assertEquals(TimingLineRef.StartFinish, candidate!!.line)
        assertEquals(2_000L, candidate.crossingMillis)
        assertTrue(candidate.signedSide != 0.0)
    }

    @Test
    fun crossingFromEastToWestHasOppositeSign() {
        val west = detector.detectStartFinish(
            line,
            MovementSegment(localOfLon(-10.0), localOfLon(10.0), 0, 2_000, 4.0, 10.0),
        )
        val east = detector.detectStartFinish(
            line,
            MovementSegment(localOfLon(10.0), localOfLon(-10.0), 0, 2_000, 4.0, 10.0),
        )
        assertTrue(west != null && east != null)
        // Opposite crossing directions yield opposite signed sides.
        assertTrue(west!!.signedSide * east!!.signedSide < 0.0)
    }

    @Test
    fun noCrossingWhenSamplesStayOnOneSide() {
        val candidate = detector.detectStartFinish(
            line,
            MovementSegment(localOfLon(-20.0), localOfLon(-5.0), 0, 2_000, 4.0, 10.0),
        )
        assertNull(candidate)
    }

    @Test
    fun lowFrequencySampleWithLineBetweenPointsStillDetected() {
        // Sparse samples 50 m apart with the line in the middle.
        val candidate = detector.detectStartFinish(
            line,
            MovementSegment(localOfLon(-25.0), localOfLon(25.0), 0, 5_000, 6.0, 10.0),
        )
        assertTrue(candidate != null)
        assertEquals(2_500L, candidate!!.crossingMillis)
    }

    @Test
    fun noisySampleNearButNotCrossingIsNull() {
        val candidate = detector.detectStartFinish(
            line,
            MovementSegment(localOfLon(-2.0), localOfLon(-0.5), 0, 1_000, 6.0, 10.0),
        )
        assertNull(candidate)
    }

    @Test
    fun sectorCrossingIdentifiesSectorLine() {
        val sector = SectorLine(
            id = "S1",
            name = "Sector 1",
            order = 0,
            pointA = GeoPoint(-0.0001, 0.0),
            pointB = GeoPoint(0.0001, 0.0),
        )
        val candidate = detector.detectSector(
            sector,
            MovementSegment(localOfLon(-10.0), localOfLon(10.0), 0, 2_000, 4.0, 10.0),
        )
        assertTrue(candidate != null)
        assertEquals(TimingLineRef.Sector("S1", 0), candidate!!.line)
    }

    @Test
    fun detectorReportsSingleCandidatePerMovement() {
        val movement = MovementSegment(localOfLon(-10.0), localOfLon(10.0), 0, 2_000, 4.0, 10.0)
        // Calling twice with the same input must give the same result (no mutation).
        val first = detector.detectStartFinish(line, movement)
        val second = detector.detectStartFinish(line, movement)
        assertEquals(first, second)
    }
}
