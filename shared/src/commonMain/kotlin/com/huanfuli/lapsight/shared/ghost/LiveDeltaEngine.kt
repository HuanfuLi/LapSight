package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample

/**
 * Pure realtime live-delta engine (D-08/D-09/D-10).
 *
 * Streams current-lap [LocationSample]s, matches direction-relative course progress,
 * looks up the equivalent reference elapsed time on the loaded [ReferenceLap]'s
 * progress curve, and emits a [LiveDeltaSnapshot]:
 *
 * - `deltaMillis = currentElapsedMillis - referenceElapsedMillis`
 * - positive delta means slower than reference, negative means faster (D-10).
 *
 * The engine is UI- and platform-independent. It never throws for bad GPS input;
 * low-confidence conditions resolve to [LiveDeltaSnapshot.Unavailable], and every
 * update overwrites [snapshot] so a stale delta is never reused (D-11/D-18).
 *
 * @param maxHorizontalAccuracyMeters current samples worse than this are treated
 *        as poor GPS quality and suppress the delta.
 * @param courseMatcher exact selected-course matcher. A missing matcher suppresses
 *        Ghost delta rather than falling back to traveled-distance accumulation.
 */
class LiveDeltaEngine(
    private val maxHorizontalAccuracyMeters: Double =
        ProgressCurveBuilder.DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    private val courseMatcher: CourseProgressMatcher? = null,
) {

    private var reference: ReferenceLap? = null

    private var lapActive: Boolean = false
    private var lapStartMillis: Long? = null
    private var sampleCount: Int = 0

    /** Latest emitted snapshot; cleared to an [LiveDeltaSnapshot.Unavailable] on suppression. */
    var snapshot: LiveDeltaSnapshot =
        LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoCurrentLap)
        private set

    /** Load (or clear) the reference lap used as the ghost target. */
    fun setReference(reference: ReferenceLap?) {
        this.reference = reference
    }

    /**
     * Begin a new current lap. Resets accumulated progress and clears any prior
     * delta so the next lap chases the reference from scratch (D-08).
     */
    fun startLap() {
        lapActive = true
        lapStartMillis = null
        sampleCount = 0
        courseMatcher?.reset()
        snapshot = LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.InsufficientCurrentSamples)
    }

    /** Stop timing the current lap; subsequent updates report no current lap. */
    fun stopLap() {
        lapActive = false
        snapshot = LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoCurrentLap)
    }

    /**
     * Feed one current-lap sample and return the resulting [LiveDeltaSnapshot].
     * The returned value is always also stored in [snapshot].
     */
    fun update(sample: LocationSample): LiveDeltaSnapshot {
        if (!lapActive) return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoCurrentLap))

        val ref = reference
            ?: return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoReference))

        val curve = ref.progressCurve
        if (curve.points.size < 2 || curve.totalDistanceMeters <= 0.0) {
            return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.InsufficientReference))
        }

        val accuracy = sample.horizontalAccuracyMeters
        if (accuracy != null && accuracy > maxHorizontalAccuracyMeters) {
            return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.PoorGpsQuality))
        }
        if (!sample.latitude.isFinite() || !sample.longitude.isFinite()) {
            return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.PoorGpsQuality))
        }

        // The lap clock is independent of match confidence. Even an unmatched first
        // sample establishes elapsed time so rematch never restarts timing.
        if (lapStartMillis == null) {
            lapStartMillis = sample.elapsedMillis
        }
        sampleCount++

        val match = courseMatcher?.match(sample, ref.compatibilityKey)
            ?: return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.UnmatchedProgress))
        if (match is CourseMatchResult.Unmatched) {
            return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.UnmatchedProgress))
        }
        match as CourseMatchResult.Matched

        if (sampleCount < 2) {
            return emit(
                LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.InsufficientCurrentSamples),
            )
        }

        val currentElapsed = sample.elapsedMillis - lapStartMillis!!
        val referenceProgress = curve.totalDistanceMeters * match.normalizedProgress
        val referenceElapsed = curve.elapsedAtProgress(referenceProgress)
            ?: return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.InsufficientReference))

        return emit(
            LiveDeltaSnapshot.Available(
                deltaMillis = currentElapsed - referenceElapsed,
                currentElapsedMillis = currentElapsed,
                referenceElapsedMillis = referenceElapsed,
                progressMeters = match.directionProgressMeters,
                normalizedProgress = match.normalizedProgress,
            ),
        )
    }

    private fun emit(next: LiveDeltaSnapshot): LiveDeltaSnapshot {
        snapshot = next
        return next
    }
}
