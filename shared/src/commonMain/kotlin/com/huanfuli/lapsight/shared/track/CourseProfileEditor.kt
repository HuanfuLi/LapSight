package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.round

/** One intermediate boundary in editor state: progress-only, never canvas coordinates (D-10). */
data class EditorBoundary(
    val id: String,
    val order: Int,
    val progress: Double,
)

/** A specific reason a [CourseProfileEditor] is not yet a valid, saveable course (D-05..D-10). */
enum class CourseProblem {
    /** No start/finish has been placed yet. */
    NoStartFinish,

    /** Start/finish is placed but not confirmed; formal Timing requires a confirmed boundary (D-05). */
    StartFinishUnconfirmed,

    /** Enabled Sector count is outside the 2..6 range (D-07). */
    InvalidSectorCount,

    /** The path is too short to fit this many equal Sectors at the minimum spacing. */
    ImpossibleSpacing,

    /** A generated boundary also cuts a nearby parallel/hairpin section (D-09). */
    AmbiguousBoundary,

    /** Two boundaries (or a boundary and start/finish) are closer than the minimum spacing. */
    BoundarySpacingTooTight,
}

/** Typed validation outcome for the editor; never thrown (T-05-11). */
sealed interface CourseValidation {
    data object Valid : CourseValidation
    data class Invalid(val problems: List<CourseProblem>) : CourseValidation

    val isValid: Boolean get() = this is Valid
}

/**
 * Pure, immutable offline course-editing state (Plan 05-05 Task 2; D-05..D-10).
 *
 * Every mutator returns a copied editor. Start/finish and every Sector boundary are
 * stored as absolute recorded arc-length [progress] only; finite endpoints are
 * regenerated on demand by [CourseGeometryBuilder] from progress + tangent. Drag
 * candidates are projected onto the [ClosedReferencePath] and snapped, so the
 * editor never persists endpoint or canvas coordinates (D-09, D-10). No Compose,
 * platform, or storage dependency.
 */
