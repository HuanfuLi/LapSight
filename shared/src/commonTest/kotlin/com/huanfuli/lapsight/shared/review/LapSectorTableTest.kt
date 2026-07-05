package com.huanfuli.lapsight.shared.review

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Lap × Sector table model coverage (SESS-02, D-32): V2 complete-Sector rows
 * whose cells sum to the lap time, column bests, the OPT (optimal) lap, the V1
 * legacy cumulative fallback that never infers intervals (D-06, D-11), and the
 * laps-only degradation.
 */
class LapSectorTableTest {

    private fun sector(
        lap: Int,
        order: Int,
        duration: Long,
        cumulative: Long,
        id: String = "sec-$order",
        name: String = "Sector ${order + 1}",
    ) = ReviewCompleteSector(
        sectorId = id,
        sectorName = name,
        sectorOrder = order,
        lapNumber = lap,
        startedAtMillis = cumulative - duration,
        endedAtMillis = cumulative,
        durationMillis = duration,
        cumulativeSplitMillis = cumulative,
    )

    private val laps = listOf(
        ReviewLapRow(lapNumber = 1, durationMillis = 100_000L),
        ReviewLapRow(lapNumber = 2, durationMillis = 95_000L),
        ReviewLapRow(lapNumber = 3, durationMillis = 98_000L),
    )

    // Lap 1: 32 + 40 + 28 = 100s; lap 2: 31 + 38 + 26 = 95s; lap 3: 30 + 41 + 27 = 98s.
    private val completeSectors = listOf(
        sector(lap = 1, order = 0, duration = 32_000L, cumulative = 32_000L),
        sector(lap = 1, order = 1, duration = 40_000L, cumulative = 72_000L),
        sector(lap = 1, order = 2, duration = 28_000L, cumulative = 100_000L),
        sector(lap = 2, order = 0, duration = 31_000L, cumulative = 31_000L),
        sector(lap = 2, order = 1, duration = 38_000L, cumulative = 69_000L),
        sector(lap = 2, order = 2, duration = 26_000L, cumulative = 95_000L),
        sector(lap = 3, order = 0, duration = 30_000L, cumulative = 30_000L),
        sector(lap = 3, order = 1, duration = 41_000L, cumulative = 71_000L),
        sector(lap = 3, order = 2, duration = 27_000L, cumulative = 98_000L),
    )

    @Test
    fun v2TableRowsCarrySectorDurationsThatSumToTheLapTime() {
        val table = buildLapSectorTable(laps, completeSectors, legacySplits = emptyList())

        assertEquals(LapSectorTableMode.CompleteSectors, table.mode)
        assertEquals(listOf("S1", "S2", "S3"), table.columns.map { it.label })
        assertEquals(3, table.rows.size)
        table.rows.forEach { row ->
            assertEquals(
                row.lapMillis,
                row.cells.filterNotNull().sum(),
                "lap ${row.lapNumber}: sector durations must sum to the lap time",
            )
        }
    }

    @Test
    fun v2TableExposesColumnBestsBestLapAndOptimalLap() {
        val table = buildLapSectorTable(laps, completeSectors, legacySplits = emptyList())

        assertEquals(listOf<Long?>(30_000L, 38_000L, 26_000L), table.columnBests)
        assertEquals(95_000L, table.bestLapMillis)
        // OPT = sum of best sectors: 30 + 38 + 26 = 94s, faster than any real lap.
        assertEquals(94_000L, table.optimalLapMillis)
        assertTrue(table.optimalLapMillis!! <= table.bestLapMillis!!)
    }

    @Test
    fun missingSectorRendersNullCellAndIsExcludedFromColumnBest() {
        // Lap 2 never observed sector 2 (GPS outage).
        val withGap = completeSectors.filterNot { it.lapNumber == 2 && it.sectorOrder == 1 }

        val table = buildLapSectorTable(laps, withGap, legacySplits = emptyList())

        val lap2 = table.rows.first { it.lapNumber == 2 }
        assertNull(lap2.cells[1], "unobserved sector must render as a missing cell")
        assertEquals(40_000L, table.columnBests[1], "column best must skip missing cells")
        assertEquals(30_000L + 40_000L + 26_000L, table.optimalLapMillis)
    }

    @Test
    fun partialColumnCoverageStillBuildsOptimalFromObservedBests() {
        // Sector 2 observed on lap 1 only; sector 3 never observed (so it never
        // becomes a column). Every existing column has a best -> OPT still builds.
        val partial = completeSectors.filter { it.sectorOrder == 0 } +
            listOf(sector(lap = 1, order = 1, duration = 40_000L, cumulative = 72_000L))
        val table = buildLapSectorTable(laps, partial, legacySplits = emptyList())

        // Both present columns have bests -> OPT is their sum (partial coverage of
        // columns is fine; only a best-less column suppresses OPT).
        assertEquals(30_000L + 40_000L, table.optimalLapMillis)
    }

    @Test
    fun legacyCumulativeSplitsRenderWithoutInferredIntervalsOrOptimalLap() {
        val legacy = listOf(
            ReviewSectorSplit("S1", "Sector 1", 0, lapNumber = 1, splitMillis = 15_000L),
            ReviewSectorSplit("S2", "Sector 2", 1, lapNumber = 1, splitMillis = 30_000L),
            ReviewSectorSplit("S1", "Sector 1", 0, lapNumber = 2, splitMillis = 14_000L),
            ReviewSectorSplit("S2", "Sector 2", 1, lapNumber = 2, splitMillis = 29_000L),
        )
        val twoLaps = laps.take(2)

        val table = buildLapSectorTable(twoLaps, completeSectors = emptyList(), legacySplits = legacy)

        assertEquals(LapSectorTableMode.LegacyCumulative, table.mode)
        // Cells are the preserved cumulative splits, unchanged (D-06, D-11).
        assertEquals(listOf<Long?>(15_000L, 30_000L), table.rows[0].cells)
        assertEquals(listOf<Long?>(14_000L, 29_000L), table.rows[1].cells)
        assertNull(table.optimalLapMillis, "legacy cumulative splits must never build an OPT lap")
        assertEquals(listOf<Long?>(14_000L, 29_000L), table.columnBests)
    }

    @Test
    fun noSectorDataDegradesToLapsOnlyTable() {
        val table = buildLapSectorTable(laps, completeSectors = emptyList(), legacySplits = emptyList())

        assertEquals(LapSectorTableMode.LapsOnly, table.mode)
        assertTrue(table.columns.isEmpty())
        assertEquals(3, table.rows.size)
        assertEquals(95_000L, table.bestLapMillis)
        assertNull(table.optimalLapMillis)
    }

    @Test
    fun rowsAreOrderedByLapNumberRegardlessOfInputOrder() {
        val shuffled = listOf(laps[2], laps[0], laps[1])
        val table = buildLapSectorTable(shuffled, completeSectors, legacySplits = emptyList())

        assertEquals(listOf(1, 2, 3), table.rows.map { it.lapNumber })
    }

    @Test
    fun sectorTimeFormatsCompactUnderAMinuteAndClockStyleAbove() {
        assertEquals("32.104", 32_104L.formatSectorTime())
        assertEquals("5.204", 5_204L.formatSectorTime())
        assertEquals("1:02.104", 62_104L.formatSectorTime())
        assertEquals("0.000", 0L.formatSectorTime())
        assertEquals("0.000", (-50L).formatSectorTime())
    }
}
