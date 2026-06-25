package com.huanfuli.lapsight.shared.lap

/**
 * Tuning for the lap engine's false-positive filters.
 *
 * All thresholds are conservative defaults suitable for phone GPS at track
 * scale. They can be relaxed in tests (for example by disabling the direction
 * gate) to isolate behavior.
 *
 * @param minLapDurationMillis minimum time after a lap starts before a
 *   start/finish crossing may complete it. Blocks duplicate laps from jitter.
 * @param crossingCooldownMillis minimum time between two accepted crossings of
 *   the same line. Blocks rapid re-triggering near a line.
 * @param maxHorizontalAccuracyMeters worst horizontal accuracy (meters) still
 *   accepted. Samples with accuracy numerically larger than this are rejected.
 *   `null` disables the accuracy gate.
 * @param minSpeedMetersPerSecond minimum movement speed for a crossing to count.
 *   Blocks crossings while effectively stationary.
 * @param enforceDirection when false, the direction gate is disabled (useful in
 *   tests and for symmetric demo courses). When true, a start/finish crossing is
 *   accepted only if it approaches the line from the same side as the first
 *   accepted crossing (a robust, heading-noise-tolerant half-plane check).
 */
data class LapEngineConfig(
    val minLapDurationMillis: Long = DEFAULT_MIN_LAP_DURATION_MILLIS,
    val crossingCooldownMillis: Long = DEFAULT_CROSSING_COOLDOWN_MILLIS,
    val maxHorizontalAccuracyMeters: Double? = DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    val minSpeedMetersPerSecond: Double = DEFAULT_MIN_SPEED_METERS_PER_SECOND,
    val enforceDirection: Boolean = true,
) {
    init {
        require(minLapDurationMillis >= 0) { "minLapDurationMillis must be >= 0" }
        require(crossingCooldownMillis >= 0) { "crossingCooldownMillis must be >= 0" }
        require(minSpeedMetersPerSecond >= 0) { "minSpeedMetersPerSecond must be >= 0" }
        maxHorizontalAccuracyMeters?.let {
            require(it > 0) { "maxHorizontalAccuracyMeters must be > 0 when set" }
        }
    }

    companion object {
        const val DEFAULT_MIN_LAP_DURATION_MILLIS: Long = 8_000
        const val DEFAULT_CROSSING_COOLDOWN_MILLIS: Long = 3_000
        const val DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS: Double = 25.0
        const val DEFAULT_MIN_SPEED_METERS_PER_SECOND: Double = 2.0

        /** Permissive config for tests: no direction gate, low thresholds. */
        fun lenientForTests(): LapEngineConfig = LapEngineConfig(
            minLapDurationMillis = 0,
            crossingCooldownMillis = 0,
            maxHorizontalAccuracyMeters = null,
            minSpeedMetersPerSecond = 0.0,
            enforceDirection = false,
        )
    }
}
