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
import com.huanfuli.lapsight.shared.track.CreateProfileResult
import com.huanfuli.lapsight.shared.track.CurrentProfileResolution
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackProfileController
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
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
