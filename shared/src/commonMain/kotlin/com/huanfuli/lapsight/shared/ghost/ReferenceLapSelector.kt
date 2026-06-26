package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.lap.LapEvent

/**
 * Pure selector that turns completed laps into [ReferenceLap]s and chooses the
 * fastest valid reference (D-01, D-02, D-12).
 *
 * It is clean-room shared Kotlin: it depends only on [LocationSample],
 * [LapEvent], and [ProgressCurveBuilder]. Source/Track boundary enforcement and
 * persistence live in the session/storage layers — this object only carries the
 * `isSimulated` flag through onto the produced [ReferenceLap] so callers can keep
 * real and simulated references isolated (D-04).
 */
object ReferenceLapSelector {

    /**
     * Build a [ReferenceLap] from a completed [lap] and the session's full
     * sample stream.
     *
     * The lap's raw samples are the subset of [allSamples] whose timestamps fall
     * within the lap window `[lap.startMillis, lap.endMillis]`. The progress curve
     * is built with [ProgressCurveBuilder]; if it cannot be built (too few/bad
     * samples, zero distance) this returns null so the caller suppresses rather
     * than crashes (T-04-01).
     *
     * @param isSimulated marks the produced reference as demo/simulated so it
     *   never crosses into real Track ghost state (D-04).
     */
    fun referenceFromLap(
        trackId: String,
        sessionId: String,
        lap: LapEvent,
        allSamples: List<LocationSample>,
        isSimulated: Boolean,
        maxHorizontalAccuracyMeters: Double =
            ProgressCurveBuilder.DEFAULT_MAX_HORIZONTAL_ACCURACY_METERS,
    ): ReferenceLap? {
        val window = allSamples.filter {
            it.elapsedMillis in lap.startMillis..lap.endMillis
        }
        val curve = when (val result = ProgressCurveBuilder.build(window, maxHorizontalAccuracyMeters)) {
            is ProgressCurveResult.Success -> result.curve
            is ProgressCurveResult.Failure -> return null
        }
        return ReferenceLap(
            trackId = trackId,
            sessionId = sessionId,
            lapNumber = lap.lapNumber,
            durationMillis = lap.durationMillis,
            isSimulated = isSimulated,
            rawSamples = window,
            progressCurve = curve,
        )
    }

    /**
     * Returns the faster of two references, preferring the existing [current] on
     * ties so an equal new lap does not churn the active reference (D-12). A null
     * argument is treated as "no reference".
     */
    fun fasterOf(current: ReferenceLap?, candidate: ReferenceLap?): ReferenceLap? {
        if (current == null) return candidate
        if (candidate == null) return current
        return if (candidate.durationMillis < current.durationMillis) candidate else current
    }
}
