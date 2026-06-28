package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.track.ClosedReferencePath
import com.huanfuli.lapsight.shared.track.CourseGeometryBuilder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Centralized live course-match thresholds from Phase 5 research Pattern 6.
 */
data class CourseProgressMatcherThresholds(
    val maxHorizontalAccuracyMeters: Double =
        ProgressCurveBuilder.DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    val minLateralDistanceLimitMeters: Double = 25.0,
    val accuracyLateralMultiplier: Double = 2.0,
    val minContinuityJumpMeters: Double = 50.0,
    val continuitySpeedMultiplier: Double = 3.0,
    val continuitySlackMeters: Double = 15.0,
) {
    init {
        require(maxHorizontalAccuracyMeters > 0.0 && maxHorizontalAccuracyMeters.isFinite())
        require(minLateralDistanceLimitMeters > 0.0 && minLateralDistanceLimitMeters.isFinite())
        require(accuracyLateralMultiplier >= 0.0 && accuracyLateralMultiplier.isFinite())
        require(minContinuityJumpMeters > 0.0 && minContinuityJumpMeters.isFinite())
        require(continuitySpeedMultiplier >= 0.0 && continuitySpeedMultiplier.isFinite())
        require(continuitySlackMeters >= 0.0 && continuitySlackMeters.isFinite())
    }

    companion object {
        val Default = CourseProgressMatcherThresholds()
    }
}

/**
 * Pure O(reference point count) direction-relative course matcher.
 *
 * The matcher owns no lap clock and no persistence. It validates each sample,
 * projects it through the shared [ClosedReferencePath], rejects off-course,
 * ambiguous, or implausibly discontinuous positions, and transforms accepted
 * recorded path position into the selected [CourseCompatibilityKey.direction].
 * Backward movement remains valid and may decrease direction progress.
 */
class CourseProgressMatcher(
    private val path: ClosedReferencePath,
    startFinishProgressMeters: Double,
    val compatibilityKey: CourseCompatibilityKey,
    private val thresholds: CourseProgressMatcherThresholds =
        CourseProgressMatcherThresholds.Default,
) {
    private val startFinishProgressMeters = path.wrap(startFinishProgressMeters)
    private var previousMatchedProgressMeters: Double? = null
    private var previousMatchedElapsedMillis: Long? = null

    fun reset() {
        previousMatchedProgressMeters = null
        previousMatchedElapsedMillis = null
    }

    fun match(
        sample: LocationSample,
        activeCompatibilityKey: CourseCompatibilityKey,
    ): CourseMatchResult {
        if (!GhostCompatibility.isSameSlot(compatibilityKey, activeCompatibilityKey)) {
            return CourseMatchResult.Unmatched(CourseUnmatchedReason.IncompatibleCourse)
        }
        val sampleIsSimulated = sample.source == LocationSource.Simulated
        if (sampleIsSimulated != compatibilityKey.isSimulated) {
            return CourseMatchResult.Unmatched(CourseUnmatchedReason.IncompatibleSource)
        }
        if (!sample.latitude.isFinite() ||
            !sample.longitude.isFinite() ||
            sample.speedMetersPerSecond?.let { !it.isFinite() || it < 0.0 } == true
        ) {
            return CourseMatchResult.Unmatched(CourseUnmatchedReason.NonFiniteInput)
        }

        val accuracy = sample.horizontalAccuracyMeters
        if (accuracy != null &&
            (!accuracy.isFinite() || accuracy < 0.0 || accuracy > thresholds.maxHorizontalAccuracyMeters)
        ) {
            return CourseMatchResult.Unmatched(CourseUnmatchedReason.PoorAccuracy)
        }

        val projection = path.projectGeo(
            GeoPointDto(latitude = sample.latitude, longitude = sample.longitude),
        )
        val lateralLimit = max(
            thresholds.minLateralDistanceLimitMeters,
            thresholds.accuracyLateralMultiplier * (accuracy ?: 0.0),
        )
        if (projection.lateralMeters > lateralLimit) {
            return CourseMatchResult.Unmatched(CourseUnmatchedReason.OffCourse)
        }
        if (projection.isAmbiguous) {
            return CourseMatchResult.Unmatched(CourseUnmatchedReason.Ambiguous)
        }

        val directionProgress = CourseGeometryBuilder.directionalProgress(
            direction = compatibilityKey.direction,
            s = projection.progressMeters,
            startProgress = startFinishProgressMeters,
            perimeter = path.perimeter,
        )

        val previousProgress = previousMatchedProgressMeters
        val previousElapsed = previousMatchedElapsedMillis
        if (previousProgress != null && previousElapsed != null) {
            val elapsedMillis = sample.elapsedMillis - previousElapsed
            if (elapsedMillis <= 0L) {
                return CourseMatchResult.Unmatched(CourseUnmatchedReason.NonMonotonicTime)
            }
            val rawDelta = abs(directionProgress - previousProgress)
            val cyclicDelta = min(rawDelta, path.perimeter - rawDelta)
            val seconds = elapsedMillis / 1_000.0
            val continuityLimit = max(
                thresholds.minContinuityJumpMeters,
                thresholds.continuitySpeedMultiplier *
                    (sample.speedMetersPerSecond ?: 0.0) *
                    seconds +
                    thresholds.continuitySlackMeters,
            )
            if (cyclicDelta > continuityLimit) {
                return CourseMatchResult.Unmatched(CourseUnmatchedReason.ImplausibleJump)
            }
        }

        previousMatchedProgressMeters = directionProgress
        previousMatchedElapsedMillis = sample.elapsedMillis
        return CourseMatchResult.Matched(
            directionProgressMeters = directionProgress,
            normalizedProgress = directionProgress / path.perimeter,
            lateralDistanceMeters = projection.lateralMeters,
            confidence = CourseMatchConfidence.Confident,
        )
    }
}
