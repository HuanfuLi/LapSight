package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Deterministic geometry gate for the one closed-loop arc-length primitive
 * (Plan 05-05 Task 1; SC-02 / D-08..D-10; threat T-05-10).
 *
 * Fixtures are built directly in local meters: with the path origin at latitude 0
 * the equirectangular [LocalProjection] maps 1 meter of x/longitude and y/latitude
 * identically, so corner coordinates and perimeters are exact and replay-stable.
 */
class ClosedReferencePathTest {

    private val mPerDeg = LocalProjection.METERS_PER_DEGREE

    /** A geo point whose local projection (origin latitude 0) equals (xMeters, yMeters). */
    private fun geo(xMeters: Double, yMeters: Double) = GeoPointDto(
        latitude = yMeters / mPerDeg,
        longitude = xMeters / mPerDeg,
    )

    /** Closed rectangle oval; perimeter = 2*(width+height). First vertex at the origin (lat 0). */
    private fun rectangle(width: Double, height: Double, extraPoints: List<GeoPointDto> = emptyList()) =
        TrackReferenceLine(
            points = listOf(
                geo(0.0, 0.0),
                geo(width, 0.0),
                geo(width, height),
                geo(0.0, height),
            ) + extraPoints,
            isClosed = true,
        )

    private fun loaded(line: TrackReferenceLine): ClosedReferencePath {
        val result = ClosedReferencePath.fromReferenceLine(line)
        assertIs<ClosedReferencePathResult.Loaded>(result, "expected a loaded path for $line")
        return result.path
    }

    // --- Perimeter & closing segment -----------------------------------------

    @Test
    fun perimeterIncludesTheClosingSegment() {
        // 100x60 rectangle: open chain is 100+60+100=260; only the closing
        // last->first (60) segment makes the perimeter 320.
        val path = loaded(rectangle(100.0, 60.0))
        assertEquals(320.0, path.perimeter, 1e-6)
        assertEquals(4, path.segmentCount, "four segments including the closing one")
    }

    @Test
    fun zeroLengthSegmentsAreIgnored() {
        // A duplicated vertex must not add a segment nor change the perimeter.
        val path = loaded(rectangle(100.0, 60.0, extraPoints = listOf(geo(0.0, 60.0))))
        assertEquals(320.0, path.perimeter, 1e-6)
        assertEquals(4, path.segmentCount, "the duplicate closing vertex is dropped")
    }

    // --- pointAt wrapping -----------------------------------------------------

    @Test
    fun pointAtWrapsModuloPerimeterAndWalksTheClosingSegment() {
        val path = loaded(rectangle(100.0, 60.0))

        assertPoint(LocalPoint(0.0, 0.0), path.pointAt(0.0))
        assertPoint(LocalPoint(100.0, 0.0), path.pointAt(100.0))
        assertPoint(LocalPoint(100.0, 60.0), path.pointAt(160.0))
        assertPoint(LocalPoint(0.0, 60.0), path.pointAt(260.0))

        // Wrap forward: 320 + 50 == 50 (50 m along the bottom edge).
        assertPoint(path.pointAt(50.0), path.pointAt(370.0))
        // Wrap backward onto the closing segment: -10 == 310 -> (0, 10).
        assertPoint(LocalPoint(0.0, 10.0), path.pointAt(-10.0))
    }

    // --- projection: progress, lateral, segment, ambiguity --------------------

    @Test
    fun projectCarriesProgressLateralAndSegmentForAnUnambiguousPoint() {
        val path = loaded(rectangle(100.0, 60.0))

        // 5 m above the middle of the bottom edge.
        val p = path.projectLocal(LocalPoint(50.0, 5.0))
        assertEquals(50.0, p.progressMeters, 1e-6)
        assertEquals(5.0, p.lateralMeters, 1e-6)
        assertEquals(0, p.segmentIndex, "bottom edge is segment 0")
        assertFalse(p.isAmbiguous, "the opposite edge is 55 m away — not ambiguous")
    }

    @Test
    fun projectGeoMatchesProjectLocal() {
        val path = loaded(rectangle(100.0, 60.0))
        val viaGeo = path.projectGeo(geo(50.0, 5.0))
        assertEquals(50.0, viaGeo.progressMeters, 1e-6)
        assertEquals(5.0, viaGeo.lateralMeters, 1e-6)
    }

