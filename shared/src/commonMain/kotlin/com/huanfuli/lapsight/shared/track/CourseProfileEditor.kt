package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.session.GeoPointDto

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
    /** Place/replace start/finish from a candidate point in local meters (re-projects + snaps). */
    fun placeStartFinish(local: LocalPoint): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Place/replace start/finish from a candidate geographic point. */
    fun placeStartFinishGeo(geo: GeoPointDto): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Confirm the placed start/finish as the timing boundary (D-05). */
    fun confirmStartFinish(): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Enable/disable Sector timing; enabling defaults to 3 Sectors (D-07). */
    fun setSectorsEnabled(enabled: Boolean): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Set the Sector count and regenerate equal boundaries with stable ids (D-08). */
    fun setSectorCount(count: Int): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Drag a boundary along the trace; persists snapped progress only, clamped to spacing (D-10). */
    fun dragBoundary(id: String, local: LocalPoint): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Drag a boundary from a candidate geographic point. */
    fun dragBoundaryGeo(id: String, geo: GeoPointDto): CourseProfileEditor = TODO("Task 2 GREEN")

    /** Typed validation of the current edit state. */
    fun validate(): CourseValidation = TODO("Task 2 GREEN")

    /** True iff start/finish is placed, confirmed, and the whole setup validates (D-05). */
    val canSave: Boolean get() = TODO("Task 2 GREEN")

    /** The derived finite start/finish line, or null when none is placed. */
    fun buildStartFinishLine(): StartFinishLineDto? = TODO("Task 2 GREEN")

    /** The derived finite perpendicular boundaries (endpoints from progress + tangent). */
    fun buildBoundaries(): List<SectorBoundary> = TODO("Task 2 GREEN")

    /** Build the persisted [CourseSetup]; requires a placed, confirmed start/finish. */
    fun toCourseSetup(): CourseSetup = TODO("Task 2 GREEN")

    companion object {
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
