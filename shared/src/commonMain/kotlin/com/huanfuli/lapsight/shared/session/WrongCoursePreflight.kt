package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.track.ClosedPathRejection
import com.huanfuli.lapsight.shared.track.ClosedReferencePath
import com.huanfuli.lapsight.shared.track.ClosedReferencePathResult
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.math.max

/**
 * Conservative pre-Timing course-distance thresholds from Phase 5 research.
 *
 * These values intentionally prefer an unknown/allowed start over a false
 * wrong-course claim. They are centralized and injectable for deterministic
 * replay calibration.
 */
data class WrongCoursePreflightThresholds(
    val blockDistanceMeters: Double = 250.0,
    val maxHorizontalAccuracyMeters: Double = 100.0,
    val maxFixAgeMillis: Long = 15_000L,
) {
    init {
        require(blockDistanceMeters > 0.0 && blockDistanceMeters.isFinite())
        require(maxHorizontalAccuracyMeters > 0.0 && maxHorizontalAccuracyMeters.isFinite())
        require(maxFixAgeMillis >= 0L)
    }

    companion object {
        val Default = WrongCoursePreflightThresholds()
    }
}

/** Why preflight could not make a trustworthy whole-course decision. */
enum class CoursePreflightUnavailableReason {
    MissingFix,
    NonFiniteFix,
    PoorAccuracy,
    StaleFix,
    MalformedGeometry,
    NonFiniteGeometry,
    OversizedGeometry,
}

/**
 * One-time whole-course decision made before a recorder or lap engine exists.
 *
 * [Ready] and [Blocked] report both raw nearest-course distance and the
 * conservative distance after subtracting horizontal-accuracy uncertainty.
 * Bad input is data ([Unavailable]), never an exception or a wrong-course claim.
 */
sealed interface CoursePreflightResult {
    data class Ready(
        val distanceMeters: Double,
        val conservativeDistanceMeters: Double,
        val thresholdMeters: Double,
    ) : CoursePreflightResult

    data class Blocked(
        val distanceMeters: Double,
        val conservativeDistanceMeters: Double,
        val thresholdMeters: Double,
    ) : CoursePreflightResult

    data class Unavailable(
        val reason: CoursePreflightUnavailableReason,
    ) : CoursePreflightResult
}

/**
 * Pure, bounded wrong-course preflight over the complete closed reference path.
 *
 * The closing last-to-first segment is included by [ClosedReferencePath].
 * Preflight owns no UI, storage, platform, matcher, or lap-engine state.
 */
class WrongCoursePreflight(
    private val thresholds: WrongCoursePreflightThresholds =
        WrongCoursePreflightThresholds.Default,
) {
    fun evaluate(
        referenceLine: TrackReferenceLine,
        latestFix: LocationSample?,
        nowElapsedMillis: Long,
    ): CoursePreflightResult {
        val fix = latestFix
            ?: return CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.MissingFix,
            )
        if (!fix.latitude.isFinite() ||
            !fix.longitude.isFinite() ||
            fix.latitude !in -90.0..90.0 ||
            fix.longitude !in -180.0..180.0
        ) {
            return CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.NonFiniteFix,
            )
        }
        val accuracy = fix.horizontalAccuracyMeters
        if (accuracy == null ||
            !accuracy.isFinite() ||
            accuracy < 0.0 ||
            accuracy > thresholds.maxHorizontalAccuracyMeters
        ) {
            return CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.PoorAccuracy,
            )
        }
        val ageMillis = nowElapsedMillis - fix.elapsedMillis
        if (fix.elapsedMillis < 0L || ageMillis < 0L || ageMillis > thresholds.maxFixAgeMillis) {
            return CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.StaleFix,
            )
        }
        if (!referenceLine.isClosed) {
            return CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.MalformedGeometry,
            )
        }

        val path = when (val loaded = ClosedReferencePath.fromReferenceLine(referenceLine)) {
            is ClosedReferencePathResult.Loaded -> loaded.path
            is ClosedReferencePathResult.Rejected -> {
                return CoursePreflightResult.Unavailable(loaded.reason.toPreflightReason())
            }
        }
        val projection = path.projectGeo(
            GeoPointDto(latitude = fix.latitude, longitude = fix.longitude),
        )
        val distance = projection.lateralMeters
        if (!distance.isFinite()) {
            return CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.MalformedGeometry,
            )
        }
        val conservativeDistance = max(0.0, distance - accuracy)
        return if (conservativeDistance > thresholds.blockDistanceMeters) {
            CoursePreflightResult.Blocked(
                distanceMeters = distance,
                conservativeDistanceMeters = conservativeDistance,
                thresholdMeters = thresholds.blockDistanceMeters,
            )
        } else {
            CoursePreflightResult.Ready(
                distanceMeters = distance,
                conservativeDistanceMeters = conservativeDistance,
                thresholdMeters = thresholds.blockDistanceMeters,
            )
        }
    }
}

private fun ClosedPathRejection.toPreflightReason(): CoursePreflightUnavailableReason = when (this) {
    ClosedPathRejection.NonFinite -> CoursePreflightUnavailableReason.NonFiniteGeometry
    ClosedPathRejection.Oversized -> CoursePreflightUnavailableReason.OversizedGeometry
    ClosedPathRejection.Empty,
    ClosedPathRejection.Degenerate,
    -> CoursePreflightUnavailableReason.MalformedGeometry
}
