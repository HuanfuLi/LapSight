package com.huanfuli.lapsight.shared.review

/**
 * Lap × Sector table model for Timing Session Review (SESS-02, D-32).
 *
 * Presents laps as rows and Sectors as columns — the motorsport timing-screen
 * shape (one row per lap; sector times sum to the lap time; a BEST row of
 * per-column bests and an OPT row with the optimal lap built from them).
 *
 * The V2 [LapSectorTableMode.CompleteSectors] mode renders complete Sector
 * interval durations from [ReviewCompleteSector]. The V1
 * [LapSectorTableMode.LegacyCumulative] mode renders the preserved cumulative
 * line splits from [ReviewSectorSplit]; those are NEVER relabeled as interval
 * durations (D-06, D-11), so legacy tables carry no optimal lap.
 */
enum class LapSectorTableMode {
    /** V2: cells are complete Sector interval durations that sum to the lap time. */
    CompleteSectors,

    /** V1 history: cells are cumulative splits from the lap start to each line. */
    LegacyCumulative,

    /** No sector data: rows carry only lap number and lap time. */
    LapsOnly,
}

/** One Sector column: identity, ordering, and the short "S1"-style header. */
data class LapSectorColumn(
    val sectorId: String,
    val sectorOrder: Int,
    val label: String,
    val name: String,
)

/**
 * One lap row. [cells] align with [LapSectorTableModel.columns]; a null cell
 * means the Sector was not observed on that lap (e.g. GPS outage).
 */
data class LapSectorTableRow(
    val lapNumber: Int,
    val cells: List<Long?>,
    val lapMillis: Long,
)

/**
 * Presentation-ready lap × sector table.
 *
 * [columnBests] aligns with [columns] (null when a column has no observed
 * value). [optimalLapMillis] is the GT7-style "OPT" lap — the sum of the best
 * observed duration in every Sector column — and is only present in
 * [LapSectorTableMode.CompleteSectors] mode with full column coverage.
 */
data class LapSectorTableModel(
    val mode: LapSectorTableMode,
    val columns: List<LapSectorColumn>,
    val rows: List<LapSectorTableRow>,
    val columnBests: List<Long?>,
    val bestLapMillis: Long?,
    val optimalLapMillis: Long?,
)

/**
 * Builds the lap × sector table from a review summary's parts.
 *
 * Prefers V2 [completeSectors]; falls back to V1 [legacySplits]; degrades to a
 * laps-only table when neither exists. Rows are ordered by lap number and
 * columns by Sector order.
 */
fun buildLapSectorTable(
    laps: List<ReviewLapRow>,
    completeSectors: List<ReviewCompleteSector>,
    legacySplits: List<ReviewSectorSplit>,
): LapSectorTableModel {
    val orderedLaps = laps.sortedBy { it.lapNumber }
    val mode = when {
        completeSectors.isNotEmpty() -> LapSectorTableMode.CompleteSectors
        legacySplits.isNotEmpty() -> LapSectorTableMode.LegacyCumulative
        else -> LapSectorTableMode.LapsOnly
    }

    data class SectorKey(val sectorId: String, val sectorOrder: Int, val name: String)

    val keys: List<SectorKey> = when (mode) {
        LapSectorTableMode.CompleteSectors -> completeSectors
            .map { SectorKey(it.sectorId, it.sectorOrder, it.sectorName) }
        LapSectorTableMode.LegacyCumulative -> legacySplits
            .map { SectorKey(it.sectorId, it.sectorOrder, it.sectorName) }
        LapSectorTableMode.LapsOnly -> emptyList()
    }.distinctBy { it.sectorId }.sortedBy { it.sectorOrder }

    val columns = keys.mapIndexed { index, key ->
        LapSectorColumn(
            sectorId = key.sectorId,
            sectorOrder = key.sectorOrder,
            label = "S${index + 1}",
            name = key.name,
        )
    }

    fun cellFor(lapNumber: Int, sectorId: String): Long? = when (mode) {
        LapSectorTableMode.CompleteSectors -> completeSectors
            .firstOrNull { it.lapNumber == lapNumber && it.sectorId == sectorId }
            ?.durationMillis
        LapSectorTableMode.LegacyCumulative -> legacySplits
            .firstOrNull { it.lapNumber == lapNumber && it.sectorId == sectorId }
            ?.splitMillis
        LapSectorTableMode.LapsOnly -> null
    }

    val rows = orderedLaps.map { lap ->
        LapSectorTableRow(
            lapNumber = lap.lapNumber,
            cells = columns.map { cellFor(lap.lapNumber, it.sectorId) },
            lapMillis = lap.durationMillis,
        )
    }

    val columnBests = columns.indices.map { columnIndex ->
        rows.mapNotNull { it.cells[columnIndex] }.minOrNull()
    }

    val bestLapMillis = rows.minOfOrNull { it.lapMillis }

    // OPT (optimal lap) is only meaningful for complete Sector interval
    // durations, and only when every column has at least one observed value.
    val optimalLapMillis = if (
        mode == LapSectorTableMode.CompleteSectors &&
        columnBests.isNotEmpty() &&
        columnBests.none { it == null }
    ) {
        columnBests.filterNotNull().sum()
    } else {
        null
    }

    return LapSectorTableModel(
        mode = mode,
        columns = columns,
        rows = rows,
        columnBests = columnBests,
        bestLapMillis = bestLapMillis,
        optimalLapMillis = optimalLapMillis,
    )
}

/**
 * Compact sector-cell time: `SS.mmm` under a minute, `M:SS.mmm` above —
 * narrower than the zero-padded lap clock so sector columns stay dense.
 */
fun Long.formatSectorTime(): String {
    val totalMillis = if (this < 0) 0 else this
    val minutes = totalMillis / 60_000
    val seconds = (totalMillis % 60_000) / 1_000
    val millis = (totalMillis % 1_000).toString().padStart(3, '0')
    return if (minutes > 0) {
        "$minutes:${seconds.toString().padStart(2, '0')}.$millis"
    } else {
        "$seconds.$millis"
    }
}
