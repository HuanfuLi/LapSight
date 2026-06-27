package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.session.GeoPointDto

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
 * [com.huanfuli.lapsight.shared.lap.LocalProjection]; saved geometry stays in
 * latitude/longitude. Construct via [fromReferenceLine]; invalid geometry yields a
 * typed [ClosedReferencePathResult.Rejected], never an exception.
 */
class ClosedReferencePath private constructor(
    val origin: GeoPoint,
    private val projection: com.huanfuli.lapsight.shared.lap.LocalProjection,
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

    fun toLocal(geo: GeoPointDto): LocalPoint = TODO("Task 1 GREEN")

    fun toGeo(local: LocalPoint): GeoPointDto = TODO("Task 1 GREEN")

    /** Wrap an arc-length value into [0, perimeter). */
    fun wrap(s: Double): Double = TODO("Task 1 GREEN")

    /** The local-meter point at wrapped arc length [s]. */
    fun pointAt(s: Double): LocalPoint = TODO("Task 1 GREEN")

    /** The local-meter point at wrapped arc length [s], in latitude/longitude. */
    fun pointAtGeo(s: Double): GeoPointDto = TODO("Task 1 GREEN")

    /** Unit smoothed tangent at arc length [s]; falls back to the nearest non-degenerate segment. */
    fun tangentAt(s: Double): LocalPoint = TODO("Task 1 GREEN")

    /** Unit normal (tangent rotated +90 degrees) at arc length [s]. */
    fun normalAt(s: Double): LocalPoint = TODO("Task 1 GREEN")

    /** Project a local-meter point onto the path with ambiguity data. */
    fun projectLocal(point: LocalPoint): PathProjection = TODO("Task 1 GREEN")

    /** Project a geographic point onto the path with ambiguity data. */
    fun projectGeo(geo: GeoPointDto): PathProjection = TODO("Task 1 GREEN")

    companion object {
        /** Minimum usable vertices to form a closed loop. */
        const val MIN_POINTS: Int = 3

        fun fromReferenceLine(
            line: TrackReferenceLine,
            thresholds: CourseGeometryThresholds = CourseGeometryThresholds.Default,
        ): ClosedReferencePathResult = TODO("Task 1 GREEN")
    }
}
