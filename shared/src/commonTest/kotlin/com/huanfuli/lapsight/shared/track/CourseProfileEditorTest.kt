package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.lap.LocalPoint
import com.huanfuli.lapsight.shared.lap.LocalProjection
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Deterministic gate for the constrained offline course editor and equal Sector
 * generation (Plan 05-05 Task 2; SC-02 / D-05, D-07..D-10; threat T-05-11).
 *
 * Fixtures are built in local meters (path origin at latitude 0), so progress and
 * perimeter are exact and replay-stable.
 */
class CourseProfileEditorTest {

    private val mPerDeg = LocalProjection.METERS_PER_DEGREE

    private fun geo(xMeters: Double, yMeters: Double) = GeoPointDto(
        latitude = yMeters / mPerDeg,
        longitude = xMeters / mPerDeg,
    )

    private fun rectanglePath(
        width: Double,
        height: Double,
        thresholds: CourseGeometryThresholds = CourseGeometryThresholds.Default,
    ): ClosedReferencePath {
        val line = TrackReferenceLine(
            points = listOf(geo(0.0, 0.0), geo(width, 0.0), geo(width, height), geo(0.0, height)),
        )
        val result = ClosedReferencePath.fromReferenceLine(line, thresholds)
        assertIs<ClosedReferencePathResult.Loaded>(result)
        return result.path
    }

    /** A 100x60 oval (perimeter 320) with start/finish placed + confirmed at progress 0. */
    private fun confirmedOvalEditor(
        thresholds: CourseGeometryThresholds = CourseGeometryThresholds.Default,
    ): CourseProfileEditor =
        CourseProfileEditor.create(rectanglePath(100.0, 60.0, thresholds))
            .placeStartFinish(LocalPoint(0.0, 0.0))
            .confirmStartFinish()

    // --- D-07: enable / count / defaults --------------------------------------

