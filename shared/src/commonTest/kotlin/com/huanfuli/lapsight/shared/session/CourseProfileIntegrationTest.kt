package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.ghost.DeltaUnavailableReason
import com.huanfuli.lapsight.shared.ghost.LiveDeltaSnapshot
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CreateProfileResult
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfileController
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import com.huanfuli.lapsight.shared.track.TrackRevision
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CourseProfileIntegrationTest {

    private val store = InMemorySessionStore()
    private val app = AppMetadata(appVersion = "0.5.0", platform = "test")

    @Test
    fun startTimingLoadsV2ProfileWhenNoLegacyTrackExists() {
        val base = TestTrackFactory.savedTrackWithStartFinish(LocationSource.Simulated)
        val profile = TrackProfile(
            profileId = "v2-only-profile",
            name = "V2 Only Course",
            createdAtEpochMillis = 1_700_000_000_000L,
            source = SourceMetadata(
                source = LocationSource.Simulated,
                isSimulated = true,
                label = "Demo",
            ),
            revisions = listOf(
                TrackRevision(
                    revisionId = "v2-only-profile:r1",
                    ordinal = 1,
                    createdAtEpochMillis = 1_700_000_000_000L,
                    sourceMarkingSessionId = null,
                    referenceLine = replayReferenceLine(),
                    courseSetup = CourseSetup(startFinish = base.startFinish),
                    geometryCompatibilityId = "v2-only-profile:g1",
                ),
            ),
            preferredDirection = CourseDirection.Reverse,
        )
        store.saveProfile(profile, app)
        store.setCurrentSelection(
            CurrentTrackSelection(
                profileId = profile.profileId,
                direction = CourseDirection.Reverse,
            ),
        )

        val controller = controller()
        val result = controller.startTiming(profile.profileId)

        assertIs<StartTimingResult.Started>(result)
        val session = assertNotNull(controller.recorderForTest()).session
        assertEquals(profile.profileId, session.trackId)
        assertEquals(profile.name, session.trackName)
        assertEquals(CourseDirection.Reverse, session.direction)
        assertEquals("v2-only-profile:g1", session.courseCompatibilityKey.geometryCompatibilityId)
    }

    @Test
    fun startTimingPrefersLatestV2ProfileOverStaleLegacyTrack() {
        val legacy = TestTrackFactory.savedTrackWithStartFinish(LocationSource.Simulated)
            .copy(id = "dual-format-track", name = "Legacy Name")
        store.saveTrackBundle(legacy, TestTrackFactory.markingFor(legacy), app)
        val profile = TrackProfile(
            profileId = legacy.id,
            name = "Revised Profile Name",
            createdAtEpochMillis = legacy.createdAtEpochMillis,
            source = legacy.source,
            revisions = listOf(
                TrackRevision(
                    revisionId = "${legacy.id}:r2",
                    ordinal = 2,
                    createdAtEpochMillis = legacy.createdAtEpochMillis + 1,
                    sourceMarkingSessionId = legacy.sourceMarkingSessionId,
                    referenceLine = replayReferenceLine(),
                    courseSetup = CourseSetup(startFinish = legacy.startFinish),
                    geometryCompatibilityId = "${legacy.id}:g2",
                ),
            ),
        )
        store.saveProfile(profile, app)
        store.setCurrentSelection(CurrentTrackSelection(profile.profileId, CourseDirection.Recorded))

        val controller = controller()
        assertIs<StartTimingResult.Started>(controller.startTiming(profile.profileId))

        val session = assertNotNull(controller.recorderForTest()).session
        assertEquals("Revised Profile Name", session.trackName)
        assertEquals("${legacy.id}:g2", session.courseCompatibilityKey.geometryCompatibilityId)
    }

    @Test
    fun selectionPreflightOverrideTimingSaveAndRelaunchStayOneVerticalFlow() {
        val track = saveSelectedReplayProfile(direction = CourseDirection.Recorded)
        val controller = controller()

        val blocked = assertIs<StartTimingResult.WrongCourseBlocked>(
            controller.startTiming(
                trackId = track.id,
                latestGps = GpsFixtureLibrary.wrongCourseFarCourse(),
                preflightNowElapsedMillis = 20_000L,
            ),
        )
        assertEquals(STILL_USE_THIS_TRACK_ACTION, blocked.actionLabel)
        assertTrue(blocked.distanceMeters > blocked.thresholdMeters)
        assertNull(controller.recorderForTest(), "blocked preflight must happen before recorder construction")

        assertIs<StartTimingResult.Started>(controller.overrideWrongCourseAndStart())
        val activeSession = assertNotNull(controller.recorderForTest()).session
        assertEquals(CourseDirection.Recorded, activeSession.direction)
        assertEquals(track.id, activeSession.courseCompatibilityKey.profileId)
        assertEquals(
            CoursePreflightDisposition.Overridden,
            activeSession.coursePreflight.disposition,
        )
        assertTrue(activeSession.coursePreflight.overrideUsed)

        val replay = ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L, 36_000L))
        val excursionIndex = 12
        var beforeExcursion: LiveDeltaSnapshot? = null
        var duringExcursion: LiveDeltaSnapshot? = null
        var afterExcursion: LiveDeltaSnapshot? = null
        replay.forEachIndexed { index, sample ->
            val input = if (index == excursionIndex) {
                sample.copy(
                    latitude = sample.latitude + 1_000.0 / 111_320.0,
                    longitude = sample.longitude + 1_000.0 / 111_320.0,
                )
            } else {
                sample
            }
            controller.ingestSample(input)
            when (index) {
                excursionIndex - 1 -> beforeExcursion = controller.liveDelta()
                excursionIndex -> duringExcursion = controller.liveDelta()
                excursionIndex + 1 -> afterExcursion = controller.liveDelta()
            }
        }

        assertIs<LiveDeltaSnapshot.Available>(beforeExcursion)
        assertEquals(
            DeltaUnavailableReason.UnmatchedProgress,
            assertIs<LiveDeltaSnapshot.Unavailable>(duringExcursion).reason,
        )
        assertIs<LiveDeltaSnapshot.Available>(afterExcursion)
        assertEquals(replay.size, controller.timingRunSnapshot().checkpointedSampleCount)
        assertTrue(controller.timingRunSnapshot().lapCount >= 2)
        assertTrue(assertNotNull(controller.recorderForTest()).sectorResultCount >= 6)
        assertEquals(
            CoursePreflightDisposition.Overridden,
            controller.recorderForTest()!!.session.coursePreflight.disposition,
            "active off-course samples must never rerun or replace preflight evidence",
        )

        controller.stop()
        val saved = assertIs<SaveDraftResult.Saved>(controller.saveStoppedDraft())
        val payload = assertIs<LoadResult.Loaded<TimingSessionPayloadV1>>(
            store.loadTimingSession(saved.sessionId),
        ).value
        assertTrue(payload.session.coursePreflight.overrideUsed)
        assertEquals(replay.size, payload.samples.size)
        assertTrue(payload.laps.size >= 2)
        assertTrue(payload.sectorResults.size >= 6)

        val relaunchedSelection = assertIs<CurrentProfileResolution.Selected>(
            TrackProfileController(store).resolveCurrent(),
        )
        assertEquals(track.id, relaunchedSelection.profile.profileId)
        assertEquals(CourseDirection.Recorded, relaunchedSelection.direction)
        assertEquals(
            activeSession.courseCompatibilityKey,
            payload.session.courseCompatibilityKey,
            "save/relaunch must preserve the exact compatibility key",
        )
    }

    @Test
    fun requireReadyStartAllowsLowRateAndPoorAccuracyWarnings() {
        val track = saveSelectedReplayProfile(direction = CourseDirection.Recorded)
        val controller = controller()
        val nearCourse = point(eastMeters = 0.0, northMeters = 0.0)
        val latest = LocationSample(
            elapsedMillis = 20_000L,
            latitude = nearCourse.latitude,
            longitude = nearCourse.longitude,
            horizontalAccuracyMeters = 120.0,
            speedMetersPerSecond = 0.0,
            headingDegrees = null,
            altitudeMeters = null,
            source = LocationSource.PhoneGps,
        )

        val result = controller.startTiming(
            trackId = track.id,
            latestGps = latest,
            preflightNowElapsedMillis = latest.elapsedMillis,
            recentRateHz = 0.2,
            requireReady = true,
        )

        assertIs<StartTimingResult.Started>(result)
        assertNotNull(controller.recorderForTest(), "warning-quality GPS must not block Start Timing")
    }

    private fun controller(): SessionController = SessionController(
        store = store,
        appMetadata = app,
        engineConfig = LapEngineConfig.lenientForTests(),
        now = { 1_700_000_005_000L },
        sourceForTrack = {
            SourceMetadata(
                source = LocationSource.Simulated,
                isSimulated = true,
                label = "Demo",
            )
        },
    )

    private fun saveSelectedReplayProfile(direction: CourseDirection): Track {
        val base = TestTrackFactory.savedTrackWithStartFinish(LocationSource.Simulated)
        val track = base.copy(
            id = "profile-course-preflight",
            name = "Private Test Course",
            sourceMarkingSessionId = "mark-course-preflight",
            referenceLine = replayReferenceLine(),
        )
        store.saveTrackBundle(track, TestTrackFactory.markingFor(track), app)
        val created = assertIs<CreateProfileResult.Created>(
            TrackProfileController(store).saveProfile(track, track.name, app),
        )
        store.setCurrentSelection(
            CurrentTrackSelection(
                profileId = created.profile.profileId,
                direction = direction,
            ),
        )
        return track
    }

    private fun replayReferenceLine(): TrackReferenceLine = TrackReferenceLine(
        points = listOf(
            point(eastMeters = -20.0, northMeters = 0.0),
            point(eastMeters = 130.0, northMeters = 0.0),
            point(eastMeters = 130.0, northMeters = -50.0),
            point(eastMeters = -20.0, northMeters = -50.0),
        ),
        isClosed = true,
    )

    private fun point(eastMeters: Double, northMeters: Double): GeoPointDto = GeoPointDto(
        latitude = northMeters / 111_320.0,
        longitude = eastMeters / 111_320.0,
    )
}
