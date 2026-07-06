package com.huanfuli.lapsight.shared

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Velocity-aided position filter over raw GNSS fixes.
 *
 * A phone receiver's Doppler-derived speed/heading is roughly two orders of
 * magnitude more accurate (~0.1 m/s) than its position solution (meters), so
 * between fixes the position estimate coasts along the last reported velocity
 * and each arriving fix corrects it only as much as its reported accuracy
 * deserves — a scalar constant-velocity Kalman filter over a local
 * east/north-meters plane.
 *
 * Robustness:
 * - Per-fix weighting: a tight fix pulls the estimate hard, a sloppy one barely
 *   nudges it ([LocationSample.horizontalAccuracyMeters], clamped, with a
 *   conservative default when absent).
 * - Outlier gating: a fix far outside the innovation gate is heavily deweighted
 *   instead of trusted; several consecutive rejections mean the world moved
 *   (tunnel exit, feed hiccup) and the filter re-anchors on the measurement.
 * - Feed restarts: a non-advancing or long-gapped [LocationSample.elapsedMillis]
 *   re-anchors rather than filtering across the discontinuity.
 *
 * The filter is deterministic (no wall clock) and pure arithmetic, so the same
 * sample stream always produces the same output and it can be re-run over
 * persisted raw traces later. Only latitude/longitude are rewritten; every
 * other field passes through untouched.
 *
 * Display-only for now: raw fixes remain the source of truth for the recorder
 * and [com.huanfuli.lapsight.shared.lap.LapEngine]. Feeding timing from this
 * filter is a separate, regression-checked step.
 */
class VelocityAidedGpsFilter {

    private var hasEstimate = false
    private var referenceLatitude = 0.0
    private var referenceLongitude = 0.0
    private var metersPerDegreeLongitude = METERS_PER_DEGREE_LATITUDE

    /** Position estimate in meters east/north of the reference anchor. */
    private var east = 0.0
    private var north = 0.0

    /** Isotropic position variance (m²) of the estimate. */
    private var variance = 0.0

    /** Doppler velocity of the last accepted fix, east/north m/s. */
    private var velocityEast = 0.0
    private var velocityNorth = 0.0
    private var hasVelocity = false

    private var lastElapsedMillis = 0L
    private var consecutiveOutliers = 0

    /** Forget all state; the next sample re-anchors the filter. */
    fun reset() {
        hasEstimate = false
    }

    /**
     * Fold one fix into the filter and return it with filtered coordinates.
     *
     * Non-finite positions, the first fix after construction/[reset], and fixes
     * across a time discontinuity pass through unchanged (the filter re-anchors
     * on them).
     */
    fun update(sample: LocationSample): LocationSample {
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite()) return sample

        val accuracyMeters = (
            sample.horizontalAccuracyMeters?.takeIf { it.isFinite() && it > 0.0 }
                ?: DEFAULT_ACCURACY_METERS
            ).coerceIn(MIN_ACCURACY_METERS, MAX_ACCURACY_METERS)
        val measurementVariance = accuracyMeters * accuracyMeters

        val dtSeconds = (sample.elapsedMillis - lastElapsedMillis) / 1000.0
        if (!hasEstimate || dtSeconds <= 0.0 || dtSeconds > RESET_GAP_SECONDS) {
            anchor(sample, measurementVariance)
            return sample
        }

        // Predict: coast along the last Doppler velocity. Without Doppler the
        // estimate holds position with much wider uncertainty, so the next
        // measurement dominates instead of being dragged toward a stale point.
        east += velocityEast * dtSeconds
        north += velocityNorth * dtSeconds
        val coastSigma = if (hasVelocity) {
            VELOCITY_SIGMA * dtSeconds + 0.5 * ACCELERATION_SIGMA * dtSeconds * dtSeconds
        } else {
            UNKNOWN_VELOCITY_SIGMA * dtSeconds
        }
        variance += coastSigma * coastSigma

        // Measurement in local meters relative to the anchor.
        val measuredEast = (sample.longitude - referenceLongitude) * metersPerDegreeLongitude
        val measuredNorth = (sample.latitude - referenceLatitude) * METERS_PER_DEGREE_LATITUDE
        val innovationEast = measuredEast - east
        val innovationNorth = measuredNorth - north
        val innovation = hypot(innovationEast, innovationNorth)