    @Test
    fun projectFlagsAmbiguityBetweenParallelStraights() {
        // A thin 100x4 rectangle: the bottom (segment 0) and top (segment 2) edges
        // are parallel, non-adjacent, and only 4 m apart — a hairpin-like ambiguity.
        val path = loaded(rectangle(100.0, 4.0))
        val p = path.projectLocal(LocalPoint(50.0, 2.0))

        assertTrue(p.isAmbiguous, "equidistant parallel straights must be ambiguous")
        assertTrue(p.runnerUpGapMeters < path.thresholds.ambiguityMarginMeters)
    }

    // --- tangent --------------------------------------------------------------

    @Test
    fun tangentAlongAStraightEdgePointsAlongIt() {
        val path = loaded(rectangle(100.0, 60.0))
        val t = path.tangentAt(50.0) // middle of the bottom edge -> +x
        assertEquals(1.0, t.x, 1e-6)
        assertEquals(0.0, t.y, 1e-6)
    }

    @Test
    fun tangentIsAlwaysFiniteUnitLength() {
        val path = loaded(rectangle(100.0, 60.0))
        var s = 0.0
        while (s < path.perimeter) {
            val t = path.tangentAt(s)
            val len = hypot(t.x, t.y)
            assertEquals(1.0, len, 1e-6, "tangent must be unit length at s=$s")
            s += 7.0
        }
    }

    @Test
    fun normalIsPerpendicularToTangent() {
        val path = loaded(rectangle(100.0, 60.0))
        val t = path.tangentAt(50.0)
        val n = path.normalAt(50.0)
        val dot = t.x * n.x + t.y * n.y
        assertEquals(0.0, dot, 1e-6, "normal must be perpendicular to the tangent")
        assertEquals(1.0, hypot(n.x, n.y), 1e-6)
    }

    // --- typed rejection (never throws) — T-05-10 -----------------------------

    @Test
    fun emptyPathIsRejected() {
        val result = ClosedReferencePath.fromReferenceLine(TrackReferenceLine(points = emptyList()))
        assertIs<ClosedReferencePathResult.Rejected>(result)
        assertEquals(ClosedPathRejection.Empty, result.reason)
    }

    @Test
    fun nonFinitePathIsRejected() {
        val line = TrackReferenceLine(
            points = listOf(
                geo(0.0, 0.0),
                GeoPointDto(latitude = Double.NaN, longitude = 0.0),
                geo(100.0, 60.0),
            ),
        )
        val result = ClosedReferencePath.fromReferenceLine(line)
        assertIs<ClosedReferencePathResult.Rejected>(result)
        assertEquals(ClosedPathRejection.NonFinite, result.reason)
    }

    @Test
    fun degeneratePathIsRejected() {
        val line = TrackReferenceLine(
            points = listOf(geo(0.0, 0.0), geo(0.0, 0.0), geo(0.0, 0.0)),
        )
        val result = ClosedReferencePath.fromReferenceLine(line)
        assertIs<ClosedReferencePathResult.Rejected>(result)
        assertEquals(ClosedPathRejection.Degenerate, result.reason)
    }

    @Test
    fun oversizedPathIsRejected() {
        val thresholds = CourseGeometryThresholds(maxPathPoints = 8)
        val many = (0 until 64).map { geo(it.toDouble(), 0.0) }
        val result = ClosedReferencePath.fromReferenceLine(
            TrackReferenceLine(points = many),
            thresholds,
        )
        assertIs<ClosedReferencePathResult.Rejected>(result)
        assertEquals(ClosedPathRejection.Oversized, result.reason)
    }

    // --- determinism ----------------------------------------------------------

    @Test
    fun projectionIsDeterministicAcrossRepeatedRuns() {
        val path = loaded(rectangle(100.0, 60.0))
        val first = path.projectLocal(LocalPoint(73.0, 11.0))
        repeat(5) {
            val again = path.projectLocal(LocalPoint(73.0, 11.0))
            assertEquals(first.progressMeters, again.progressMeters, 0.0)
            assertEquals(first.lateralMeters, again.lateralMeters, 0.0)
            assertEquals(first.segmentIndex, again.segmentIndex)
            assertEquals(first.isAmbiguous, again.isAmbiguous)
        }
    }

    private fun assertPoint(expected: LocalPoint, actual: LocalPoint, tol: Double = 1e-6) {
        assertEquals(expected.x, actual.x, tol, "x mismatch")
        assertEquals(expected.y, actual.y, tol, "y mismatch")
    }
}
