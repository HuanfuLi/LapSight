package com.huanfuli.lapsight.shared.glasses

import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.ghost.DeltaDisplayState
import com.huanfuli.lapsight.shared.ghost.DeltaTone
import com.huanfuli.lapsight.shared.lap.formatLapTime
import com.huanfuli.lapsight.shared.session.TimingRunSnapshot
import kotlin.math.roundToInt

/**
 * Ahead/behind caret shown beside the delta pill (D-03 iconic fallback — the DAT
 * display DSL exposes no arbitrary color, so ahead/behind is carried by caret
 * direction + the already-signed [TimingRunSnapshot.deltaDisplay] text, not hue).
 */
enum class DeltaCaret { Up, Down, None }

/**
 * Pure, SDK-free projection of a [TimingRunSnapshot] (+ [GlassesGpsState], active
 * [HudPage], and the sector-flash window) into every readout the glasses HUD
 * renders (MR-02) plus the D-13/D-14/D-15 non-timing states.
 *
 * Mirrors the `LapDashState.from(...)` + format-on-the-struct idiom
 * (`shared/.../lap/LapDashState.kt`): every field below is pre-formatted so
 * `androidApp`'s `HudRenderer` (a later plan) never touches lap/delta math —
 * it only lays out these strings/flags into the DAT flexBox DSL. Imports zero
 * `com.meta.wearable.*`/Compose types so it host-tests fast under
 * `:shared:testAndroidHostTest`.
 *
 * `HudModel.from` is called fresh every ~2 Hz beat by the (Android-only)
 * glasses bridge poll loop with a newly-polled [TimingRunSnapshot] — it never
 * holds state across calls. The D-15 "clock keeps advancing on a stale fix"
 * behavior therefore falls out of that polling: as long as the lap engine
 * keeps receiving samples the underlying [TimingRunSnapshot.currentLapMillis]
 * keeps advancing regardless of [isStaleFix]; this mapper deliberately never
 * freezes, blanks, or re-derives the clock itself — only [deltaText] and
 * [speedLabel] collapse to `--` when the fix is stale (D-15).
 *
 * @property page the active HUD page (D-01); the renderer decides which of the
 *   fields below are actually drawn for each page.
 * @property deltaText the delta pill's value text: a straight passthrough of
 *   [TimingRunSnapshot.deltaDisplay]'s already-signed text (never reformatted),
 *   collapsed to [DeltaDisplayState.UNAVAILABLE_TEXT] when [isStaleFix] (D-15)
 *   so a stale delta never reads as live.
 * @property deltaCaret ahead/behind caret (D-03): [DeltaTone.Faster] maps to
 *   [DeltaCaret.Down] (ahead), [DeltaTone.Slower] to [DeltaCaret.Up] (behind),
 *   neutral or stale to [DeltaCaret.None].
 * @property clockText the current-lap clock at TENTHS precision (D-05, e.g.
 *   `"1:23.4"`), or — inside the sector-flash window (D-04) — the sector split
 *   label occupying the same slot.
 * @property isSectorFlash true while [clockText] holds the sector-flash label
 *   instead of the running clock (D-04).
 * @property lastLapLabel / [bestLapLabel] standard `M:SS.mmm` labels, reused
 *   from [formatLapTime] — never re-derived.
 * @property speedLabel the unit-applied speed label (mirrors the phone's
 *   `DisplaySettings.speedUnit`), or `--` when unavailable or [isStaleFix].
 * @property lapCount raw lap count (TELEMETRY page, D-01).
 * @property isIdle true when no timing run is active (D-13) — the renderer
 *   shows the GPS-status idle screen ("Waiting for GPS" / "Ready — start
 *   timing", driven by [gpsReady]) instead of a timing page.
 * @property isStaleFix true while a run IS active but the phone's live GPS
 *   state is not Ready (D-15) — [speedLabel] and [deltaText] collapse to `--`
 *   plus a GPS warning glyph (renderer's concern); the clock is untouched.
 * @property isNeutralDelta true when no reference lap is selected (D-14) —
 *   [deltaText]/[deltaCaret] already read `--`/[DeltaCaret.None] in this case,
 *   but the flag lets the renderer keep the exact same pill geometry.
 * @property gpsReady the GPS readiness this model was built from (D-13).
 * @property gpsFixStatus the raw feed status used to distinguish no fix from a
 *   weak-but-present fix on the idle HUD.
 * @property gpsAccuracyMeters / [gpsSampleRateHz] raw GPS quality for the D-13
 *   idle screen's fix-state readout.
 */
