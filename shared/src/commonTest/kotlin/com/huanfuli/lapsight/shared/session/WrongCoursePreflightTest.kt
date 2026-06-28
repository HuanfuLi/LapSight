package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.track.CourseGeometryThresholds
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WrongCoursePreflightTest {

    private val preflight = WrongCoursePreflight()

    @Test
    fun pitPositionNearClosingSegmentIsReady() {
        val result = preflight.evaluate(
            referenceLine = GpsFixtureLibrary.wrongCourseReferenceLine(),
            latestFix = GpsFixtureLibrary.wrongCoursePitPaddock(),
            nowElapsedMillis = 20_000L,
        )

        val ready = assertIs<CoursePreflightResult.Ready>(result)
        assertTrue(ready.distanceMeters in 20.0..40.0)
        assertEquals(250.0, ready.thresholdMeters)
    }

    @Test
    fun clearlyFarCourseBlocksAfterAccuracyUncertaintyIsSubtracted() {
        val result = preflight.evaluate(
            referenceLine = GpsFixtureLibrary.wrongCourseReferenceLine(),
            latestFix = GpsFixtureLibrary.wrongCourseFarCourse(),
            nowElapsedMillis = 20_000L,
        )

        val blocked = assertIs<CoursePreflightResult.Blocked>(result)
        assertTrue(blocked.distanceMeters > blocked.thresholdMeters)
        assertEquals(
            blocked.distanceMeters - GpsFixtureLibrary.wrongCourseFarCourse()
                .horizontalAccuracyMeters!!,
            blocked.conservativeDistanceMeters,
            absoluteTolerance = 0.001,
        )
    }

    @Test
    fun accuracyUncertaintyPreventsMarginalFalseBlock() {
        val result = preflight.evaluate(
            referenceLine = GpsFixtureLibrary.wrongCourseReferenceLine(),
            latestFix = GpsFixtureLibrary.wrongCourseAccuracyMargin(),
            nowElapsedMillis = 20_000L,
        )

        val ready = assertIs<CoursePreflightResult.Ready>(result)
        assertTrue(ready.distanceMeters > ready.thresholdMeters)
        assertTrue(ready.conservativeDistanceMeters <= ready.thresholdMeters)
    }

    @Test
    fun stalePoorAndNonFiniteFixesAreUnavailableNotWrongCourse() {
        val line = GpsFixtureLibrary.wrongCourseReferenceLine()

        assertEquals(
            CoursePreflightUnavailableReason.StaleFix,
            assertIs<CoursePreflightResult.Unavailable>(
                preflight.evaluate(
                    referenceLine = line,
                    latestFix = GpsFixtureLibrary.wrongCourseStale(),
                    nowElapsedMillis = 20_001L,
                ),
            ).reason,
        )
        assertEquals(
            CoursePreflightUnavailableReason.PoorAccuracy,
            assertIs<CoursePreflightResult.Unavailable>(
                preflight.evaluate(
                    referenceLine = line,
                    latestFix = GpsFixtureLibrary.wrongCoursePitPaddock()
                        .copy(horizontalAccuracyMeters = 100.1),
                    nowElapsedMillis = 20_000L,
                ),
            ).reason,
        )
        assertEquals(
            CoursePreflightUnavailableReason.NonFiniteFix,
            assertIs<CoursePreflightResult.Unavailable>(
                preflight.evaluate(
                    referenceLine = line,
                    latestFix = GpsFixtureLibrary.wrongCoursePitPaddock()
                        .copy(latitude = Double.NaN),
                    nowElapsedMillis = 20_000L,
                ),
            ).reason,
        )
    }

    @Test
    fun malformedNonFiniteAndOversizedGeometryFailBeforeScanning() {
        assertEquals(
            CoursePreflightUnavailableReason.MalformedGeometry,
            assertIs<CoursePreflightResult.Unavailable>(
                preflight.evaluate(
                    referenceLine = GpsFixtureLibrary.wrongCourseMalformedReferenceLine(),
                    latestFix = GpsFixtureLibrary.wrongCoursePitPaddock(),
                    nowElapsedMillis = 20_000L,
                ),
            ).reason,
        )
        assertEquals(
            CoursePreflightUnavailableReason.NonFiniteGeometry,
            assertIs<CoursePreflightResult.Unavailable>(
                preflight.evaluate(
                    referenceLine = GpsFixtureLibrary.wrongCourseNonFiniteReferenceLine(),
                    latestFix = GpsFixtureLibrary.wrongCoursePitPaddock(),
                    nowElapsedMillis = 20_000L,
                ),
            ).reason,
        )

        val oversized = TrackReferenceLine(
            points = List(CourseGeometryThresholds.Default.maxPathPoints + 1) { index ->
                GeoPointDto(
                    latitude = 39.0 + index * 0.000001,
                    longitude = -86.0,
                )
            },
            isClosed = true,
        )
        assertEquals(
            CoursePreflightUnavailableReason.OversizedGeometry,
            assertIs<CoursePreflightResult.Unavailable>(
                preflight.evaluate(
                    referenceLine = oversized,
                    latestFix = GpsFixtureLibrary.wrongCoursePitPaddock(),
                    nowElapsedMillis = 20_000L,
                ),
            ).reason,
        )
    }

    @Test
    fun snapshotDistinguishesNormalReadinessFromExplicitOverride() {
        val ready = assertIs<CoursePreflightResult.Ready>(
            preflight.evaluate(
                referenceLine = GpsFixtureLibrary.wrongCourseReferenceLine(),
                latestFix = GpsFixtureLibrary.wrongCoursePitPaddock(),
                nowElapsedMillis = 20_000L,
            ),
        )
        val blocked = assertIs<CoursePreflightResult.Blocked>(
            preflight.evaluate(
                referenceLine = GpsFixtureLibrary.wrongCourseReferenceLine(),
                latestFix = GpsFixtureLibrary.wrongCourseFarCourse(),
                nowElapsedMillis = 20_000L,
            ),
        )

        val normal = CoursePreflightSnapshot.from(ready)
        val override = CoursePreflightSnapshot.from(blocked, overrideUsed = true)

        assertEquals(CoursePreflightDisposition.Ready, normal.disposition)
        assertFalse(normal.overrideUsed)
        assertEquals(CoursePreflightDisposition.Overridden, override.disposition)
        assertTrue(override.overrideUsed)
        assertEquals(blocked.distanceMeters, override.distanceMeters)
    }
}
