package com.huanfuli.lapsight.shared.track

/**
 * Pure generator of course boundary geometry over a [ClosedReferencePath]
 * (Plan 05-05 Task 2; D-06, D-08, D-09).
 *
 * It owns the rule that boundaries are NOT Sectors: for `N` configured Sectors it
 * produces `N-1` intermediate boundaries at equal closed-loop arc-length intervals
 * starting at the start/finish anchor, each a finite line perpendicular to the
 * smoothed local tangent. Endpoints are always derived from progress + tangent, so
 * they are never user-editable canvas coordinates (D-09, D-10). No Compose,
 * platform, or storage types appear here.
 */
object CourseGeometryBuilder {

    /** A new enabled Sector setup defaults to 3 Sectors (D-07). */
    const val DEFAULT_SECTOR_COUNT: Int = 3

    /** Minimum configurable Sector count when enabled (D-07). */
    const val MIN_SECTOR_COUNT: Int = 2

    /** Maximum configurable Sector count (D-07). */
    const val MAX_SECTOR_COUNT: Int = 6

    /** Stable id for the k-th (1-based) intermediate boundary. */
    fun boundaryId(k: Int): String = "sb-$k"

    /** Equal closed-loop arc-length progresses for the `sectorCount-1` boundaries. */
    fun equalBoundaryProgresses(
        path: ClosedReferencePath,
        startFinishProgress: Double,
        sectorCount: Int,
    ): List<Double> = TODO("Task 2 GREEN")

    /** Build a finite perpendicular [SectorBoundary] at arc-length [progress]. */
    fun buildBoundary(
        path: ClosedReferencePath,
        id: String,
        order: Int,
        progress: Double,
    ): SectorBoundary = TODO("Task 2 GREEN")

    /** Build the finite perpendicular start/finish line at arc-length [progress]. */
    fun buildStartFinishLine(
        path: ClosedReferencePath,
        progress: Double,
    ): StartFinishLineDto = TODO("Task 2 GREEN")

    /**
     * How many times the finite perpendicular boundary at [progress] crosses the
     * closed path. A valid boundary crosses exactly once (at its anchor); a count
     * of 2+ means it also cuts a nearby parallel/hairpin section (D-09 invalid).
     */
    fun pathCrossingCount(
        path: ClosedReferencePath,
        progress: Double,
    ): Int = TODO("Task 2 GREEN")
}
