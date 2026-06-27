package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.SegmentGeometry
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

    /** Stable id for the k-th (1-based) intermediate boundary. */
    fun boundaryId(k: Int): String = "sb-$k"

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
