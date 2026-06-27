package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.hypot

/**
 * Centralized course-geometry thresholds (Phase 5, 05-RESEARCH Pattern 3).
 *
 * Every tunable distance used by [ClosedReferencePath], [CourseGeometryBuilder],
 * and [CourseProfileEditor] lives here so there is exactly one place to calibrate
 * smoothing, generated-line length, snapping, ambiguity, and minimum spacing
 * against real-track data. Values are the conservative cross-discipline defaults
 * from 05-RESEARCH; they are replay-configurable, never hard-coded at call sites.
 */
data class CourseGeometryThresholds(
    /** Full length of a generated finite timing boundary (perpendicular line). */
    val boundaryLengthMeters: Double = 30.0,
    /** Arc-length snap applied to dragged/placed progress for stable persistence. */
    val snapMeters: Double = 1.0,
    /** A second non-adjacent projection candidate within this margin is ambiguous. */
    val ambiguityMarginMeters: Double = 5.0,
    /** Lower clamp for the smoothed-tangent half-window. */
    val minTangentHalfWindowMeters: Double = 5.0,
    /** Upper clamp for the smoothed-tangent half-window. */
    val maxTangentHalfWindowMeters: Double = 15.0,
    /** Tangent half-window as a fraction of lap length before clamping. */
    val tangentHalfWindowFraction: Double = 0.005,
    /** Absolute floor for minimum cyclic spacing between boundaries. */
    val minSpacingFloorMeters: Double = 20.0,
    /** Minimum cyclic spacing as a fraction of lap length (whichever is larger). */
    val minSpacingFraction: Double = 0.02,
    /** Upper bound on reference-line point count (DoS guard, T-05-10). */
    val maxPathPoints: Int = 4096,
) {
    /** Smoothed-tangent half-window for a path of [perimeter] meters. */
    fun tangentHalfWindow(perimeter: Double): Double =
        (perimeter * tangentHalfWindowFraction)
            .coerceIn(minTangentHalfWindowMeters, maxTangentHalfWindowMeters)

    /** Minimum cyclic spacing between adjacent boundaries on a [perimeter]-meter path. */
    fun minCyclicSpacing(perimeter: Double): Double =
        maxOf(minSpacingFloorMeters, perimeter * minSpacingFraction)

    companion object {
        val Default = CourseGeometryThresholds()
    }
}

/** Why a [TrackReferenceLine] could not become a [ClosedReferencePath] (typed, never thrown). */
enum class ClosedPathRejection {
    /** Fewer than three usable points — no closed loop can be formed. */
    Empty,

    /** A coordinate was NaN or infinite. */
    NonFinite,

    /** More points than the DoS bound allows. */
    Oversized,

    /** Total arc length collapses to ~zero after de-duplication. */
    Degenerate,
}

/** Typed outcome of building a [ClosedReferencePath]; rejection never throws (T-05-10). */
sealed interface ClosedReferencePathResult {
    data class Loaded(val path: ClosedReferencePath) : ClosedReferencePathResult
    data class Rejected(val reason: ClosedPathRejection) : ClosedReferencePathResult
}

/**
 * Result of projecting a point onto the closed path.
 *
 * @property progressMeters absolute recorded arc-length progress in [0, perimeter).
 * @property lateralMeters perpendicular distance from the point to the path.
 * @property segmentIndex the index of the nearest segment (closing segment included).
 * @property isAmbiguous true when a second candidate on a non-adjacent segment is
 *   within [CourseGeometryThresholds.ambiguityMarginMeters] of the nearest — i.e. a
 *   nearby parallel/hairpin section makes the match unreliable.
 * @property runnerUpGapMeters how much worse the nearest non-adjacent runner-up is;
 *   `Double.POSITIVE_INFINITY` when there is no non-adjacent candidate.
 */
data class PathProjection(
    val progressMeters: Double,
    val lateralMeters: Double,
    val segmentIndex: Int,
    val isAmbiguous: Boolean,
    val runnerUpGapMeters: Double,
)

/**
 * One pure, closed-loop arc-length model reused by editor placement, direction
 * transforms, whole-course preflight, and Ghost matching (05-RESEARCH Pattern 3).
 *
 * The last point connects back to the first: the closing segment participates in
 * perimeter, [pointAt], [projectLocal], and wrapping. Zero-length consecutive
 * segments are dropped at construction. Local-meter state comes from the canonical
 * [LocalProjection]; saved geometry stays in latitude/longitude. Construct via
 * [fromReferenceLine]; invalid geometry yields a typed
 * [ClosedReferencePathResult.Rejected], never an exception.
 */