        // Gate wild fixes: deweight rather than drop, so the filter can never
        // free-run forever; persistent disagreement re-anchors on reality.
        var effectiveVariance = measurementVariance
        val gateMeters = GATE_SIGMA * sqrt(variance + measurementVariance)
        if (innovation > gateMeters && innovation > MIN_GATE_METERS) {
            consecutiveOutliers += 1
            if (consecutiveOutliers >= MAX_CONSECUTIVE_OUTLIERS) {
                anchor(sample, measurementVariance)
                return sample
            }
            effectiveVariance *= OUTLIER_VARIANCE_INFLATION
        } else {
            consecutiveOutliers = 0
        }

        // Scalar Kalman correction.
        val gain = variance / (variance + effectiveVariance)
        east += gain * innovationEast
        north += gain * innovationNorth
        variance *= (1.0 - gain)

        rememberVelocity(sample)
        lastElapsedMillis = sample.elapsedMillis

        return sample.copy(
            latitude = referenceLatitude + north / METERS_PER_DEGREE_LATITUDE,
            longitude = referenceLongitude + east / metersPerDegreeLongitude,
        )
    }

    /** Trust the measurement outright and restart filtering from it. */
    private fun anchor(sample: LocationSample, measurementVariance: Double) {
        referenceLatitude = sample.latitude
        referenceLongitude = sample.longitude
        metersPerDegreeLongitude =
            (METERS_PER_DEGREE_LATITUDE * cos(sample.latitude * PI / 180.0))
                .coerceAtLeast(MIN_METERS_PER_DEGREE_LONGITUDE)
        east = 0.0
        north = 0.0
        variance = measurementVariance
        consecutiveOutliers = 0
        hasEstimate = true
        rememberVelocity(sample)
        lastElapsedMillis = sample.elapsedMillis
    }

    private fun rememberVelocity(sample: LocationSample) {
        val speed = sample.speedMetersPerSecond
        val heading = sample.headingDegrees
        if (speed != null && heading != null && speed.isFinite() && heading.isFinite() && speed >= 0.0) {
            val headingRadians = heading * PI / 180.0
            velocityEast = speed * sin(headingRadians)
            velocityNorth = speed * cos(headingRadians)
            hasVelocity = true
        } else {
            velocityEast = 0.0
            velocityNorth = 0.0
            hasVelocity = false
        }
    }

    companion object {
        /** Same flat-earth scale the trace math uses; ample at track extents. */
        private const val METERS_PER_DEGREE_LATITUDE = 111_320.0

        /** Longitude scale floor so polar latitudes can never divide by ~zero. */
        private const val MIN_METERS_PER_DEGREE_LONGITUDE = 1.0

        /** Reported accuracy is clamped into this band; default when absent. */
        private const val MIN_ACCURACY_METERS = 2.0
        private const val MAX_ACCURACY_METERS = 50.0
        private const val DEFAULT_ACCURACY_METERS = 12.0

        /** Elapsed gap beyond which fixes are treated as a new feed session. */
        private const val RESET_GAP_SECONDS = 3.0

        /** 1-sigma trust in Doppler velocity (chips report ~0.1 m/s; margin kept). */
        private const val VELOCITY_SIGMA = 0.5

        /** 1-sigma unmodeled acceleration between fixes (braking/turn-in). */
        private const val ACCELERATION_SIGMA = 4.0

        /** Coast uncertainty growth when the fix carried no Doppler velocity. */
        private const val UNKNOWN_VELOCITY_SIGMA = 15.0

        /** Innovation gate width, in combined standard deviations. */
        private const val GATE_SIGMA = 4.0

        /** Never gate disagreements smaller than this, whatever the variances. */
        private const val MIN_GATE_METERS = 8.0

        /** Variance multiplier applied to gated (distrusted) fixes. */
        private const val OUTLIER_VARIANCE_INFLATION = 25.0

        /** Consecutive gated fixes before conceding the world really moved. */
        private const val MAX_CONSECUTIVE_OUTLIERS = 3
    }
}
