package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.lap.GeoPoint
import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import kotlin.math.sqrt

/**
 * Pure realtime live-delta engine (D-08/D-09/D-10).
 *
 * Streams current-lap [LocationSample]s, accumulates current progress distance,
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
 * @param maxProgressOverrunFraction how far current progress may exceed the
 *        reference total distance before it is considered unmatchable. Progress
 *        within this margin is clamped to the reference range; beyond it the delta
 *        is suppressed as [DeltaUnavailableReason.UnmatchedProgress].
 */
class LiveDeltaEngine(
    private val maxHorizontalAccuracyMeters: Double =
        ProgressCurveBuilder.DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    private val maxProgressOverrunFraction: Double = DEFAULT_MAX_PROGRESS_OVERRUN_FRACTION,
) {

    private var reference: ReferenceLap? = null

    private var lapActive: Boolean = false
    private var projection: LocalProjection? = null
    private var lastLocal: LocalPoint? = null
    private var lapStartMillis: Long? = null
    private var accumulatedMeters: Double = 0.0
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
        projection = null
        lastLocal = null
        lapStartMillis = null
        accumulatedMeters = 0.0
        sampleCount = 0
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

        val geo = GeoPoint(sample.latitude, sample.longitude)
        val proj = projection ?: LocalProjection(geo).also { projection = it }
        val local = proj.toLocal(geo)

        // First usable sample of the lap establishes the origin and start time.
        if (lapStartMillis == null) {
            lapStartMillis = sample.elapsedMillis
            lastLocal = local
            accumulatedMeters = 0.0
            sampleCount = 1
            return emit(
                LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.InsufficientCurrentSamples),
            )
        }

        val prev = lastLocal
        if (prev != null) {
            val dx = local.x - prev.x
            val dy = local.y - prev.y
            accumulatedMeters += sqrt(dx * dx + dy * dy)
        }
        lastLocal = local
        sampleCount++

        val currentElapsed = sample.elapsedMillis - lapStartMillis!!
        val progress = accumulatedMeters
        val overrunLimit = curve.totalDistanceMeters * (1.0 + maxProgressOverrunFraction)
        if (progress > overrunLimit) {
            return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.UnmatchedProgress))
        }

        val clamped = progress.coerceIn(0.0, curve.totalDistanceMeters)
        val referenceElapsed = curve.elapsedAtProgress(clamped)
            ?: return emit(LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.InsufficientReference))

        return emit(
            LiveDeltaSnapshot.Available(
                deltaMillis = currentElapsed - referenceElapsed,
                currentElapsedMillis = currentElapsed,
                referenceElapsedMillis = referenceElapsed,
                progressMeters = clamped,
                normalizedProgress = clamped / curve.totalDistanceMeters,
            ),
        )
    }

    private fun emit(next: LiveDeltaSnapshot): LiveDeltaSnapshot {
        snapshot = next
        return next
    }

    companion object {
        /** Default tolerance (25%) for current progress overshooting the reference. */
        const val DEFAULT_MAX_PROGRESS_OVERRUN_FRACTION: Double = 0.25
    }
}
