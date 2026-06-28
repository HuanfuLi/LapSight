package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import kotlin.math.roundToLong

/**
 * Clean-room ghost/reference-lap domain models.
 *
 * These types are pure shared Kotlin with no Compose, platform, storage, or
 * serialization dependency (D-07: telemetry-ready data, but no chart/UI here).
 * Storage DTOs and persistence live in a later plan; this file only defines the
 * algorithmic contract used by [ProgressCurveBuilder] and [LiveDeltaEngine].
 */

/**
 * A single point on a distance-indexed progress curve (D-06).
 *
 * Carries everything later telemetry work needs: progress distance, normalized
 * progress, elapsed time relative to lap start, raw geographic coordinates, the
 * local projected meters, and optional speed/heading/accuracy. Sector metadata
 * hooks are intentionally left for a future plan.
 *
 * @param elapsedMillis time since the lap's first sample (lap-relative).
 * @param progressMeters cumulative distance travelled along the lap path.
 * @param normalizedProgress [progressMeters] divided by total distance, in [0, 1].
 * @param latitude raw geographic latitude preserved for replay/export.
 * @param longitude raw geographic longitude preserved for replay/export.
 * @param localX local east meters relative to the lap origin.
 * @param localY local north meters relative to the lap origin.
 */
data class ProgressPoint(
    val elapsedMillis: Long,
    val progressMeters: Double,
    val normalizedProgress: Double,
    val latitude: Double,
    val longitude: Double,
    val localX: Double,
    val localY: Double,
    val speedMetersPerSecond: Double?,
    val headingDegrees: Double?,
    val horizontalAccuracyMeters: Double?,
)

/**
 * A monotonic, distance-indexed progress curve for one lap (D-05/D-06).
 *
 * Points are ordered by increasing [ProgressPoint.progressMeters]; the first
 * point is at distance 0 and the last point is at [totalDistanceMeters].
 */
data class ProgressCurve(
    val totalDistanceMeters: Double,
    val points: List<ProgressPoint>,
) {

    /**
     * Interpolate lap-relative elapsed time at a given progress distance.
     *
     * Input progress is clamped to `[0, totalDistanceMeters]` so out-of-range
     * lookups never produce stale or non-finite output (threat T-04-02).
     *
     * @return interpolated elapsed millis, or `null` when the curve has fewer
     *         than two points and cannot be interpolated.
     */
    fun elapsedAtProgress(progressMeters: Double): Long? {
        if (points.size < 2) return null
        val clamped = progressMeters.coerceIn(0.0, totalDistanceMeters)

        // Binary search for the last point whose progress is <= clamped.
        var lo = 0
        var hi = points.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (points[mid].progressMeters <= clamped) lo = mid else hi = mid - 1
        }
        if (lo >= points.size - 1) return points.last().elapsedMillis

        val a = points[lo]
        val b = points[lo + 1]
        val span = b.progressMeters - a.progressMeters
        val frac = if (span <= 0.0) 0.0 else (clamped - a.progressMeters) / span
        return (a.elapsedMillis + frac * (b.elapsedMillis - a.elapsedMillis)).roundToLong()
    }
}

/**
 * A reference lap used as the ghost target for live delta (D-05).
 *
 * Stores both the raw samples (for replay/export/future algorithm work) and the
 * precomputed [progressCurve] that powers realtime delta without rescanning raw
 * GPS on every sample.
 *
 * @param isSimulated true for demo/simulated laps, which must stay isolated from
 *        real Track ghost calculations (D-04). Kept as a simple flag here; the
 *        richer source metadata and persistence belong to a later storage plan.
 * @param compatibilityKey full profile/geometry/direction/source identity used
 *        to decide whether another reference may be compared or reused (D-15,
 *        D-16, D-18, D-19). V1 Track-scoped references receive a deterministic
 *        Recorded-only migrated key.
 */
data class ReferenceLap(
    val trackId: String,
    val sessionId: String,
    val lapNumber: Int,
    val durationMillis: Long,
    val isSimulated: Boolean,
    val rawSamples: List<LocationSample>,
    val progressCurve: ProgressCurve,
    val compatibilityKey: CourseCompatibilityKey = GhostCompatibility.migratedV1Key(
        trackId = trackId,
        isSimulated = isSimulated,
    ),
)

/** Why a progress curve could not be built from raw lap samples (T-04-01). */
enum class ProgressCurveFailure {
    /** Fewer than two usable samples remained after filtering. */
    TooFewSamples,

    /** A sample carried a non-finite latitude/longitude. */
    NonFiniteCoordinates,

    /** Sample timestamps were not strictly increasing. */
    NonMonotonicTimestamps,

    /** Accumulated path distance was effectively zero. */
    ZeroDistance,
}

/** Typed result of [ProgressCurveBuilder.build] — never throws for bad GPS input. */
sealed interface ProgressCurveResult {
    data class Success(val curve: ProgressCurve) : ProgressCurveResult
    data class Failure(val reason: ProgressCurveFailure) : ProgressCurveResult
}

/** Confidence attached to a course-position match. */
enum class CourseMatchConfidence {
    Confident,
}

/** Typed reason a realtime sample could not be matched to the selected course. */
enum class CourseUnmatchedReason {
    NonFiniteInput,
    PoorAccuracy,
    OffCourse,
    Ambiguous,
    NonMonotonicTime,
    ImplausibleJump,
    IncompatibleCourse,
    IncompatibleSource,
}

/**
 * Direction-relative course position for one realtime GPS sample.
 *
 * Matcher failures are data, not exceptions: callers suppress only live delta
 * and continue lap timing/raw capture unchanged.
 */
sealed interface CourseMatchResult {
    data class Matched(
        val directionProgressMeters: Double,
        val normalizedProgress: Double,
        val lateralDistanceMeters: Double,
        val confidence: CourseMatchConfidence,
    ) : CourseMatchResult

    data class Unmatched(
        val reason: CourseUnmatchedReason,
    ) : CourseMatchResult
}

/** Why a live delta is suppressed and shown as `--` (D-11/D-17/D-18). */
enum class DeltaUnavailableReason {
    /** No current lap is being timed yet. */
    NoCurrentLap,

    /** No reference lap is loaded for the active Track/source. */
    NoReference,

    /** Fewer than two current-lap samples have arrived. */
    InsufficientCurrentSamples,

    /** The reference progress curve has fewer than two points. */
    InsufficientReference,

    /** Current GPS quality is too poor to trust the delta. */
    PoorGpsQuality,

    /** Current progress cannot be matched confidently to the reference. */
    UnmatchedProgress,
}

/**
 * Display-independent live delta state (D-09/D-10).
 *
 * [Available.deltaMillis] equals current lap elapsed minus reference elapsed at
 * equivalent progress: positive means slower, negative means faster. UI sign and
 * color formatting (`+0.421s`, `-0.218s`, `--`) is layered on later in Plan 04-03.
 */
sealed interface LiveDeltaSnapshot {
    data class Available(
        val deltaMillis: Long,
        val currentElapsedMillis: Long,
        val referenceElapsedMillis: Long,
        val progressMeters: Double,
        val normalizedProgress: Double,
    ) : LiveDeltaSnapshot

    data class Unavailable(
        val reason: DeltaUnavailableReason,
    ) : LiveDeltaSnapshot
}
