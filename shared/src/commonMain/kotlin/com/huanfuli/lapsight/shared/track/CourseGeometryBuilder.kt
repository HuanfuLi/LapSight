package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.CourseDefinition
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.SectorLine
import com.huanfuli.lapsight.shared.lap.SegmentGeometry
import com.huanfuli.lapsight.shared.lap.StartFinishLine
import kotlin.math.hypot

/**
 * Pure generator of course boundary geometry over a [ClosedReferencePath]
 * (Plan 05-05 Task 2; D-06, D-08, D-09).
 *
 * It owns the rule that boundaries are NOT Sectors: for `N` configured Sectors it
 * produces `N-1` intermediate boundaries at equal closed-loop arc-length intervals
 * starting at the start/finish anchor, each a finite line perpendicular to the
 * smoothed local tangent. Endpoints are always derived from progress + tangent, so
 * they are never user-editable canvas coordinates (D-09, D-10). No Compose,
 * platform, or storage types appear here.
 */
object CourseGeometryBuilder {

    /** A new enabled Sector setup defaults to 3 Sectors (D-07). */
    const val DEFAULT_SECTOR_COUNT: Int = 3

    /** Minimum configurable Sector count when enabled (D-07). */
    const val MIN_SECTOR_COUNT: Int = 2

    /** Maximum configurable Sector count (D-07). */
    const val MAX_SECTOR_COUNT: Int = 6

    /** Two crossing points within this distance are the same physical crossing. */
    private const val CROSSING_DEDUP_METERS: Double = 0.01

    /**
     * Normalized signed side of an accepted RECORDED-direction start/finish
     * crossing for a forward-oriented course (D-18, D-21).
     *
     * A course is "forward-oriented" when a recorded-direction crossing yields a
     * positive [com.huanfuli.lapsight.shared.lap.SegmentGeometry.cross] — the
     * orientation [buildStartFinishLine]/[buildBoundary] and the demo course
     * produce (pointA = center − normal·half, pointB = center + normal·half, with
     * normal = tangent rotated +90°). Reverse keeps this same accepted sign and
     * instead swaps every line's endpoints, so a reverse-direction physical crossing
     * lands on the same positive side.
     */
    const val FORWARD_CROSSING_SIGN: Double = 1.0

    /** Stable id for the k-th (1-based) intermediate boundary. */
    fun boundaryId(k: Int): String = "sb-$k"

    /**
     * Direction-relative closed-loop progress of arc-length [s] for [direction]
     * (D-11, D-18). Recorded measures forward from the start/finish anchor
     * `wrap(s − startProgress, L)`; Reverse measures backward `wrap(startProgress − s, L)`.
     * Both read 0 at the start/finish anchor and increase along their own travel
     * direction, so the same physical revision yields two mirrored progress axes.
     */
    fun directionalProgress(
        direction: CourseDirection,
        s: Double,
        startProgress: Double,
        perimeter: Double,
    ): Double {
        if (perimeter <= 0.0) return 0.0
        val raw = when (direction) {
            CourseDirection.Recorded -> s - startProgress
            CourseDirection.Reverse -> startProgress - s
        }
        var r = raw % perimeter
        if (r < 0.0) r += perimeter
        if (r >= perimeter) r -= perimeter
        return r
    }

