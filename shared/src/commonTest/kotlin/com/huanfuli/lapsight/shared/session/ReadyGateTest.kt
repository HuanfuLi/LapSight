package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import com.huanfuli.lapsight.shared.track.TrackRevision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Coverage for the conservative pure [aggregateReady] gate (D-13, D-14, D-15).
 *
 * One test per [ReadyBlocker], the all-satisfied Ready case, a multi-reason case,
 * threshold-validation, and the "bad GPS is data, never an exception" contract.
 */
class ReadyGateTest {

    // --- Good baseline inputs (all satisfied) ----------------------------------

    private val goodFix = LocationSample(
        elapsedMillis = 10_000L,
        latitude = 39.81,
        longitude = -86.10,
        horizontalAccuracyMeters = 4.0,
        speedMetersPerSecond = 0.0, // stationary is allowed (D-15)
        headingDegrees = null,
        altitudeMeters = 219.0,
        source = LocationSource.PhoneGps,
    )

    private val now = 12_000L // 2 s after the fix → fresh
    private val goodRate = 5.0
    private val readyPreflight = CoursePreflightResult.Ready(
        distanceMeters = 10.0,
        conservativeDistanceMeters = 5.0,
        thresholdMeters = 250.0,
    )

    private fun selectedProfile(): CurrentProfileResolution.Selected {
        val ref = TrackReferenceLine(
            points = listOf(
                GeoPointDto(latitude = 0.0, longitude = 0.0),
                GeoPointDto(latitude = 0.0, longitude = 0.001),
                GeoPointDto(latitude = 0.001, longitude = 0.001),
                GeoPointDto(latitude = 0.0, longitude = 0.0),
            ),
            isClosed = true,
        )
        val startFinish = StartFinishLineDto(
            pointA = GeoPointDto(latitude = 0.0, longitude = 0.0),
            pointB = GeoPointDto(latitude = 0.0, longitude = 0.001),
        )
        val revision = TrackRevision(
            revisionId = "p1:r1",
            ordinal = 1,
            createdAtEpochMillis = 0L,
            sourceMarkingSessionId = null,
            referenceLine = ref,
            courseSetup = CourseSetup(startFinish = startFinish),
            geometryCompatibilityId = "p1:g1",
        )
        val profile = TrackProfile(
            profileId = "p1",
            name = "Track",
            createdAtEpochMillis = 0L,
            source = SourceMetadata(LocationSource.PhoneGps, isSimulated = false),
            revisions = listOf(revision),
        )
        return CurrentProfileResolution.Selected(
            profile = profile,
            revision = revision,
            direction = CourseDirection.Recorded,
        )
    }

    private fun ready(
        latest: LocationSample? = goodFix,
        nowElapsedMillis: Long = now,
        recentRateHz: Double? = goodRate,
        selection: CurrentProfileResolution = selectedProfile(),
        startFinishConfirmed: Boolean = true,
        directionCompatible: Boolean = true,
        preflight: CoursePreflightResult = readyPreflight,
    ): ReadyState = aggregateReady(
        latest = latest,
        nowElapsedMillis = nowElapsedMillis,
        recentRateHz = recentRateHz,
        selection = selection,
        startFinishConfirmed = startFinishConfirmed,
        directionCompatible = directionCompatible,
        preflight = preflight,
    )

    private fun startReady(
        latest: LocationSample? = goodFix,
        selection: CurrentProfileResolution = selectedProfile(),
        startFinishConfirmed: Boolean = true,
        directionCompatible: Boolean = true,
        preflight: CoursePreflightResult = readyPreflight,
    ): ReadyState = aggregateStartReady(
        latest = latest,
        selection = selection,
        startFinishConfirmed = startFinishConfirmed,
        directionCompatible = directionCompatible,
        preflight = preflight,
    )

    private fun soleReason(state: ReadyState): ReadyBlocker {
        val notReady = assertIs<ReadyState.NotReady>(state)
        assertEquals(1, notReady.reasons.size, "expected exactly one blocker, got ${notReady.reasons}")
        return notReady.reasons.single()
    }

    // --- All satisfied ---------------------------------------------------------

    @Test
    fun allInputsSatisfiedIsReady() {
        assertIs<ReadyState.Ready>(ready())
    }

    @Test
    fun startGateTreatsPoorAccuracyAndUnavailablePreflightAsWarnings() {
        val unavailable = CoursePreflightResult.Unavailable(
            CoursePreflightUnavailableReason.PoorAccuracy,
        )

        val state = startReady(
            latest = goodFix.copy(horizontalAccuracyMeters = 120.0),
            preflight = unavailable,
        )

        assertIs<ReadyState.Ready>(state)
    }

    @Test
    fun startGateStillBlocksMissingFix() {
        assertEquals(
            ReadyBlocker.MissingFix,
            soleReason(
                startReady(
                    latest = null,
                    preflight = CoursePreflightResult.Unavailable(
                        CoursePreflightUnavailableReason.MissingFix,
                    ),
                ),
            ),
        )
    }

    @Test
    fun startGateStillBlocksWrongCoursePreflight() {
        val blocked = CoursePreflightResult.Blocked(
            distanceMeters = 500.0,
            conservativeDistanceMeters = 450.0,
            thresholdMeters = 250.0,
        )

        assertEquals(
            ReadyBlocker.WrongCourseBlocked,
            soleReason(startReady(preflight = blocked)),
        )
    }

    // --- One test per ReadyBlocker ---------------------------------------------

