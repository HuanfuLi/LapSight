package com.huanfuli.lapsight.shared.lap

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Result of testing a movement segment against a timing line segment.
 *
 * @param crossingPoint where the two segments intersect, in local meters.
 * @param ratio interpolation parameter along the movement segment in [0, 1].
 *   0 means the crossing is at the segment start, 1 at the segment end.
 * @param signedSideBefore signed side of the movement start relative to the
 *   line (sign of the 2D cross product). Used for direction gating.
 */
data class SegmentCrossing(
    val crossingPoint: LocalPoint,
    val ratio: Double,
    val signedSideBefore: Double,
)

/**
 * Pure 2D segment geometry in local meter space. No state, no platform deps.
 *
 * The core operation is [intersectMovementWithLine]: given a movement segment
 * (two consecutive samples) and a timing line segment, it reports whether and
 * where the movement crosses the line, plus the interpolation ratio used to
 * estimate the crossing timestamp.
 */
object SegmentGeometry {

    /** Numerical tolerance for treating a determinant as zero (parallel). */
    const val EPSILON: Double = 1e-9

    /** 2D cross product of vectors (a->b) and (a->c). Sign gives the side of c. */
    fun cross(a: LocalPoint, b: LocalPoint, c: LocalPoint): Double {
        val abx = b.x - a.x
        val aby = b.y - a.y
        val acx = c.x - a.x
        val acy = c.y - a.y
        return abx * acy - aby * acx
    }

    /** Euclidean distance in meters between two local points. */
    fun distance(a: LocalPoint, b: LocalPoint): Double = hypot(b.x - a.x, b.y - a.y)

    /**
     * Intersect a movement segment [moveStart] -> [moveEnd] with a line segment
     * [lineA] -> [lineB].
     *
     * Returns a [SegmentCrossing] when the movement actually crosses the finite
     * line segment, otherwise `null`. Handles these cases explicitly:
     * - clear crossing: returns the intersection
     * - no crossing: returns null
     * - touching endpoint: counted as a crossing if it lies on both segments
     * - nearly parallel: returns null (determinant below [EPSILON])
     * - movement starting on the line: ratio clamps to ~0 and is returned
     *
     * The interpolation ratio is the fraction along the movement segment and is
     * clamped to [0, 1]; intersections strictly outside the segments return null.
     */
    fun intersectMovementWithLine(
        moveStart: LocalPoint,
        moveEnd: LocalPoint,
        lineA: LocalPoint,
        lineB: LocalPoint,
    ): SegmentCrossing? {
        val rx = moveEnd.x - moveStart.x
        val ry = moveEnd.y - moveStart.y
        val sx = lineB.x - lineA.x
        val sy = lineB.y - lineA.y

        val denom = rx * sy - ry * sx
        if (abs(denom) < EPSILON) {
            // Parallel or both degenerate: no well-defined single crossing.
            return null
        }

        val qpx = lineA.x - moveStart.x
        val qpy = lineA.y - moveStart.y

        // t along the movement segment, u along the line segment.
        val t = (qpx * sy - qpy * sx) / denom
        val u = (qpx * ry - qpy * rx) / denom

        if (t < -EPSILON || t > 1.0 + EPSILON) return null
        if (u < -EPSILON || u > 1.0 + EPSILON) return null

        val ratio = t.coerceIn(0.0, 1.0)
        val crossing = LocalPoint(
            x = moveStart.x + ratio * rx,
            y = moveStart.y + ratio * ry,
        )
        return SegmentCrossing(
            crossingPoint = crossing,
            ratio = ratio,
            signedSideBefore = cross(lineA, lineB, moveStart),
        )
    }

    /**
     * Interpolate a timestamp at [ratio] (in [0, 1]) between two sample times.
     * Returns null if the ratio is outside [0, 1].
     */
    fun interpolateTimestamp(startMillis: Long, endMillis: Long, ratio: Double): Long? {
        if (ratio < 0.0 || ratio > 1.0) return null
        return startMillis + ((endMillis - startMillis).toDouble() * ratio).toLong()
    }

    /**
     * Heading in degrees [0, 360) of the movement vector, measured clockwise
     * from local north. Returns null for a zero-length movement.
     */
    fun headingDegrees(moveStart: LocalPoint, moveEnd: LocalPoint): Double? {
        val dx = moveEnd.x - moveStart.x
        val dy = moveEnd.y - moveStart.y
        if (abs(dx) < EPSILON && abs(dy) < EPSILON) return null
        val deg = atan2(dx, dy) * 180.0 / kotlin.math.PI
        return (deg + 360.0) % 360.0
    }
}