    /**
     * Project a recorded-oriented lap [CourseDefinition] into the selected
     * [direction] over the SAME physical anchors (D-11, D-18, D-21).
     *
     * [base] must be forward-oriented (a recorded crossing yields
     * [FORWARD_CROSSING_SIGN]; this is the orientation [buildStartFinishLine] and the
     * demo course produce). The result always declares an explicit accepted approach
     * side so the lap engine enforces orientation from the first crossing with no
     * learned-first-crossing fallback.
     *
     * - Recorded: identical geometry/order, accepted side = [FORWARD_CROSSING_SIGN].
     * - Reverse: every line's endpoints are swapped and the intermediate boundaries
     *   are walked in reverse spatial order (relabeled Sector 1..N from start/finish);
     *   because the endpoints are swapped, a reverse-direction physical crossing again
     *   lands on the [FORWARD_CROSSING_SIGN] side, so the engine accepts the reverse
     *   pass and rejects the recorded pass.
     */
    fun directionalCourse(base: CourseDefinition, direction: CourseDirection): CourseDefinition =
        when (direction) {
            CourseDirection.Recorded -> base.copy(acceptedStartFinishSign = FORWARD_CROSSING_SIGN)
            CourseDirection.Reverse -> {
                val sf = base.startFinish
                val swappedStartFinish = StartFinishLine(pointA = sf.pointB, pointB = sf.pointA)
                val reversedBoundaries = base.orderedSectors.reversed().mapIndexed { index, boundary ->
                    SectorLine(
                        id = boundary.id,
                        name = "Sector ${index + 1}",
                        order = index,
                        // Swap endpoints so this boundary's accepted orientation flips too.
                        pointA = boundary.pointB,
                        pointB = boundary.pointA,
                    )
                }
                CourseDefinition(
                    startFinish = swappedStartFinish,
                    sectors = reversedBoundaries,
                    acceptedStartFinishSign = FORWARD_CROSSING_SIGN,
                )
            }
        }

    /** Equal closed-loop arc-length progresses for the `sectorCount-1` boundaries. */
    fun equalBoundaryProgresses(
        path: ClosedReferencePath,
        startFinishProgress: Double,
        sectorCount: Int,
    ): List<Double> {
        if (sectorCount < 2) return emptyList()
        val l = path.perimeter
        return (1 until sectorCount).map { k ->
            path.wrap(startFinishProgress + l * k.toDouble() / sectorCount.toDouble())
        }
    }

    /** Build a finite perpendicular [SectorBoundary] at arc-length [progress]. */
    fun buildBoundary(
        path: ClosedReferencePath,
        id: String,
        order: Int,
        progress: Double,
    ): SectorBoundary {
        val (a, b) = endpoints(path, progress)
        return SectorBoundary(
            id = id,
            order = order,
            pointA = path.toGeo(a),
            pointB = path.toGeo(b),
            normalizedProgress = path.wrap(progress) / path.perimeter,
        )
    }

    /** Build the finite perpendicular start/finish line at arc-length [progress]. */
    fun buildStartFinishLine(
        path: ClosedReferencePath,
        progress: Double,
    ): StartFinishLineDto {
        val (a, b) = endpoints(path, progress)
        return StartFinishLineDto(pointA = path.toGeo(a), pointB = path.toGeo(b))
    }

    /**
     * How many times the finite perpendicular boundary at [progress] crosses the
     * closed path. A valid boundary crosses exactly once (at its anchor); a count
     * of 2+ means it also cuts a nearby parallel/hairpin section (D-09 invalid).
     *
     * Crossing points are de-duplicated within [CROSSING_DEDUP_METERS] so a touch at
     * a shared vertex between two adjacent segments counts once.
     */
    fun pathCrossingCount(
        path: ClosedReferencePath,
        progress: Double,
    ): Int {
        val (a, b) = endpoints(path, progress)
        val pts = path.points
        val n = pts.size
        val seen = ArrayList<LocalPoint>(4)
        for (j in 0 until n) {
            val crossing = SegmentGeometry.intersectMovementWithLine(
                moveStart = a,
                moveEnd = b,
                lineA = pts[j],
                lineB = pts[(j + 1) % n],
            ) ?: continue
            val p = crossing.crossingPoint
            if (seen.none { hypot(it.x - p.x, it.y - p.y) < CROSSING_DEDUP_METERS }) {
                seen.add(p)
            }
        }
        return seen.size
    }

    /** The two finite endpoints (local meters) of the perpendicular boundary at [progress]. */
    private fun endpoints(path: ClosedReferencePath, progress: Double): Pair<LocalPoint, LocalPoint> {
        val center = path.pointAt(progress)
        val normal = path.normalAt(progress)
        val half = path.thresholds.boundaryLengthMeters / 2.0
        val a = LocalPoint(center.x - normal.x * half, center.y - normal.y * half)
        val b = LocalPoint(center.x + normal.x * half, center.y + normal.y * half)
        return a to b
    }
}