    @Test
    fun missingFixBlocksReady() {
        assertEquals(ReadyBlocker.MissingFix, soleReason(ready(latest = null)))
    }

    @Test
    fun nonFiniteFixBlocksReady() {
        assertEquals(
            ReadyBlocker.NonFiniteFix,
            soleReason(ready(latest = goodFix.copy(latitude = Double.NaN))),
        )
    }

    @Test
    fun poorAccuracyBlocksReady() {
        assertEquals(
            ReadyBlocker.PoorAccuracy,
            soleReason(ready(latest = goodFix.copy(horizontalAccuracyMeters = 40.0))),
        )
    }

    @Test
    fun staleFixBlocksReady() {
        // Fix at 10_000 with now 30_000 → 20 s old, beyond the 15 s window.
        assertEquals(
            ReadyBlocker.StaleFix,
            soleReason(ready(nowElapsedMillis = 30_000L)),
        )
    }

    @Test
    fun lowSampleRateBlocksReady() {
        assertEquals(
            ReadyBlocker.LowSampleRate,
            soleReason(ready(recentRateHz = 0.89)),
        )
    }

    @Test
    fun pointNineHertzSatisfiesSampleRateFloor() {
        assertIs<ReadyState.Ready>(ready(recentRateHz = 0.9))
    }

    @Test
    fun missingSampleRateBlocksReady() {
        assertEquals(
            ReadyBlocker.LowSampleRate,
            soleReason(ready(recentRateHz = null)),
        )
    }

    @Test
    fun noCourseSelectedBlocksReady() {
        assertEquals(
            ReadyBlocker.NoCourseSelected,
            soleReason(ready(selection = CurrentProfileResolution.None)),
        )
    }

    @Test
    fun startFinishUnconfirmedBlocksReady() {
        assertEquals(
            ReadyBlocker.StartFinishUnconfirmed,
            soleReason(ready(startFinishConfirmed = false)),
        )
    }

    @Test
    fun directionIncompatibleBlocksReady() {
        assertEquals(
            ReadyBlocker.DirectionIncompatible,
            soleReason(ready(directionCompatible = false)),
        )
    }

    @Test
    fun wrongCoursePreflightBlocksReady() {
        val blocked = CoursePreflightResult.Blocked(
            distanceMeters = 500.0,
            conservativeDistanceMeters = 450.0,
            thresholdMeters = 250.0,
        )
        assertEquals(
            ReadyBlocker.WrongCourseBlocked,
            soleReason(ready(preflight = blocked)),
        )
    }

    @Test
    fun preflightUnavailableBlocksReady() {
        val unavailable = CoursePreflightResult.Unavailable(
            CoursePreflightUnavailableReason.MissingFix,
        )
        assertEquals(
            ReadyBlocker.PreflightUnavailable,
            soleReason(ready(preflight = unavailable)),
        )
    }

    // --- Multiple failing conditions -------------------------------------------

    @Test
    fun multipleFailingConditionsListEveryReason() {
        val state = aggregateReady(
            latest = null,
            nowElapsedMillis = now,
            recentRateHz = 0.2,
            selection = CurrentProfileResolution.None,
            startFinishConfirmed = false,
            directionCompatible = false,
            preflight = CoursePreflightResult.Unavailable(
                CoursePreflightUnavailableReason.MissingFix,
            ),
        )
        val notReady = assertIs<ReadyState.NotReady>(state)
        assertTrue(notReady.reasons.containsAll(
            listOf(
                ReadyBlocker.MissingFix,
                ReadyBlocker.LowSampleRate,
                ReadyBlocker.NoCourseSelected,
                ReadyBlocker.StartFinishUnconfirmed,
                ReadyBlocker.DirectionIncompatible,
                ReadyBlocker.PreflightUnavailable,
            ),
        ), "expected every applicable reason, got ${notReady.reasons}")
    }

    // --- Bad input is data, never an exception ---------------------------------

    @Test
    fun nonFiniteGpsReturnsNotReadyWithoutThrowing() {
        // Out-of-range and infinite coordinates must be data, not exceptions.
        assertEquals(
            ReadyBlocker.NonFiniteFix,
            soleReason(ready(latest = goodFix.copy(longitude = Double.POSITIVE_INFINITY))),
        )
        assertEquals(
            ReadyBlocker.NonFiniteFix,
            soleReason(ready(latest = goodFix.copy(latitude = 200.0))),
        )
    }

    // --- Threshold validation --------------------------------------------------

    @Test
    fun nonFiniteOrNonPositiveThresholdsThrow() {
        assertFailsWith<IllegalArgumentException> {
            ReadyThresholds(maxHorizontalAccuracyMeters = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            ReadyThresholds(maxHorizontalAccuracyMeters = Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            ReadyThresholds(maxFixAgeMillis = -1L)
        }
        assertFailsWith<IllegalArgumentException> {
            ReadyThresholds(minSampleRateHz = 0.0)
        }
    }

    @Test
    fun injectedThresholdsAreHonored() {
        // A stricter accuracy threshold rejects an otherwise-good 4 m fix.
        val strict = ReadyThresholds(maxHorizontalAccuracyMeters = 2.0)
        val state = aggregateReady(
            latest = goodFix,
            nowElapsedMillis = now,
            recentRateHz = goodRate,
            selection = selectedProfile(),
            startFinishConfirmed = true,
            directionCompatible = true,
            preflight = readyPreflight,
            thresholds = strict,
        )
        assertEquals(ReadyBlocker.PoorAccuracy, soleReason(state))
    }
}
