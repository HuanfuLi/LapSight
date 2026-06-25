package com.huanfuli.lapsight.shared.lap

import com.huanfuli.lapsight.shared.GpsFixStatus
import com.huanfuli.lapsight.shared.LocationSample
import kotlin.math.roundToInt

/**
 * Presentation-ready view of the lap engine for the mounted-phone dash.
 *
 * Pure data derived from [LapTimingState] plus the latest sample. The UI renders
 * directly from this and never reimplements lap logic. Label formatting lives
 * here so Android/iOS share identical strings.
 */
data class LapDashState(
    val isRunning: Boolean = false,
    val fixStatus: GpsFixStatus = GpsFixStatus.Idle,
    val courseName: String = "",
    val lapCount: Int = 0,
    val currentLapMillis: Long? = null,
    val lastLapMillis: Long? = null,
    val bestLapMillis: Long? = null,
    val latestSectorLabel: String? = null,
    val latestSectorSplitMillis: Long? = null,
    val sectorSummaries: List<SectorSummary> = emptyList(),
    val speedMetersPerSecond: Double? = null,
    val accuracyMeters: Double? = null,
) {
    val currentLapLabel: String get() = currentLapMillis.formatLapTime()
    val lastLapLabel: String get() = lastLapMillis.formatLapTime()
    val bestLapLabel: String get() = bestLapMillis.formatLapTime()
    val lapCountLabel: String get() = lapCount.toString()
    val latestSplitLabel: String get() = latestSectorSplitMillis.formatLapTime()

    val speedKmhLabel: String
        get() = speedMetersPerSecond?.let { (it * 3.6).roundToInt().toString() } ?: "--"

    val accuracyLabel: String
        get() = accuracyMeters?.let { it.roundToInt().toString() } ?: "--"

    companion object {
        fun idle(courseName: String): LapDashState =
            LapDashState(courseName = courseName, fixStatus = GpsFixStatus.Idle)

        /**
         * Build a dash state from engine output and the latest sample.
         */
        fun from(
            isRunning: Boolean,
            fixStatus: GpsFixStatus,
            courseName: String,
            timing: LapTimingState,
            latestSample: LocationSample?,
        ): LapDashState {
            val latest = timing.latestSector
            return LapDashState(
                isRunning = isRunning,
                fixStatus = fixStatus,
                courseName = courseName,
                lapCount = timing.lapCount,
                currentLapMillis = timing.currentLapElapsedMillis,
                lastLapMillis = timing.lastLapMillis,
                bestLapMillis = timing.bestLapMillis,
                latestSectorLabel = latest?.let { event ->
                    timing.sectors.firstOrNull { it.sectorId == event.sectorId }?.sectorName
                        ?: event.sectorId
                },
                latestSectorSplitMillis = latest?.splitMillis,
                sectorSummaries = timing.sectors.map {
                    SectorSummary(
                        id = it.sectorId,
                        name = it.sectorName,
                        status = it.status,
                        splitMillis = it.splitMillis,
                    )
                },
                speedMetersPerSecond = latestSample?.speedMetersPerSecond,
                accuracyMeters = latestSample?.horizontalAccuracyMeters,
            )
        }
    }
}

/** Compact per-sector readout for the dash. */
data class SectorSummary(
    val id: String,
    val name: String,
    val status: SectorStatus,
    val splitMillis: Long?,
) {
    val splitLabel: String get() = splitMillis.formatLapTime()
}

/**
 * Format milliseconds as M:SS.mmm (or --:--.--- when null). Shared so platform
 * UIs render identical strings.
 */
fun Long?.formatLapTime(): String {
    if (this == null) return "--:--.---"
    val totalMillis = if (this < 0) 0 else this
    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = totalMillis % 1_000
    val mm = minutes.toString().padStart(2, '0')
    val ss = seconds.toString().padStart(2, '0')
    val mmm = millis.toString().padStart(3, '0')
    return "$mm:$ss.$mmm"
}
