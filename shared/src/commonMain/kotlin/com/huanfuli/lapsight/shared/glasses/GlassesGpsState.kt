package com.huanfuli.lapsight.shared.glasses

import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.session.ReadyThresholds

/**
 * Platform-free GPS/ready snapshot for the glasses HUD's pre-timing idle screen
 * (D-13, Phase 7 MR-01).
 *
 * The Drive dash already computes an equivalent Ready preview via
 * `dashReadyState`/`gpsQualityWarning`
 * (`shared/.../ui/drive/DriveTelemetry.kt:183-234`), but that reads a
 * `DriveMarkingSnapshot` that is only ever produced inside a `DriveMarkingController`
 * remembered by Compose (`DriveScreen.kt:79-81`) — not reachable from the
 * Android-only glasses bridge outside Compose. [GlassesGpsState] is the
 * platform-free fallback the RESEARCH KMP-seam open question resolves to: the
 * SAME [ReadyThresholds] the dash uses, applied over whatever fix
 * status/accuracy/rate the caller currently has, with zero Compose/DAT import so
 * it stays host-testable next to [HudModel].
 *
 * @property fixStatus the current GPS fix status.
 * @property accuracyMeters latest horizontal accuracy, or null when unknown.
 * @property sampleRateHz recent update cadence, or null when unknown.
 * @property ready true when [fixStatus] is a usable live fix and both quality
 *   inputs are within [ReadyThresholds] — mirrors the dash's conservative Ready
 *   preview (accuracy + rate), not the full aggregate gate (which also needs a
 *   selected course).
 */
data class GlassesGpsState(
    val fixStatus: GpsFixStatus,
    val accuracyMeters: Double?,
    val sampleRateHz: Double?,
    val ready: Boolean,
) {
    companion object {
        /** No feed observed yet — the neutral pre-cast state (D-13 "Waiting for GPS"). */
        fun idle(): GlassesGpsState = GlassesGpsState(
            fixStatus = GpsFixStatus.Idle,
            accuracyMeters = null,
            sampleRateHz = null,
            ready = false,
        )

        /**
         * Derive [ready] from a live fix status + quality using
         * [ReadyThresholds.Default] (25 m accuracy / 0.9 Hz, matching the dash).
         *
         * A [GpsFixStatus.Live] or [GpsFixStatus.Simulated] fix with accuracy and
         * sample rate inside the thresholds is Ready; anything else (no fix,
         * acquiring, degraded, unavailable, or out-of-threshold quality) is not —
         * the same conservative posture as `gpsQualityWarning` in
         * `DriveTelemetry.kt`.
         */
        fun from(
            fixStatus: GpsFixStatus,
            accuracyMeters: Double?,
            sampleRateHz: Double?,
            thresholds: ReadyThresholds = ReadyThresholds.Default,
        ): GlassesGpsState {
            val hasUsableFix = fixStatus == GpsFixStatus.Live || fixStatus == GpsFixStatus.Simulated
            val accuracyOk = accuracyMeters != null && accuracyMeters.isFinite() &&
                accuracyMeters >= 0.0 && accuracyMeters <= thresholds.maxHorizontalAccuracyMeters
            val rateOk = sampleRateHz != null && sampleRateHz.isFinite() &&
                sampleRateHz >= thresholds.minSampleRateHz
            return GlassesGpsState(
                fixStatus = fixStatus,
                accuracyMeters = accuracyMeters,
                sampleRateHz = sampleRateHz,
                ready = hasUsableFix && accuracyOk && rateOk,
            )
        }
    }
}
