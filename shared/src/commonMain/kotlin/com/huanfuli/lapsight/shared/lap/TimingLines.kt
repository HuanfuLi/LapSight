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
 * A full course definition for a demo/replay session: one start/finish line and
 * an ordered list of sector lines.
 */
data class CourseDefinition(
    val startFinish: StartFinishLine,
    val sectors: List<SectorLine> = emptyList(),
) {
    /** Sectors sorted by their declared [SectorLine.order]. */
    val orderedSectors: List<SectorLine>
        get() = sectors.sortedBy { it.order }
}