class ClosedReferencePath private constructor(
    val origin: GeoPoint,
    private val projection: LocalProjection,
    /** De-duplicated local-meter vertices in order; the closing segment is implicit. */
    val points: List<LocalPoint>,
    /** Cumulative arc length; `cum[i]` is the distance to vertex i, `cum[size]` the perimeter. */
    private val cum: DoubleArray,
    val thresholds: CourseGeometryThresholds,
) {
    /** Total closed-loop arc length in meters (includes the closing segment). */
    val perimeter: Double get() = cum[points.size]

    /** Number of segments, including the implicit closing segment. */
    val segmentCount: Int get() = points.size

    fun toLocal(geo: GeoPointDto): LocalPoint =
        projection.toLocal(GeoPoint(geo.latitude, geo.longitude))

    fun toGeo(local: LocalPoint): GeoPointDto {
        val g = projection.toGeo(local)
        return GeoPointDto(latitude = g.latitude, longitude = g.longitude)
    }

    /** Wrap an arc-length value into [0, perimeter). */
    fun wrap(s: Double): Double {
        val l = perimeter
        if (l <= 0.0) return 0.0
        var r = s % l
        if (r < 0.0) r += l
        // Guard against `-0.0` and the rare `s % l == l` rounding edge.
        if (r >= l) r -= l
        return r
    }

    /** The local-meter point at wrapped arc length [s]. */
    fun pointAt(s: Double): LocalPoint {
        val target = wrap(s)
        val seg = segmentIndexFor(target)
        val a = points[seg]
        val b = points[(seg + 1) % points.size]
        val segLen = cum[seg + 1] - cum[seg]
        val t = if (segLen <= 0.0) 0.0 else (target - cum[seg]) / segLen
        return LocalPoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    }

    /** The local-meter point at wrapped arc length [s], in latitude/longitude. */
    fun pointAtGeo(s: Double): GeoPointDto = toGeo(pointAt(s))

    /** Unit smoothed tangent at arc length [s]; falls back to the nearest non-degenerate segment. */
    fun tangentAt(s: Double): LocalPoint {
        val w = thresholds.tangentHalfWindow(perimeter)
        val ahead = pointAt(s + w)
        val behind = pointAt(s - w)
        var dx = ahead.x - behind.x
        var dy = ahead.y - behind.y
        var len = hypot(dx, dy)
        if (len < EPSILON) {
            // Degenerate window (e.g. the smoothed chord collapses): fall back to the
            // direction of the nearest non-degenerate segment to the anchor point.
            val seg = nearestNonDegenerateSegment(wrap(s))
            val a = points[seg]
            val b = points[(seg + 1) % points.size]
            dx = b.x - a.x
            dy = b.y - a.y
            len = hypot(dx, dy)
            if (len < EPSILON) return LocalPoint(1.0, 0.0)
        }
        return LocalPoint(dx / len, dy / len)
    }

    /** Unit normal (tangent rotated +90 degrees) at arc length [s]. */
    fun normalAt(s: Double): LocalPoint {
        val t = tangentAt(s)
        return LocalPoint(-t.y, t.x)
    }

    /** Project a local-meter point onto the path with ambiguity data. */
    fun projectLocal(point: LocalPoint): PathProjection {
        val n = points.size
        var bestSeg = 0
        var bestDist = Double.MAX_VALUE
        var bestProgress = 0.0
        // First pass: nearest segment overall.
        for (j in 0 until n) {
            val a = points[j]
            val b = points[(j + 1) % n]
            val t = projectionParameter(point, a, b)
            val px = a.x + (b.x - a.x) * t
            val py = a.y + (b.y - a.y) * t
            val d = hypot(point.x - px, point.y - py)
            if (d < bestDist) {
                bestDist = d
                bestSeg = j
                val segLen = cum[j + 1] - cum[j]
                bestProgress = cum[j] + t * segLen
            }
        }
        // Second pass: nearest candidate on a segment NOT adjacent to the best one.
        var runnerUpDist = Double.POSITIVE_INFINITY
        for (j in 0 until n) {
            if (isAdjacent(bestSeg, j, n)) continue
            val a = points[j]
            val b = points[(j + 1) % n]
            val t = projectionParameter(point, a, b)
            val px = a.x + (b.x - a.x) * t
            val py = a.y + (b.y - a.y) * t
            val d = hypot(point.x - px, point.y - py)
            if (d < runnerUpDist) runnerUpDist = d
        }
        val gap = runnerUpDist - bestDist
        return PathProjection(
            progressMeters = wrap(bestProgress),
            lateralMeters = bestDist,
            segmentIndex = bestSeg,
            isAmbiguous = gap < thresholds.ambiguityMarginMeters,
            runnerUpGapMeters = gap,
        )
    }

    /** Project a geographic point onto the path with ambiguity data. */
    fun projectGeo(geo: GeoPointDto): PathProjection = projectLocal(toLocal(geo))

    /** Binary-search the segment whose arc-length span contains [target] (in [0, perimeter)). */
    private fun segmentIndexFor(target: Double): Int {
        val n = points.size
        var lo = 0
        var hi = n - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (cum[mid] <= target) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** The non-degenerate segment nearest (by arc length) to wrapped position [s]. */
    private fun nearestNonDegenerateSegment(s: Double): Int {
        val n = points.size
        val start = segmentIndexFor(s)
        for (offset in 0 until n) {
            val seg = (start + offset) % n
            if (cum[seg + 1] - cum[seg] > EPSILON) return seg
        }
        return start
    }

    companion object {
        /** Minimum usable vertices to form a closed loop. */
        const val MIN_POINTS: Int = 3

        private const val EPSILON: Double = 1e-9

        fun fromReferenceLine(
            line: TrackReferenceLine,
            thresholds: CourseGeometryThresholds = CourseGeometryThresholds.Default,
        ): ClosedReferencePathResult {
            val raw = line.points
            if (raw.size < MIN_POINTS) return ClosedReferencePathResult.Rejected(ClosedPathRejection.Empty)
            if (raw.size > thresholds.maxPathPoints) {
                return ClosedReferencePathResult.Rejected(ClosedPathRejection.Oversized)
            }
            for (p in raw) {
                if (!p.latitude.isFinite() || !p.longitude.isFinite()) {
                    return ClosedReferencePathResult.Rejected(ClosedPathRejection.NonFinite)
                }
            }

            val origin = GeoPoint(raw.first().latitude, raw.first().longitude)
            val projection = LocalProjection(origin)
            val projected = raw.map { projection.toLocal(GeoPoint(it.latitude, it.longitude)) }

            // Drop consecutive duplicates and a final vertex coincident with the first,
            // so the closing segment is always implicit and never zero length.
            val dedup = dedupeClosed(projected)
            if (dedup.size < MIN_POINTS) {
                return ClosedReferencePathResult.Rejected(ClosedPathRejection.Degenerate)
            }

            val n = dedup.size
            val cum = DoubleArray(n + 1)
            for (i in 1..n) {
                val a = dedup[i - 1]
                val b = dedup[i % n] // i == n closes back to vertex 0
                cum[i] = cum[i - 1] + hypot(b.x - a.x, b.y - a.y)
            }
            if (cum[n] < EPSILON) {
                return ClosedReferencePathResult.Rejected(ClosedPathRejection.Degenerate)
            }

            return ClosedReferencePathResult.Loaded(
                ClosedReferencePath(origin, projection, dedup, cum, thresholds),
            )
        }

        /** Remove consecutive zero-length steps, including a last vertex equal to the first. */
        private fun dedupeClosed(points: List<LocalPoint>): List<LocalPoint> {
            val out = ArrayList<LocalPoint>(points.size)
            for (p in points) {
                val prev = out.lastOrNull()
                if (prev == null || hypot(p.x - prev.x, p.y - prev.y) > EPSILON) out.add(p)
            }
            // If the last vertex coincides with the first, the closing segment already
            // connects them — drop the explicit duplicate.
            while (out.size >= 2 &&
                hypot(out.first().x - out.last().x, out.first().y - out.last().y) <= EPSILON
            ) {
                out.removeAt(out.size - 1)
            }
            return out
        }

        /** Clamped parameter t in [0,1] of the projection of [p] onto segment a->b. */
        private fun projectionParameter(p: LocalPoint, a: LocalPoint, b: LocalPoint): Double {
            val dx = b.x - a.x
            val dy = b.y - a.y
            val lenSq = dx * dx + dy * dy
            if (lenSq <= 0.0) return 0.0
            val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / lenSq
            return t.coerceIn(0.0, 1.0)
        }

        /** Cyclic adjacency of two segment indices on an n-segment loop. */
        private fun isAdjacent(i: Int, j: Int, n: Int): Boolean =
            i == j || (i + 1) % n == j || (j + 1) % n == i
    }
}