data class HudModel(
    val page: HudPage,
    val deltaText: String,
    val deltaCaret: DeltaCaret,
    val clockText: String,
    val isSectorFlash: Boolean,
    val lastLapLabel: String,
    val bestLapLabel: String,
    val speedLabel: String,
    val lapCount: Int,
    val isIdle: Boolean,
    val isStaleFix: Boolean,
    val isNeutralDelta: Boolean,
    val gpsReady: Boolean,
    val gpsFixStatus: GpsFixStatus = GpsFixStatus.Idle,
    val gpsAccuracyMeters: Double?,
    val gpsSampleRateHz: Double?,
) {
    companion object {
        /**
         * Build the HUD projection for one render beat.
         *
         * @param run the freshly-polled timing snapshot (`SessionController.timingRunSnapshot()`).
         * @param gps the freshly-polled GPS/ready state ([GlassesGpsState]).
         * @param page the currently active HUD page (D-01).
         * @param nowEpochMs wall-clock now, used ONLY to evaluate the sector-flash window.
         * @param flashUntilEpochMs the sector-flash window end (D-04), or null/past when inactive.
         * @param speedUnit mirrors the phone's `DisplaySettings.speedUnit` so glasses/phone agree.
         */
        fun from(
            run: TimingRunSnapshot,
            gps: GlassesGpsState,
            page: HudPage,
            nowEpochMs: Long,
            flashUntilEpochMs: Long?,
            speedUnit: SpeedUnit = SpeedUnit.KilometersPerHour,
        ): HudModel {
            val isIdle = !run.isActive
            val isStaleFix = run.isActive && !gps.ready
            val isNeutralDelta = run.referenceLapMillis == null ||
                run.deltaDisplay.tone == DeltaTone.Neutral

            val inFlashWindow = flashUntilEpochMs != null &&
                nowEpochMs < flashUntilEpochMs &&
                run.latestSectorSplitMillis != null

            val clockText = if (inFlashWindow) {
                sectorFlashLabel(run.latestSectorName, run.latestSectorSplitMillis)
            } else {
                run.currentLapMillis.toTenthsClock()
            }

            val deltaText = if (isStaleFix) {
                DeltaDisplayState.UNAVAILABLE_TEXT
            } else {
                run.deltaDisplay.text
            }
            val deltaCaret = if (isStaleFix) {
                DeltaCaret.None
            } else {
                when (run.deltaDisplay.tone) {
                    DeltaTone.Faster -> DeltaCaret.Down
                    DeltaTone.Slower -> DeltaCaret.Up
                    DeltaTone.Neutral -> DeltaCaret.None
                }
            }

            val speedLabel = if (isStaleFix) {
                DeltaDisplayState.UNAVAILABLE_TEXT
            } else {
                run.speedMetersPerSecond?.let { formatSpeed(it, speedUnit) }
                    ?: DeltaDisplayState.UNAVAILABLE_TEXT
            }

            return HudModel(
                page = page,
                deltaText = deltaText,
                deltaCaret = deltaCaret,
                clockText = clockText,
                isSectorFlash = inFlashWindow,
                lastLapLabel = run.lastLapMillis.formatLapTime(),
                bestLapLabel = run.bestLapMillis.formatLapTime(),
                speedLabel = speedLabel,
                lapCount = run.lapCount,
                isIdle = isIdle,
                isStaleFix = isStaleFix,
                isNeutralDelta = isNeutralDelta,
                gpsReady = gps.ready,
                gpsFixStatus = gps.fixStatus,
                gpsAccuracyMeters = gps.accuracyMeters,
                gpsSampleRateHz = gps.sampleRateHz,
            )
        }

        /**
         * D-04 sector-flash label rendered in the clock slot. `TimingRunSnapshot`
         * exposes only the raw split duration (no per-sector delta-to-reference),
         * so — per RESEARCH "Don't Hand-Roll" (never re-derive delta math) — this
         * formats the raw split at tenths precision rather than inventing a
         * per-sector delta the engine does not compute.
         */
        private fun sectorFlashLabel(name: String?, splitMillis: Long?): String {
            val label = name ?: "Sector"
            return "$label ${splitMillis.toTenthsSplit()}"
        }

        private fun formatSpeed(metersPerSecond: Double, unit: SpeedUnit): String {
            val multiplier = when (unit) {
                SpeedUnit.KilometersPerHour -> 3.6
                SpeedUnit.MilesPerHour -> 2.2369362921
            }
            return (metersPerSecond * multiplier).roundToInt().toString()
        }
    }
}

/**
 * Tenths-precision current-lap clock (D-05, e.g. `"1:23.4"`) — a dedicated
 * truncation, never a second lap clock: it reads the SAME
 * [TimingRunSnapshot.currentLapMillis] the phone's `formatLapTime()` (ms
 * precision) formats.
 */
private fun Long?.toTenthsClock(): String {
    if (this == null) return "-:--.-"
    val totalMillis = if (this < 0) 0 else this
    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val tenths = (totalMillis % 1_000) / 100
    return "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
}

/** Tenths-precision split duration for the D-04 sector-flash label. */
private fun Long?.toTenthsSplit(): String {
    if (this == null) return "--.-s"
    val totalMillis = if (this < 0) 0 else this
    val seconds = totalMillis / 1_000
    val tenths = (totalMillis % 1_000) / 100
    return "+$seconds.${tenths}s"
}
