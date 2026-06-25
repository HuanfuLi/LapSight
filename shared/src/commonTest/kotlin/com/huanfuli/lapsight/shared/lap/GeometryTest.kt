package com.huanfuli.lapsight.shared.lap

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeometryTest {

    private val origin = GeoPoint(latitude = 39.8121, longitude = -86.1062)
    private val projection = LocalProjection(origin)

    @Test
    fun projectionMapsOriginToZero() {
        val local = projection.toLocal(origin)
        assertTrue(abs(local.x) < 1e-6)
        assertTrue(abs(local.y) < 1e-6)
    }

    @Test
    fun projectionIsReversibleWithinTolerance() {
        val point = GeoPoint(origin.latitude + 0.0009, origin.longitude - 0.0007)
        val roundTrip = projection.toGeo(projection.toLocal(point))
        assertTrue(abs(roundTrip.latitude - point.latitude) < 1e-9)
        assertTrue(abs(roundTrip.longitude - point.longitude) < 1e-9)
    }

    @Test
    fun northwardDegreeMapsToExpectedMeters() {
        // 0.001 deg latitude north ~= 111.32 m.
        val north = projection.toLocal(GeoPoint(origin.latitude + 0.001, origin.longitude))
        assertTrue(abs(north.x) < 1e-6)
        assertTrue(abs(north.y - 111.32) < 0.01)
    }

    @Test
    fun clearCrossingIsDetected() {
        // Movement east across a vertical line at x = 5.
        val crossing = SegmentGeometry.intersectMovementWithLine(
            moveStart = LocalPoint(0.0, 0.0),
            moveEnd = LocalPoint(10.0, 0.0),
            lineA = LocalPoint(5.0, -5.0),
            lineB = LocalPoint(5.0, 5.0),
        )
        assertTrue(crossing != null)
        assertTrue(abs(crossing!!.ratio - 0.5) < 1e-9)
        assertTrue(abs(crossing.crossingPoint.x - 5.0) < 1e-9)
        assertTrue(abs(crossing.crossingPoint.y) < 1e-9)
    }

    @Test
    fun noCrossingWhenMovementStopsShort() {
        val crossing = SegmentGeometry.intersectMovementWithLine(
            moveStart = LocalPoint(0.0, 0.0),
            moveEnd = LocalPoint(4.0, 0.0),
            lineA = LocalPoint(5.0, -5.0),
            lineB = LocalPoint(5.0, 5.0),
        )
        assertNull(crossing)
    }

    @Test
    fun noCrossingForParallelSegments() {
        val crossing = SegmentGeometry.intersectMovementWithLine(
            moveStart = LocalPoint(0.0, 0.0),
            moveEnd = LocalPoint(10.0, 0.0),
            lineA = LocalPoint(0.0, 2.0),
            lineB = LocalPoint(10.0, 2.0),
        )
        assertNull(crossing)
    }

    @Test
    fun touchingEndpointCounts() {
        // Movement ends exactly on the line.
        val crossing = SegmentGeometry.intersectMovementWithLine(
            moveStart = LocalPoint(0.0, 0.0),
            moveEnd = LocalPoint(5.0, 0.0),
            lineA = LocalPoint(5.0, -5.0),
            lineB = LocalPoint(5.0, 5.0),
        )
        assertTrue(crossing != null)
        assertTrue(abs(crossing!!.ratio - 1.0) < 1e-9)
    }

    @Test
    fun movementStartingNearLineGivesRatioNearZero() {
        val crossing = SegmentGeometry.intersectMovementWithLine(
            moveStart = LocalPoint(5.0, 0.0),
            moveEnd = LocalPoint(15.0, 0.0),
            lineA = LocalPoint(5.0, -5.0),
            lineB = LocalPoint(5.0, 5.0),
        )
        assertTrue(crossing != null)
        assertTrue(crossing!!.ratio < 1e-6)
    }

    @Test
    fun interpolateTimestampMidpoint() {
        assertEquals(1_500L, SegmentGeometry.interpolateTimestamp(1_000, 2_000, 0.5))
        assertEquals(1_000L, SegmentGeometry.interpolateTimestamp(1_000, 2_000, 0.0))
        assertEquals(2_000L, SegmentGeometry.interpolateTimestamp(1_000, 2_000, 1.0))
    }

    @Test
    fun interpolateTimestampRejectsOutOfRange() {
        assertNull(SegmentGeometry.interpolateTimestamp(1_000, 2_000, 1.5))
        assertNull(SegmentGeometry.interpolateTimestamp(1_000, 2_000, -0.1))
    }

    @Test
    fun headingEastIsNinety() {
        val heading = SegmentGeometry.headingDegrees(LocalPoint(0.0, 0.0), LocalPoint(5.0, 0.0))
        assertTrue(heading != null)
        assertTrue(abs(heading!! - 90.0) < 1e-6)
    }

    @Test
    fun headingNorthIsZero() {
        val heading = SegmentGeometry.headingDegrees(LocalPoint(0.0, 0.0), LocalPoint(0.0, 5.0))
        assertTrue(heading != null)
        assertTrue(abs(heading!!) < 1e-6)
    }

    @Test
    fun headingZeroLengthIsNull() {
        assertNull(SegmentGeometry.headingDegrees(LocalPoint(1.0, 1.0), LocalPoint(1.0, 1.0)))
    }
}
