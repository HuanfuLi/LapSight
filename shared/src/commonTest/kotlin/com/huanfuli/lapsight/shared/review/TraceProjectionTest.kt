package com.huanfuli.lapsight.shared.review

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 0 (Plan 03-07 Task 1) coverage for [TraceProjection] (SESS-03, D-33, D-34).
 *
 * Asserts:
 *  - Projected points are finite and inside [0..1] render bounds.
 *  - Projection is deterministic (same input → same output).
 *  - Original canonical lat/lon samples are not mutated or replaced by projection.
 *  - Empty and single-point traces return typed empty/degenerate states without crash.
 */
class TraceProjectionTest {

    // ── Test helpers ─────────────────────────────────────────────────────────

    private fun geo(lat: Double, lon: Double) = GeoPointDto(lat, lon)

    /** A ~500m oval that looks like a kart track when projected. */
    private fun sampleOval(): List<GeoPointDto> {
        val baseLat = 39.8121
        val baseLon = -86.1062
        val points = mutableListOf<GeoPointDto>()
        // Generate ~20 points around a small oval loop.
        for (i in 0 until 20) {
            val angle = i * 2.0 * kotlin.math.PI / 20.0
            val lat = baseLat + kotlin.math.sin(angle) * 0.0009
            val lon = baseLon + kotlin.math.cos(angle) * 0.00045
            points += geo(lat, lon)
        }
        return points
    }

    // ── Test 1: finite normalized/render points inside bounds (SESS-03) ─────

    @Test
    fun projectedPointsAreFiniteAndInsideBounds() {
        val oval = sampleOval()
        val width = 400.0
        val height = 300.0
        val padding = 0.05

        val result = TraceProjection.project(
            layers = listOf(oval),
            width = width,
            height = height,
            padding = padding,
        )

        assertEquals(1, result.size, "one layer in → one layer out")
        val projected = result[0]
        assertEquals(oval.size, projected.size, "every input point must produce an output point")

        for ((idx, pt) in projected.withIndex()) {
            assertTrue(pt.x.isFinite(), "point[$idx].x must be finite, got ${pt.x}")
            assertTrue(pt.y.isFinite(), "point[$idx].y must be finite, got ${pt.y}")
            assertTrue(
                pt.x >= 0.0 && pt.x <= 1.0,
                "point[$idx].x (${pt.x}) must be within [0..1]",
            )
            assertTrue(
                pt.y >= 0.0 && pt.y <= 1.0,
                "point[$idx].y (${pt.y}) must be within [0..1]",
            )
        }
    }

    // ── Test 2: deterministic, canonical lat/lon preserved (D-34) ────────────