data class CourseProfileEditor(
    val path: ClosedReferencePath,
    val startFinishProgress: Double?,
    val startFinishConfirmed: Boolean,
    val sectorsEnabled: Boolean,
    val sectorCount: Int,
    val boundaries: List<EditorBoundary>,
) {
    private val thresholds get() = path.thresholds

    /** Place/replace start/finish from a candidate point in local meters (re-projects + snaps). */
    fun placeStartFinish(local: LocalPoint): CourseProfileEditor {
        val progress = snap(path.projectLocal(local).progressMeters)
        val regenerated = if (sectorsEnabled) generateEqualBoundaries(sectorCount, progress) else boundaries
        return copy(
            startFinishProgress = progress,
            startFinishConfirmed = false,
            boundaries = regenerated,
        )
    }

    /** Place/replace start/finish from a candidate geographic point. */
    fun placeStartFinishGeo(geo: GeoPointDto): CourseProfileEditor = placeStartFinish(path.toLocal(geo))

    /**
     * Drag the existing start/finish by an arc-length delta along the recorded trace.
     *
     * Unlike [placeStartFinish], this is a true handle drag: the movement is relative
     * to the current progress anchor, so dragging away from the thin trace does not
     * repeatedly re-project to nearly the same nearest point.
     */
    fun dragStartFinishBy(deltaMeters: Double): CourseProfileEditor {
        val current = startFinishProgress ?: return this
        val snappedRaw = snapRaw(current + deltaMeters)
        val movedProgress = path.wrap(snappedRaw)
        val actualDelta = snappedRaw - current
        val movedBoundaries = if (sectorsEnabled) {
            boundaries.map { it.copy(progress = path.wrap(it.progress + actualDelta)) }
        } else {
            boundaries
        }
        return copy(
            startFinishProgress = movedProgress,
            startFinishConfirmed = false,
            boundaries = movedBoundaries,
        )
    }

    /** Confirm the placed start/finish as the timing boundary (D-05). */
    fun confirmStartFinish(): CourseProfileEditor {
        require(startFinishProgress != null) { "Cannot confirm start/finish before it is placed." }
        return copy(startFinishConfirmed = true)
    }

    /** Enable/disable Sector timing; enabling defaults to 3 Sectors (D-07). */
    fun setSectorsEnabled(enabled: Boolean): CourseProfileEditor {
        if (!enabled) {
            return copy(sectorsEnabled = false, sectorCount = 0, boundaries = emptyList())
        }
        val count = if (sectorCount in CourseGeometryBuilder.MIN_SECTOR_COUNT..CourseGeometryBuilder.MAX_SECTOR_COUNT) {
            sectorCount
        } else {
            CourseGeometryBuilder.DEFAULT_SECTOR_COUNT
        }
        return copy(
            sectorsEnabled = true,
            sectorCount = count,
            boundaries = generateEqualBoundaries(count, startFinishProgress ?: 0.0),
        )
    }

    /** Set the Sector count and regenerate equal boundaries with stable ids (D-08). */
    fun setSectorCount(count: Int): CourseProfileEditor = copy(
        sectorsEnabled = true,
        sectorCount = count,
        boundaries = if (count >= CourseGeometryBuilder.MIN_SECTOR_COUNT) {
            generateEqualBoundaries(count, startFinishProgress ?: 0.0)
        } else {
            emptyList()
        },
    )

    /** Drag a boundary along the trace; persists snapped progress only, clamped to spacing (D-10). */
    fun dragBoundary(id: String, local: LocalPoint): CourseProfileEditor {
        val target = boundaries.firstOrNull { it.id == id } ?: return this
        val candidate = snap(path.projectLocal(local).progressMeters)
        val clamped = clampToSpacing(id, candidate)
        return copy(
            boundaries = boundaries.map { if (it.id == id) it.copy(progress = clamped) else it },
        )
    }

    /** Drag a boundary by an arc-length delta along the trace. */
    fun dragBoundaryBy(id: String, deltaMeters: Double): CourseProfileEditor {
        val target = boundaries.firstOrNull { it.id == id } ?: return this
        val candidate = path.wrap(snapRaw(target.progress + deltaMeters))
        val clamped = clampToSpacing(id, candidate)
        return copy(
            boundaries = boundaries.map { if (it.id == id) it.copy(progress = clamped) else it },
        )
    }

    /** Drag a boundary from a candidate geographic point. */
    fun dragBoundaryGeo(id: String, geo: GeoPointDto): CourseProfileEditor =
        dragBoundary(id, path.toLocal(geo))

    /** Typed validation of the current edit state. */
    fun validate(): CourseValidation {
        val problems = LinkedHashSet<CourseProblem>()
        when {
            startFinishProgress == null -> problems += CourseProblem.NoStartFinish
            !startFinishConfirmed -> problems += CourseProblem.StartFinishUnconfirmed
        }
        if (sectorsEnabled) {
            val l = path.perimeter
            val minSpacing = thresholds.minCyclicSpacing(l)
            if (sectorCount !in CourseGeometryBuilder.MIN_SECTOR_COUNT..CourseGeometryBuilder.MAX_SECTOR_COUNT) {
                problems += CourseProblem.InvalidSectorCount
            } else {
                if (l / sectorCount.toDouble() < minSpacing - EPSILON) {
                    problems += CourseProblem.ImpossibleSpacing
                }
                if (boundaries.any { CourseGeometryBuilder.pathCrossingCount(path, it.progress) > 1 }) {
                    problems += CourseProblem.AmbiguousBoundary
                }
                if (hasTightSpacing(minSpacing)) {
                    problems += CourseProblem.BoundarySpacingTooTight
                }
            }
        }
        return if (problems.isEmpty()) CourseValidation.Valid else CourseValidation.Invalid(problems.toList())
    }

    /** True iff start/finish is placed, confirmed, and the whole setup validates (D-05). */
    val canSave: Boolean get() = validate().isValid

    /** The derived finite start/finish line, or null when none is placed. */
    fun buildStartFinishLine(): StartFinishLineDto? =
        startFinishProgress?.let { CourseGeometryBuilder.buildStartFinishLine(path, it) }

    /** The derived finite perpendicular boundaries (endpoints from progress + tangent). */
    fun buildBoundaries(): List<SectorBoundary> =
        boundaries.map { CourseGeometryBuilder.buildBoundary(path, it.id, it.order, it.progress) }

    /** Build the persisted [CourseSetup]; requires a placed, confirmed start/finish. */
    fun toCourseSetup(): CourseSetup {
        val sf = startFinishProgress
        require(sf != null && startFinishConfirmed) {
            "Cannot build a CourseSetup without a confirmed start/finish (D-05)."
        }
        return CourseSetup(
            startFinish = buildStartFinishLine(),
            startFinishProgress = path.wrap(sf) / path.perimeter,
            sectorsEnabled = sectorsEnabled,
            sectorCount = sectorCount,
            boundaries = buildBoundaries(),
        )
    }

    // --- internals ------------------------------------------------------------

    private fun generateEqualBoundaries(count: Int, startFinishProgress: Double): List<EditorBoundary> =
        CourseGeometryBuilder.equalBoundaryProgresses(path, startFinishProgress, count)
            .mapIndexed { idx, progress ->
                val k = idx + 1
                EditorBoundary(id = CourseGeometryBuilder.boundaryId(k), order = k, progress = progress)
            }

    /** Snap arc length to the configured grid and wrap into [0, perimeter). */
    private fun snap(progress: Double): Double {
        return path.wrap(snapRaw(progress))
    }

    /** Snap arc length to the configured grid without wrapping, preserving drag direction. */
    private fun snapRaw(progress: Double): Double {
        val grid = thresholds.snapMeters
        return if (grid <= 0.0) progress else round(progress / grid) * grid
    }

    /**
     * Clamp a candidate boundary progress so it keeps the minimum cyclic spacing
     * from start/finish and every other boundary. Works in offsets relative to
     * start/finish so the cyclic neighbours are easy to find.
     */
    private fun clampToSpacing(id: String, candidateProgress: Double): Double {
        val l = path.perimeter
        val s0 = startFinishProgress ?: 0.0
        val minSpacing = thresholds.minCyclicSpacing(l)

        // Fixed anchors (offsets from start/finish, in [0, L)): start/finish itself
        // plus every OTHER boundary. Append L as the wrapped start/finish sentinel.
        val fixed = mutableListOf(0.0)
        boundaries.filter { it.id != id }.forEach { fixed += offsetFrom(s0, it.progress) }
        val sorted = fixed.sorted().toMutableList()
        sorted.add(l)

        val co = offsetFrom(s0, candidateProgress)
        var lower = 0.0
        var upper = l
        for (v in sorted) {
            if (v <= co) lower = v
            if (v > co) { upper = v; break }
        }
        val lo = lower + minSpacing
        val hi = upper - minSpacing
        val clampedOffset = if (lo > hi) (lower + upper) / 2.0 else co.coerceIn(lo, hi)
        return path.wrap(s0 + clampedOffset)
    }

    /** Cyclic offset of [progress] ahead of [from], in [0, perimeter). */
    private fun offsetFrom(from: Double, progress: Double): Double = path.wrap(progress - from)

    /** True when any two adjacent anchors (start/finish + boundaries) are closer than [minSpacing]. */
    private fun hasTightSpacing(minSpacing: Double): Boolean {
        val l = path.perimeter
        val s0 = startFinishProgress ?: 0.0
        val offsets = (listOf(0.0) + boundaries.map { offsetFrom(s0, it.progress) }).sorted()
        for (i in offsets.indices) {
            val next = if (i + 1 < offsets.size) offsets[i + 1] else offsets[0] + l
            if (next - offsets[i] < minSpacing - EPSILON) return true
        }
        return false
    }

    companion object {
        private const val EPSILON: Double = 1e-6

        /** A fresh editor over [path]: no start/finish, Sector timing disabled. */
        fun create(path: ClosedReferencePath): CourseProfileEditor = CourseProfileEditor(
            path = path,
            startFinishProgress = null,
            startFinishConfirmed = false,
            sectorsEnabled = false,
            sectorCount = 0,
            boundaries = emptyList(),
        )
    }
}
