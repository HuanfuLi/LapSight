package com.huanfuli.lapsight.shared.lap

/**
 * A timing line defined by two geographic points (V0 contract).
 *
 * The line is treated as the finite segment between [pointA] and [pointB], not
 * an infinite line. A vehicle "crosses" the line when the segment connecting
 * two consecutive samples intersects this segment.
 */
data class StartFinishLine(
    val pointA: GeoPoint,
    val pointB: GeoPoint,
)

/**
 * A sector line. Uses the same two-point geometry as [StartFinishLine] but
 * carries a stable ordering and identity so per-lap split state can be tracked.
 *
 * @param id stable identifier, unique within a course (e.g. "S1").
 * @param name human-readable label shown in the dash (e.g. "Sector 1").
 * @param order zero-based ordering of the sector within a lap. Sectors are
 *   expected to be crossed in ascending [order] during a valid lap.
 */
data class SectorLine(
    val id: String,
    val name: String,
    val order: Int,
    val pointA: GeoPoint,
    val pointB: GeoPoint,
)

/**
 * A complete Sector INTERVAL derived from the course's intermediate boundaries
 * (D-06, D-11).
 *
 * Unlike a [SectorLine] (a single line you cross), a [SectorDefinition] is the
 * span between two adjacent timing lines. For `N` configured Sectors there are
 * `N - 1` intermediate boundaries; the engine derives `N` intervals that cover
 * the whole closed lap:
 *
 * ```
 * Start/Finish -> [Sector 1] -> B1 -> [Sector 2] -> ... -> [Sector N] -> Start/Finish
 * ```
 *
 * Sector order begins at the start/finish (D-11): Sector 1 opens at the lap
 * crossing and closes at the first boundary; the final Sector closes back on the
 * accepted start/finish crossing.
 *
 * @param sectorId stable identifier for the interval (e.g. "sector-1").
 * @param sectorOrder one-based ordering beginning at start/finish (D-11).
 * @param closingBoundary the intermediate boundary that closes this interval, or
 *   null for the final Sector which closes on the start/finish line (D-06).
 */
data class SectorDefinition(
    val sectorId: String,
    val sectorOrder: Int,
    val closingBoundary: SectorLine?,
)

/**
 * A full course definition for a demo/replay session: one start/finish line and
 * an ordered list of intermediate boundary lines.
 *
 * The [sectors] list carries the intermediate boundaries (the lines you cross
 * mid-lap). Complete Sector INTERVALS are derived from them via [derivedSectors]:
 * `M` boundaries yield `M + 1` complete intervals, and an empty boundary list
 * (Sectors disabled) yields no intervals (D-07).
 */
data class CourseDefinition(
    val startFinish: StartFinishLine,
    val sectors: List<SectorLine> = emptyList(),
) {
    /** Intermediate boundaries sorted by their declared [SectorLine.order]. */
    val orderedSectors: List<SectorLine>
        get() = sectors.sortedBy { it.order }

    /**
     * The complete ordered Sector intervals derived from the intermediate
     * boundaries (D-06, D-07, D-11).
     *
     * For `M` boundaries this is `M + 1` intervals (Sector 1 opens at the lap
     * crossing; the final Sector closes back on the start/finish line). When no
     * boundaries are configured (Sectors disabled) the result is empty — there
     * is no single all-encompassing interval, only "no sectors" (D-07).
     */
    val derivedSectors: List<SectorDefinition>
        get() {
            val boundaries = orderedSectors
            if (boundaries.isEmpty()) return emptyList()
            val intervals = ArrayList<SectorDefinition>(boundaries.size + 1)
            boundaries.forEachIndexed { index, boundary ->
                intervals += SectorDefinition(
                    sectorId = "sector-${index + 1}",
                    sectorOrder = index + 1,
                    closingBoundary = boundary,
                )
            }
            // Final interval closes on the accepted start/finish crossing (D-06).
            intervals += SectorDefinition(
                sectorId = "sector-${boundaries.size + 1}",
                sectorOrder = boundaries.size + 1,
                closingBoundary = null,
            )
            return intervals
        }
}
