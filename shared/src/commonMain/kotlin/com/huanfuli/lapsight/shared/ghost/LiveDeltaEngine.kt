package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample

/**
 * Realtime live-delta engine (skeleton).
 *
 * The full realtime delta math, suppression rules, and stale-clearing behavior
 * land in Task 3 of this plan. This skeleton exists so the ghost domain compiles
 * and the progress-curve tests can run in isolation.
 */
class LiveDeltaEngine(
    @Suppress("unused")
    private val maxHorizontalAccuracyMeters: Double =
        ProgressCurveBuilder.DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
) {

    var snapshot: LiveDeltaSnapshot =
        LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoCurrentLap)
        private set

    fun setReference(reference: ReferenceLap?) {
        // Implemented in Task 3.
    }

    fun startLap() {
        // Implemented in Task 3.
    }

    fun update(sample: LocationSample): LiveDeltaSnapshot = snapshot
}
