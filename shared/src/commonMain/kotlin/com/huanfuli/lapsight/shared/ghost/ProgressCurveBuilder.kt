package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import kotlin.math.sqrt

/**
 * Pure builder that turns a completed lap's raw samples into a distance-indexed
 * [ProgressCurve] (D-05/D-06).
 *
 * The builder is clean-room shared Kotlin: it depends only on [LocationSample]
 * and the existing [LocalProjection] geometry. It never throws for normal bad
 * GPS input — instead it returns a typed [ProgressCurveResult.Failure] so callers
 * can suppress delta rather than crash (threat T-04-01).
 *
 * Algorithm:
 * 1. Drop samples whose horizontal accuracy exceeds the configured threshold.
 * 2. Reject too-few-sample, non-finite, and non-monotonic-timestamp inputs.
 * 3. Project lat/lon into local meters around the first usable sample.
 * 4. Accumulate Euclidean segment distance into monotonic progress meters.
 * 5. Normalize progress and reject a degenerate (zero-distance) path.
 */
object ProgressCurveBuilder {

    /** Default maximum horizontal accuracy (meters) for a usable sample. */
    const val DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS: Double = 50.0

    /** Below this total path length the lap is treated as stationary noise. */
    private const val MIN_TOTAL_DISTANCE_METERS: Double = 1e-6

    /**
     * Build a progress curve from [samples].
     *
     * @param samples raw lap samples in timestamp order.
     * @param maxHorizontalAccuracyMeters samples worse than this are discarded;
     *        samples with a null accuracy are always kept.
     */
    fun build(
        samples: List<LocationSample>,
        maxHorizontalAccuracyMeters: Double = DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    ): ProgressCurveResult {
        val usable = samples.filter { sample ->
            val accuracy = sample.horizontalAccuracyMeters
            accuracy == null || accuracy <= maxHorizontalAccuracyMeters
        }
        if (usable.size < 2) return ProgressCurveResult.Failure(ProgressCurveFailure.TooFewSamples)

        for (sample in usable) {
            if (!sample.latitude.isFinite() || !sample.longitude.isFinite()) {
                return ProgressCurveResult.Failure(ProgressCurveFailure.NonFiniteCoordinates)
            }
        }

        for (i in 1 until usable.size) {
            if (usable[i].elapsedMillis <= usable[i - 1].elapsedMillis) {
                return ProgressCurveResult.Failure(ProgressCurveFailure.NonMonotonicTimestamps)
            }
        }

        val baseElapsed = usable.first().elapsedMillis
        val origin = GeoPoint(usable.first().latitude, usable.first().longitude)
        val projection = LocalProjection(origin)
        val locals = usable.map { projection.toLocal(GeoPoint(it.latitude, it.longitude)) }

        val progressMeters = DoubleArray(usable.size)
        var accumulated = 0.0
        for (i in 1 until usable.size) {
            val dx = locals[i].x - locals[i - 1].x
            val dy = locals[i].y - locals[i - 1].y
            accumulated += sqrt(dx * dx + dy * dy)
            progressMeters[i] = accumulated
        }

        val total = accumulated
        if (total < MIN_TOTAL_DISTANCE_METERS) {
            return ProgressCurveResult.Failure(ProgressCurveFailure.ZeroDistance)
        }

        val points = usable.indices.map { i ->
            ProgressPoint(
                elapsedMillis = usable[i].elapsedMillis - baseElapsed,
                progressMeters = progressMeters[i],
                normalizedProgress = progressMeters[i] / total,
                latitude = usable[i].latitude,
                longitude = usable[i].longitude,
                localX = locals[i].x,
                localY = locals[i].y,
                speedMetersPerSecond = usable[i].speedMetersPerSecond,
                headingDegrees = usable[i].headingDegrees,
                horizontalAccuracyMeters = usable[i].horizontalAccuracyMeters,
            )
        }

        return ProgressCurveResult.Success(ProgressCurve(total, points))
    }
}
