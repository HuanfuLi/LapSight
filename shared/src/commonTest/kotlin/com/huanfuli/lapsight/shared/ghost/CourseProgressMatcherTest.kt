package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.track.ClosedReferencePath
import com.huanfuli.lapsight.shared.track.ClosedReferencePathResult
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CourseProgressMatcherTest {

    private val simulatedRecordedKey = CourseCompatibilityKey(
        profileId = "course-match-profile",
        geometryCompatibilityId = "course-match-profile:g1",
        direction = CourseDirection.Recorded,
        isSimulated = true,
    )

    private fun path(
        line: TrackReferenceLine = GpsFixtureLibrary.courseMatchReferenceLine(),
    ): ClosedReferencePath =
        assertIs<ClosedReferencePathResult.Loaded>(
            ClosedReferencePath.fromReferenceLine(line),
        ).path

    private fun matcher(
        line: TrackReferenceLine = GpsFixtureLibrary.courseMatchReferenceLine(),
        key: CourseCompatibilityKey = simulatedRecordedKey,
    ): CourseProgressMatcher = CourseProgressMatcher(
        path = path(line),
        startFinishProgressMeters = 0.0,
        compatibilityKey = key,
    )

    private fun matched(result: CourseMatchResult): CourseMatchResult.Matched =
        assertIs<CourseMatchResult.Matched>(result)

    private fun unmatched(result: CourseMatchResult): CourseMatchResult.Unmatched =
        assertIs<CourseMatchResult.Unmatched>(result)

    @Test
    fun nonAdjacentParallelCandidatesAreUnmatchedWhenAmbiguous() {
        val sample = GpsFixtureLibrary.courseMatchAmbiguity().single()
        val result = matcher(GpsFixtureLibrary.courseMatchAmbiguityReferenceLine()).match(
            sample = sample,
            activeCompatibilityKey = simulatedRecordedKey,
        )

        assertEquals(CourseUnmatchedReason.Ambiguous, unmatched(result).reason)
    }

    @Test
    fun temporaryExcursionSuppressesThenRematchesWithoutReset() {
        val matcher = matcher()
        val results = GpsFixtureLibrary.courseMatchRecovery().map {
            matcher.match(it, simulatedRecordedKey)
        }

        assertIs<CourseMatchResult.Matched>(results[0])
        assertIs<CourseMatchResult.Matched>(results[1])
        assertEquals(CourseUnmatchedReason.OffCourse, unmatched(results[2]).reason)
        assertIs<CourseMatchResult.Matched>(results[3])
        assertTrue(matched(results[3]).directionProgressMeters > matched(results[1]).directionProgressMeters)
    }

    @Test
    fun backwardMovementCanDecreaseDirectionProgress() {
        val matcher = matcher()
        val progress = GpsFixtureLibrary.courseMatchBackward().map {
            matched(matcher.match(it, simulatedRecordedKey)).directionProgressMeters
        }

        assertTrue(progress[1] > progress[0])
        assertTrue(progress[2] < progress[1], "backward motion must reduce course progress")
    }

    @Test
    fun reverseDirectionMirrorsRecordedProgressAroundStartFinish() {
        val sample = GpsFixtureLibrary.courseMatchBackward().first()
        val recorded = matched(matcher().match(sample, simulatedRecordedKey))
        val reverseKey = simulatedRecordedKey.copy(direction = CourseDirection.Reverse)
        val reverse = matched(matcher(key = reverseKey).match(sample, reverseKey))
        val perimeter = path().perimeter

        assertEquals(perimeter, recorded.directionProgressMeters + reverse.directionProgressMeters, 0.01)
        assertEquals(reverse.directionProgressMeters / perimeter, reverse.normalizedProgress, 1e-9)
    }

    @Test
    fun malformedPoorAccuracyImplausibleAndIncompatibleInputsNeverThrow() {
        val matcher = matcher()
        val base = GpsFixtureLibrary.courseMatchRecovery().first()

        assertEquals(
            CourseUnmatchedReason.NonFiniteInput,
            unmatched(matcher.match(base.copy(latitude = Double.NaN), simulatedRecordedKey)).reason,
        )
        assertEquals(
            CourseUnmatchedReason.PoorAccuracy,
            unmatched(matcher.match(base.copy(elapsedMillis = 1L, horizontalAccuracyMeters = 51.0), simulatedRecordedKey)).reason,
        )

        matcher.reset()
        matched(matcher.match(base, simulatedRecordedKey))
        val implausible = base.copy(
            elapsedMillis = 1_000L,
            longitude = GpsFixtureLibrary.courseMatchReferenceLine().points[1].longitude - 10.0 / 85_000.0,
            speedMetersPerSecond = 1.0,
        )
        assertEquals(
            CourseUnmatchedReason.ImplausibleJump,
            unmatched(matcher.match(implausible, simulatedRecordedKey)).reason,
        )

        assertEquals(
            CourseUnmatchedReason.IncompatibleCourse,
            unmatched(
                matcher.match(
                    base.copy(elapsedMillis = 2_000L),
                    simulatedRecordedKey.copy(direction = CourseDirection.Reverse),
                ),
            ).reason,
        )
        assertEquals(
            CourseUnmatchedReason.IncompatibleSource,
            unmatched(
                matcher.match(
                    base.copy(elapsedMillis = 3_000L, source = LocationSource.PhoneGps),
                    simulatedRecordedKey,
                ),
            ).reason,
        )
    }

    @Test
    fun liveDeltaTransitionsAvailableUnavailableAvailableWithoutReset() {
        val path = path()
        val matcher = matcher()
        val reference = ReferenceLap(
            trackId = simulatedRecordedKey.profileId,
            sessionId = "reference-session",
            lapNumber = 1,
            durationMillis = 40_000L,
            isSimulated = true,
            rawSamples = GpsFixtureLibrary.courseMatchRecovery(),
            progressCurve = ProgressCurve(
                totalDistanceMeters = path.perimeter,
                points = listOf(
                    progressPoint(elapsedMillis = 0L, progressMeters = 0.0, path = path),
                    progressPoint(elapsedMillis = 40_000L, progressMeters = path.perimeter, path = path),
                ),
            ),
            compatibilityKey = simulatedRecordedKey,
        )
        val engine = LiveDeltaEngine(courseMatcher = matcher)
        engine.setReference(reference)
        engine.startLap()

        val samples = GpsFixtureLibrary.courseMatchRecovery()
        assertIs<LiveDeltaSnapshot.Unavailable>(engine.update(samples[0]))
        assertIs<LiveDeltaSnapshot.Available>(engine.update(samples[1]))
        assertEquals(
            DeltaUnavailableReason.UnmatchedProgress,
            assertIs<LiveDeltaSnapshot.Unavailable>(engine.update(samples[2])).reason,
        )
        assertIs<LiveDeltaSnapshot.Available>(engine.update(samples[3]))
    }

    private fun progressPoint(
        elapsedMillis: Long,
        progressMeters: Double,
        path: ClosedReferencePath,
    ): ProgressPoint {
        val geo = path.pointAtGeo(progressMeters)
        val local = path.pointAt(progressMeters)
        return ProgressPoint(
            elapsedMillis = elapsedMillis,
            progressMeters = progressMeters,
            normalizedProgress = progressMeters / path.perimeter,
            latitude = geo.latitude,
            longitude = geo.longitude,
            localX = local.x,
            localY = local.y,
            speedMetersPerSecond = 10.0,
            headingDegrees = 90.0,
            horizontalAccuracyMeters = 5.0,
        )
    }
}