    @Test
    fun projectionIsDeterministic() {
        val oval = sampleOval()

        val a = TraceProjection.project(listOf(oval), 400.0, 300.0, 0.05)
        val b = TraceProjection.project(listOf(oval), 400.0, 300.0, 0.05)

        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals(a[i].size, b[i].size)
            for (j in a[i].indices) {
                assertEquals(a[i][j].x, b[i][j].x, 1e-9, "projection must be deterministic for x")
                assertEquals(a[i][j].y, b[i][j].y, 1e-9, "projection must be deterministic for y")
            }
        }
    }

    @Test
    fun canonicalLatLonIsPreservedAfterProjection() {
        val oval = sampleOval()
        val original = oval.toList() // snapshot

        TraceProjection.project(listOf(oval), 400.0, 300.0, 0.05)

        // The input list must be unchanged after projection (D-34).
        assertEquals(original.size, oval.size, "input list length unchanged")
        for (i in original.indices) {
            assertEquals(
                original[i].latitude,
                oval[i].latitude,
                1e-12,
                "original latitude at index $i must be unchanged",
            )
            assertEquals(
                original[i].longitude,
                oval[i].longitude,
                1e-12,
                "original longitude at index $i must be unchanged",
            )
        }
    }

    // ── Test 3/4: empty and degenerate inputs (T-03-16) ──────────────────────

    @Test
    fun emptyLayerListReturnsEmptyResult() {
        val result = TraceProjection.project(
            layers = emptyList(),
            width = 400.0,
            height = 300.0,
            padding = 0.05,
        )
        assertTrue(result.isEmpty(), "empty layers → empty result")
    }

    @Test
    fun emptyPointLayerReturnsEmptyProjectedLayer() {
        val result = TraceProjection.project(
            layers = listOf(emptyList()),
            width = 400.0,
            height = 300.0,
            padding = 0.05,
        )
        assertEquals(1, result.size)
        assertTrue(result[0].isEmpty(), "empty point list → empty projected list")
    }

    @Test
    fun singlePointLayerReturnsEmptyOrSingleProjectedPoint() {
        val single = listOf(geo(39.8121, -86.1062))
        val result = TraceProjection.project(
            layers = listOf(single),
            width = 400.0,
            height = 300.0,
            padding = 0.05,
        )
        assertEquals(1, result.size)
        // A single point cannot establish a bounding box, so projected output
        // must be empty (degenerate) — not a crash.
        assertTrue(result[0].isEmpty(), "single point → degenerate bounding box → empty output")
    }

    @Test
    fun allIdenticalPointsReturnsEmptyProjection() {
        val allSame = listOf(
            geo(39.8121, -86.1062),
            geo(39.8121, -86.1062),
            geo(39.8121, -86.1062),
        )
        val result = TraceProjection.project(
            layers = listOf(allSame),
            width = 400.0,
            height = 300.0,
            padding = 0.05,
        )
        assertEquals(1, result.size)
        // All identical points = zero-area bounding box → degenerate → empty.
        assertTrue(result[0].isEmpty(), "identical points → degenerate bounds → empty output")
    }

    // ── Test: multi-layer projection shares common bounding box ──────────────

    @Test
    fun multiLayerProjectionSharesCommonBoundingBox() {
        val baseLat = 39.8121
        val baseLon = -86.1062
        // Layer 1: small inner loop
        val layer1 = (0 until 10).map { i ->
            val angle = i * 2.0 * kotlin.math.PI / 10.0
            geo(baseLat + kotlin.math.sin(angle) * 0.0003, baseLon + kotlin.math.cos(angle) * 0.00015)
        }
        // Layer 2: larger outer loop (wider)
        val layer2 = (0 until 10).map { i ->
            val angle = i * 2.0 * kotlin.math.PI / 10.0
            geo(baseLat + kotlin.math.sin(angle) * 0.0009, baseLon + kotlin.math.cos(angle) * 0.00045)
        }

        val result = TraceProjection.project(
            layers = listOf(layer1, layer2),
            width = 400.0,
            height = 300.0,
            padding = 0.05,
        )
        assertEquals(2, result.size)
        assertEquals(layer1.size, result[0].size)
        assertEquals(layer2.size, result[1].size)

        // The outer layer should reach the edges.
        val allX = result.flatMap { l -> l.map { it.x } }
        val allY = result.flatMap { l -> l.map { it.y } }
        assertTrue(allX.min() >= 0.0, "all x >= 0")
        assertTrue(allX.max() <= 1.0, "all x <= 1")
        assertTrue(allY.min() >= 0.0, "all y >= 0")
        assertTrue(allY.max() <= 1.0, "all y <= 1")

        // At least one layer should have points near the edges (the larger one).
        assertTrue(
            allX.min() < 0.1 || allX.max() > 0.9 || allY.min() < 0.1 || allY.max() > 0.9,
            "at least one point should be near the bounds after projection into shared bounding box",
        )
    }

    // ── Test: aspect ratio is preserved ──────────────────────────────────────

    @Test
    fun projectionPreservesAspectRatio() {
        val points = listOf(
            geo(39.8121, -86.1062),
            geo(39.8130, -86.1062), // ~100m north
            geo(39.8121, -86.1052), // ~100m east
        )
        // Use a tall, narrow canvas: width=200, height=600.
        // With padding=0, the projected points should fit proportionally.
        val result = TraceProjection.project(
            layers = listOf(points),
            width = 200.0,
            height = 600.0,
            padding = 0.0,
        )
        assertEquals(1, result.size)
        val proj = result[0]
        // The bounding box aspect ratio should be preserved — the points
        // span roughly equal distances in lat and lon directions.
        val xSpan = proj.maxOf { it.x } - proj.minOf { it.x }
        val ySpan = proj.maxOf { it.y } - proj.minOf { it.y }
        // In a tall canvas, the vertical span should NOT be 3x the horizontal
        // span (which would happen if aspect ratio is ignored and points are
        // just mapped 1:1 to canvas dimensions). The actual span ratio should
        // be close to the geographic aspect ratio (~1:1 here).
        assertTrue(xSpan > 0.0, "horizontal span > 0")
        assertTrue(ySpan > 0.0, "vertical span > 0")
        // The ratio should be close to 1.0 (±30%) since the input is roughly square.
        val ratio = xSpan / ySpan
        assertTrue(ratio > 0.7 && ratio < 1.3, "aspect ratio should be ~1:1, got xSpan/ySpan=$ratio")
    }
}
