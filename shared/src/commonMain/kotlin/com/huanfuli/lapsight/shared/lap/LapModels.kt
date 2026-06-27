package com.huanfuli.lapsight.shared.lap

/**
 * Reason a candidate crossing was rejected by the engine. Surfaced for
 * diagnostics so test and UI can explain why a lap or sector did not register.
 */
enum class LapRejectReason {
    /** Horizontal accuracy worse than the configured maximum. */
    PoorAccuracy,

    /** Movement speed below the configured minimum. */
    TooSlow,

    /** Movement heading outside the direction tolerance. */
    WrongDirection,

    /** Crossing arrived within the cooldown window of the previous one. */
    Cooldown,

    /** Lap completed before the minimum lap duration elapsed. */
    BelowMinLapDuration,

    /** Sector crossed before any lap had started. */
    SectorBeforeLapStart,

    /** Sector already recorded for the current lap. */
    DuplicateSector,
}

/**
 * A completed lap.
 *
 * @param lapNumber one-based lap index.
 * @param startMillis interpolated start/finish crossing time that opened the lap.
 * @param endMillis interpolated start/finish crossing time that closed the lap.
 */
data class LapEvent(
    val lapNumber: Int,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * A recorded sector split within a lap.
 *
 * @param lapNumber lap this split belongs to.
 * @param sectorId sector line identifier.
 * @param sectorOrder sector ordering within the lap.
 * @param crossingMillis interpolated time the sector line was crossed.
 * @param splitMillis time from the lap start to this sector crossing.
 */
data class SectorEvent(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val crossingMillis: Long,
    val splitMillis: Long,
)

/**
 * A complete Sector INTERVAL result for one lap (D-06, D-11).
 *
 * Unlike the legacy line-centric [SectorEvent] (which carries only a cumulative
 * split at a single crossing), a [SectorResult] is the span between two adjacent
 * timing lines, carrying BOTH the adjacent-crossing [durationMillis] and the
 * separate [cumulativeSplitMillis] from the lap start. Sector 1 opens at the lap
 * crossing (so its duration equals its cumulative split); the final Sector closes
 * on the accepted start/finish crossing at the same timestamp as the lap (D-06).
 *
 * @param lapNumber one-based lap this interval belongs to.
 * @param sectorId stable identifier of the derived interval (e.g. "sector-1").
 * @param sectorOrder one-based ordering beginning at start/finish (D-11).
 * @param startedAtMillis interpolated time the interval opened (the prior accepted
 *   boundary crossing, or the lap start for Sector 1).
 * @param endedAtMillis interpolated time the closing crossing occurred.
 * @param durationMillis adjacent-crossing difference [endedAtMillis] - [startedAtMillis].
 * @param cumulativeSplitMillis time from the lap start to [endedAtMillis].
 */
data class SectorResult(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val cumulativeSplitMillis: Long,
)

/** Status of a single sector within the current lap. */
enum class SectorStatus {
    /** Not yet crossed in the current lap. */
    Pending,

    /** Crossed and timed in the current lap. */
    Crossed,

    /** A crossing was seen but rejected (e.g. duplicate). */
    Rejected,
}

/**
 * Per-sector timing state for the current lap. Resets at each new lap.
 */
data class SectorTimingState(
    val sectorId: String,
    val sectorName: String,
    val sectorOrder: Int,
    val status: SectorStatus = SectorStatus.Pending,
    val splitMillis: Long? = null,
    val lastRejectReason: LapRejectReason? = null,
)

/** High-level phase of the lap engine. */
enum class LapPhase {
    /** No valid start crossing yet; waiting to begin lap 1. */
    AwaitingStart,

    /** A lap is currently being timed. */
    Timing,
}

/**
 * Complete observable state of the lap engine after processing samples.
 *
 * UI renders directly from this; it never reimplements lap logic.
 */
data class LapTimingState(
    val phase: LapPhase = LapPhase.AwaitingStart,
    val lapCount: Int = 0,
    val currentLapNumber: Int? = null,
    val currentLapStartMillis: Long? = null,
    val currentLapElapsedMillis: Long? = null,
    val lastLapMillis: Long? = null,
    val bestLapMillis: Long? = null,
    val sectors: List<SectorTimingState> = emptyList(),
    val latestSector: SectorEvent? = null,
    val latestSectorResult: SectorResult? = null,
    val completedSectorResults: List<SectorResult> = emptyList(),
    val completedLaps: List<LapEvent> = emptyList(),
    val lastRejectReason: LapRejectReason? = null,
) {
    companion object {
        fun initial(course: CourseDefinition): LapTimingState = LapTimingState(
            sectors = course.orderedSectors.map {
                SectorTimingState(
                    sectorId = it.id,
                    sectorName = it.name,
                    sectorOrder = it.order,
                )
            },
        )
    }
}
