package com.huanfuli.lapsight.shared.lap

/**
 * Identity of the timing line a crossing candidate belongs to.
 */
sealed interface TimingLineRef {
    /** The single start/finish line. */
    data object StartFinish : TimingLineRef

    /** A sector line, identified by its stable id and order. */
    data class Sector(val id: String, val order: Int) : TimingLineRef
}

/**
 * A detected crossing candidate produced by [CrossingDetector].
 *
 * This is geometry only; acceptance (filters, state) is the engine's job.
 *
 * @param line which timing line was crossed.
 * @param crossingMillis interpolated crossing timestamp.
 * @param crossingPoint crossing location in local meters.
 * @param ratio interpolation ratio along the movement segment.
 * @param headingDegrees movement heading at the crossing, if computable.
 * @param signedSide signed side of the movement start relative to the line.
 *   Its sign indicates crossing direction.
 * @param horizontalAccuracyMeters worst accuracy of the two samples, if known.
 * @param speedMetersPerSecond movement speed across the segment.
 */
data class CrossingCandidate(
    val line: TimingLineRef,
    val crossingMillis: Long,
    val crossingPoint: LocalPoint,
    val ratio: Double,
    val headingDegrees: Double?,
    val signedSide: Double,
    val horizontalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
)

/**
 * Stateless detector that tests one movement segment (two consecutive projected
 * samples) against a single timing line and returns a crossing candidate or null.
 *
 * The same primitive serves the start/finish line and sector lines; the engine
 * applies different acceptance rules per line type. The detector never mutates
 * session state and reports at most one candidate per movement segment.
 *
 * @property projection projection used to map line geometry into local meters.
 */
class CrossingDetector(private val projection: LocalProjection) {

    /**
     * Test a movement against the start/finish line.
     */
    fun detectStartFinish(
        line: StartFinishLine,
        movement: MovementSegment,
    ): CrossingCandidate? = detect(
        ref = TimingLineRef.StartFinish,
        lineA = line.pointA,
        lineB = line.pointB,
        movement = movement,
    )

    /**
     * Test a movement against a sector line.
     */
    fun detectSector(
        line: SectorLine,
        movement: MovementSegment,
    ): CrossingCandidate? = detect(
        ref = TimingLineRef.Sector(line.id, line.order),
        lineA = line.pointA,
        lineB = line.pointB,
        movement = movement,
    )

    private fun detect(
        ref: TimingLineRef,
        lineA: GeoPoint,
        lineB: GeoPoint,
        movement: MovementSegment,
    ): CrossingCandidate? {
        val crossing = SegmentGeometry.intersectMovementWithLine(
            moveStart = movement.startLocal,
            moveEnd = movement.endLocal,
            lineA = projection.toLocal(lineA),
            lineB = projection.toLocal(lineB),
        ) ?: return null

        val crossingMillis = SegmentGeometry.interpolateTimestamp(
            startMillis = movement.startMillis,
            endMillis = movement.endMillis,
            ratio = crossing.ratio,
        ) ?: return null

        return CrossingCandidate(
            line = ref,
            crossingMillis = crossingMillis,
            crossingPoint = crossing.crossingPoint,
            ratio = crossing.ratio,
            headingDegrees = SegmentGeometry.headingDegrees(movement.startLocal, movement.endLocal),
            signedSide = crossing.signedSideBefore,
            horizontalAccuracyMeters = movement.horizontalAccuracyMeters,
            speedMetersPerSecond = movement.speedMetersPerSecond,
        )
    }
}

/**
 * Two consecutive samples already projected into local meters, plus the quality
 * metadata needed for filtering. The engine builds these from a sample stream.
 *
 * @param speedMetersPerSecond movement speed. Defaults to the segment distance
 *   over the time delta if not supplied by the sample.
 */
data class MovementSegment(
    val startLocal: LocalPoint,
    val endLocal: LocalPoint,
    val startMillis: Long,
    val endMillis: Long,
    val horizontalAccuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
) {
    /** Derived speed from geometry/time if no measured speed is available. */
    fun effectiveSpeed(): Double {
        speedMetersPerSecond?.let { return it }
        val dtSeconds = (endMillis - startMillis).toDouble() / 1_000.0
        if (dtSeconds <= 0.0) return 0.0
        return SegmentGeometry.distance(startLocal, endLocal) / dtSeconds
    }
}
