package com.huanfuli.lapsight.shared.ui.drive

import com.huanfuli.lapsight.shared.LocationSample

/**
 * Display-only smoothing for the live moving dot and speed readout.
 *
 * Phone GNSS solves fixes at roughly 1 Hz, so the raw dot visibly jumps once a
 * second. RaceChrono's own guidance is that positional accuracy matters more than
 * update rate and that the display should interpolate between fixes; this does the
 * display half of that.
 *
 * It is a pure, constant-velocity predictor over the last two fixes:
 * - between the two fixes it interpolates,
 * - just past the latest fix it extrapolates along the same velocity, capped at
 *   [MAX_EXTRAPOLATION_MILLIS] so a stale fix never lets the dot fly off.
 *
 * It is deliberately NOT fed into [com.huanfuli.lapsight.shared.lap.LapEngine] or
 * the recorder: raw fixes remain the single source of truth for timing and
 * storage. Lap-crossing time is already interpolated inside the engine.
 */
object LiveTrackPredictor {

    /** Never project more than this far past the latest fix (milliseconds). */
    const val MAX_EXTRAPOLATION_MILLIS: Long = 1_500L

    /**
     * A smoothed position for rendering at [atElapsedMillis].
     *
     * Speed/heading are carried from the latest fix (falling back to the previous)
     * rather than re-derived, since the receiver's own values are more accurate
     * than differencing two noisy positions.
     */
    data class PredictedFix(
        val latitude: Double,
        val longitude: Double,
        val speedMetersPerSecond: Double?,
        val headingDegrees: Double?,
    )

    /**
     * Predict a display position at [atElapsedMillis] from the last two fixes.
     *
     * With no usable segment (missing [previous], zero/negative time delta, or the
     * two fixes out of order) this returns [latest] unchanged — a safe passthrough.
     */
    fun predict(
        previous: LocationSample?,
        latest: LocationSample,
        atElapsedMillis: Long,
    ): PredictedFix {
        val speed = latest.speedMetersPerSecond ?: previous?.speedMetersPerSecond
        val heading = latest.headingDegrees ?: previous?.headingDegrees

        val segmentMillis = if (previous != null) latest.elapsedMillis - previous.elapsedMillis else 0L
        if (previous == null || segmentMillis <= 0L) {
            return PredictedFix(latest.latitude, latest.longitude, speed, heading)
        }

        // Per-millisecond velocity across the last segment, in lat/lon degrees.
        val latPerMillis = (latest.latitude - previous.latitude) / segmentMillis
        val lonPerMillis = (latest.longitude - previous.longitude) / segmentMillis

        // Offset from the latest fix. Clamp interpolation to no earlier than the
        // previous fix, and extrapolation to the capped horizon.
        val offsetMillis = (atElapsedMillis - latest.elapsedMillis)
            .coerceIn(-segmentMillis, MAX_EXTRAPOLATION_MILLIS)

        return PredictedFix(
            latitude = latest.latitude + latPerMillis * offsetMillis,
            longitude = latest.longitude + lonPerMillis * offsetMillis,
            speedMetersPerSecond = speed,
            headingDegrees = heading,
        )
    }

    /**
     * Convenience over a sample history: predicts from the last two samples, or
     * returns null when the history is empty.
     */
    fun predict(history: List<LocationSample>, atElapsedMillis: Long): PredictedFix? {
        val latest = history.lastOrNull() ?: return null
        val previous = history.getOrNull(history.size - 2)
        return predict(previous, latest, atElapsedMillis)
    }
}