    @Test
    fun enablingSectorsDefaultsToThreeWithTwoStableBoundaryIds() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true)

        assertTrue(editor.sectorsEnabled)
        assertEquals(3, editor.sectorCount, "enabling defaults to 3 Sectors (D-07)")
        assertEquals(2, editor.boundaries.size, "N Sectors -> N-1 boundaries (D-08)")
        assertEquals(listOf("sb-1", "sb-2"), editor.boundaries.map { it.id })
    }

    @Test
    fun disablingSectorsClearsBoundaries() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorsEnabled(false)
        assertFalse(editor.sectorsEnabled)
        assertEquals(0, editor.sectorCount)
        assertTrue(editor.boundaries.isEmpty())
    }

    @Test
    fun eachSectorCountTwoThroughSixGeneratesNMinusOneEqualBoundaries() {
        val base = confirmedOvalEditor().setSectorsEnabled(true)
        val perimeter = base.path.perimeter
        for (n in 2..6) {
            val editor = base.setSectorCount(n)
            assertEquals(n - 1, editor.boundaries.size, "N=$n must yield N-1 boundaries")
            // Equal arc placement from start/finish at progress 0: k*L/N.
            editor.boundaries.forEachIndexed { idx, b ->
                val expected = perimeter * (idx + 1) / n
                assertEquals(expected, b.progress, 1e-6, "boundary $idx for N=$n")
            }
            assertIs<CourseValidation.Valid>(editor.validate(), "N=$n on a 320 m oval is valid")
        }
    }

    @Test
    fun equalGenerationIsDeterministic() {
        val a = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(4)
        val b = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(4)
        assertEquals(a.boundaries, b.boundaries)
    }

    // --- D-09: derived perpendicular endpoints --------------------------------

    @Test
    fun boundaryEndpointsAreDerivedPerpendicularToTangentNotCanvasCoordinates() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(4)
        val built = editor.buildBoundaries()
        assertEquals(3, built.size)

        built.forEach { boundary ->
            val progress = boundary.normalizedProgress!! * editor.path.perimeter
            val tangent = editor.path.tangentAt(progress)
            val a = editor.path.toLocal(boundary.pointA)
            val b = editor.path.toLocal(boundary.pointB)
            // The boundary segment direction must be perpendicular to the tangent.
            val dirX = b.x - a.x
            val dirY = b.y - a.y
            val len = hypot(dirX, dirY)
            val dot = (dirX / len) * tangent.x + (dirY / len) * tangent.y
            assertEquals(0.0, dot, 1e-6, "boundary must be perpendicular to the tangent")
            // Endpoints straddle the anchor on the path (centered), not a canvas point.
            val mid = LocalPoint((a.x + b.x) / 2, (a.y + b.y) / 2)
            val anchor = editor.path.pointAt(progress)
            assertTrue(hypot(mid.x - anchor.x, mid.y - anchor.y) < 1e-6, "boundary is centered on its anchor")
            assertEquals(editor.path.thresholds.boundaryLengthMeters, len, 1e-6)
        }
    }

    // --- D-10: constrained drag, progress-only, snapping, spacing -------------

    @Test
    fun dragMovesOnlyProgressAndSnapsToTheGrid() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(2)
        // Single boundary starts at L/2 = 160 (top edge). Drag toward a point near
        // (70.3, 59.0): projects onto the top edge near progress ~ 160 + (100-70.3)=189.7.
        val dragged = editor.dragBoundaryGeo("sb-1", geo(70.3, 59.0))
        val moved = dragged.boundaries.single()

        // Progress is snapped to the 1 m grid.
        assertEquals(moved.progress, kotlin.math.round(moved.progress / 1.0) * 1.0, 1e-9)
        // It actually moved along the trace (progress changed) and stayed in [0, L).
        assertTrue(moved.progress in 0.0..editor.path.perimeter)
        // Editor boundary carries progress only — no canvas/endpoint fields exist.
        assertEquals("sb-1", moved.id)
    }

    @Test
    fun dragStartFinishByMovesAlongCourseProgressAndPreservesSectorOffsets() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(4)
        val beforeBoundaries = editor.boundaries.map { it.progress }

        val dragged = editor.dragStartFinishBy(40.2)

        assertEquals(40.0, dragged.startFinishProgress!!, 1e-6)
        assertFalse(dragged.startFinishConfirmed, "moving start/finish requires reconfirmation")
        dragged.boundaries.zip(beforeBoundaries).forEach { (after, before) ->
            assertEquals(
                dragged.path.wrap(before + 40.0),
                after.progress,
                1e-6,
                "sector boundaries should move with the dragged start/finish",
            )
        }
    }

    @Test
    fun dragBoundaryByMovesRelativeToExistingBoundary() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(2)
        val before = editor.boundaries.single().progress

        val dragged = editor.dragBoundaryBy("sb-1", 28.6)

        assertEquals(editor.path.wrap(before + 29.0), dragged.boundaries.single().progress, 1e-6)
    }

    @Test
    fun dragIsClampedToMinimumCyclicSpacingFromStartFinish() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(3)
        val minSpacing = editor.path.thresholds.minCyclicSpacing(editor.path.perimeter)

        // Try to drag the first boundary right onto start/finish (progress ~ 0).
        val dragged = editor.dragBoundary("sb-1", LocalPoint(0.0, 0.0))
        val moved = dragged.boundaries.first { it.id == "sb-1" }

        // Cyclic distance from start/finish (progress 0) must respect the minimum spacing.
        val cyclic = minOf(moved.progress, editor.path.perimeter - moved.progress)
        assertTrue(
            cyclic >= minSpacing - 1e-6,
            "drag must clamp to >= $minSpacing m from start/finish, was $cyclic",
        )
    }

    // --- D-05: confirmed start/finish & saveability ----------------------------

    @Test
    fun disabledSectorsWithConfirmedStartFinishIsSaveable() {
        val editor = confirmedOvalEditor()
        assertIs<CourseValidation.Valid>(editor.validate())
        assertTrue(editor.canSave)

        val setup = editor.toCourseSetup()
        assertFalse(setup.sectorsEnabled)
        assertEquals(0, setup.sectorCount)
        assertTrue(setup.boundaries.isEmpty())
        assertEquals(0.0, setup.startFinishProgress!! * editor.path.perimeter, 1e-6)
        assertTrue(setup.startFinish != null)
    }

    @Test
    fun savingRequiresAConfirmedStartFinish() {
        val noStartFinish = CourseProfileEditor.create(rectanglePath(100.0, 60.0))
        assertFalse(noStartFinish.canSave)
        val v1 = noStartFinish.validate()
        assertIs<CourseValidation.Invalid>(v1)
        assertTrue(v1.problems.contains(CourseProblem.NoStartFinish))

        val placedNotConfirmed = noStartFinish.placeStartFinish(LocalPoint(0.0, 0.0))
        assertFalse(placedNotConfirmed.canSave)
        val v2 = placedNotConfirmed.validate()
        assertIs<CourseValidation.Invalid>(v2)
        assertTrue(v2.problems.contains(CourseProblem.StartFinishUnconfirmed))
    }

    // --- typed validation: invalid count, impossible spacing, hairpin ----------

    @Test
    fun invalidSectorCountIsRejected() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(7)
        val v = editor.validate()
        assertIs<CourseValidation.Invalid>(v)
        assertTrue(v.problems.contains(CourseProblem.InvalidSectorCount))
        assertFalse(editor.canSave)
    }

    @Test
    fun impossibleSpacingCountIsRejected() {
        // Force a large minimum spacing so 6 Sectors cannot fit on the 320 m oval,
        // while 3 Sectors still can.
        val thresholds = CourseGeometryThresholds(minSpacingFloorMeters = 100.0)
        val editor = confirmedOvalEditor(thresholds).setSectorsEnabled(true)

        val six = editor.setSectorCount(6)
        val v6 = six.validate()
        assertIs<CourseValidation.Invalid>(v6)
        assertTrue(v6.problems.contains(CourseProblem.ImpossibleSpacing))

        val three = editor.setSectorCount(3)
        assertIs<CourseValidation.Valid>(three.validate(), "3 Sectors still fit at 100 m spacing")
    }

    @Test
    fun hairpinSelfIntersectingBoundaryIsRejected() {
        // A thin 100x4 rectangle (perimeter 208): a 30 m perpendicular boundary at
        // any point cuts BOTH parallel long edges, so it is self-intersecting (D-09).
        val path = rectanglePath(100.0, 4.0)
        val editor = CourseProfileEditor.create(path)
            .placeStartFinish(LocalPoint(0.0, 0.0))
            .confirmStartFinish()
            .setSectorsEnabled(true)
            .setSectorCount(2)

        val v = editor.validate()
        assertIs<CourseValidation.Invalid>(v)
        assertTrue(
            v.problems.contains(CourseProblem.AmbiguousBoundary),
            "a boundary cutting a parallel straight must be rejected, problems=${v.problems}",
        )
    }

    // --- persistence shape ----------------------------------------------------

    @Test
    fun toCourseSetupCarriesProgressAndDerivedEndpoints() {
        val editor = confirmedOvalEditor().setSectorsEnabled(true).setSectorCount(3)
        val setup = editor.toCourseSetup()

        assertTrue(setup.sectorsEnabled)
        assertEquals(3, setup.sectorCount)
        assertEquals(2, setup.boundaries.size)
        setup.boundaries.forEach { b ->
            assertTrue(b.normalizedProgress != null, "each boundary persists its progress anchor")
            assertTrue(abs(b.pointA.latitude - b.pointB.latitude) > 0 || abs(b.pointA.longitude - b.pointB.longitude) > 0)
        }
        assertEquals(listOf("sb-1", "sb-2"), setup.boundaries.map { it.id })
    }
}
