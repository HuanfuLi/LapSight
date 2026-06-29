package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.SimulatedGpsProvider
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.lap.ReplayFixtures
import com.huanfuli.lapsight.shared.session.SessionControllerTest.TestTrackFactory.savedTrackWithStartFinish
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.track.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wave 0 (Plan 03-06 Task 1) coverage for draft recovery on app launch (D-15).
 *
 * Asserts:
 *  - An unfinished active/stopped draft persists across controller recreation and
 *    surfaces a recovery prompt state with Resume / Save / Discard actions (D-15).
 *  - Recovery does not silently promote a draft into Review history (D-16).
 *  - Missing/corrupt drafts do not crash recovery.
 */
class DraftRecoveryTest {

    private val store = InMemorySessionStore()
    private val app = AppMetadata(appVersion = "0.3.0", platform = "test")

    private fun newController(source: LocationSource = LocationSource.Simulated): SessionController =
        SessionController(
            store = store,
            appMetadata = app,
            engineConfig = LapEngineConfig.lenientForTests(),
            now = { 1_700_000_000_000L },
            sourceForTrack = { _ ->
                SourceMetadata(
                    source = source,
                    isSimulated = source == LocationSource.Simulated,
                    label = if (source == LocationSource.Simulated) "Demo" else null,
                )
            },
        )

    private fun saveRealTrack(source: LocationSource = LocationSource.Simulated): Track {
        val track = savedTrackWithStartFinish(source)
        store.saveTrackBundle(
            track,
            SessionControllerTest.TestTrackFactory.markingFor(track, source),
            app,
        )
        return track
    }

    @Test
    fun appLaunchWithUnfinishedDraftReturnsRecoveryPromptWithResumeSaveDiscard() {
        val track = saveRealTrack()
        // First "session" of the app: start timing, feed samples, stop but do NOT save.
        val controllerA = newController()
        val provider = SimulatedGpsProvider(scenarioId = GpsFixtureLibrary.CLEAN_10_LOOP)
        controllerA.startTiming(track.id)
        val recorder = controllerA.recorderForTest()!!
        provider.start(); repeat(480) { provider.nextSample()?.let { recorder.onSample(it) } }
        controllerA.stop()
        assertTrue(
            recorder.checkpointCount < 100,
            "checkpointing must be batched instead of rewriting the full draft for every sample",
        )

        // App restart: a fresh controller loads the unfinished draft from storage.
        val controllerB = newController()
        val recovery = controllerB.loadUnfinishedDraft()
        assertNotNull(recovery, "an unfinished draft must be recoverable on launch (D-15)")
        assertEquals(TimingDraftState.StoppedPendingSave, recovery.state)
        // The prompt offers Resume / Save / Discard — never auto-promotes to history.
        assertEquals(
            setOf(DraftRecoveryAction.Resume, DraftRecoveryAction.Save, DraftRecoveryAction.Discard),
            recovery.availableActions.toSet(),
        )
        // No Review history row exists yet (D-16).
        assertFalse(
            store.readIndex().rows.any { it.type == com.huanfuli.lapsight.shared.track.ReviewEntryType.TimingSession },
            "recovery must not auto-promote a draft into history",
        )

        controllerB.handleRecoveryAction(recovery, DraftRecoveryAction.Resume)
        assertEquals(
            0,
            controllerB.recorderForTest()!!.checkpointCount,
            "replaying persisted samples must not rewrite the draft once per historical sample",
        )
    }

    @Test
    fun recoverySaveActionPersistsSessionToHistory() {
        val track = saveRealTrack()
        val controllerA = newController()
        val provider = SimulatedGpsProvider(scenarioId = GpsFixtureLibrary.CLEAN_10_LOOP)
        controllerA.startTiming(track.id)
        val recorder = controllerA.recorderForTest()!!
        provider.start(); repeat(480) { provider.nextSample()?.let { recorder.onSample(it) } }
        controllerA.stop()

        val controllerB = newController()
        val recovery = controllerB.loadUnfinishedDraft()
        assertNotNull(recovery)
        val result = controllerB.handleRecoveryAction(recovery, DraftRecoveryAction.Save)

        assertTrue(result is SaveDraftResult.Saved)
        assertTrue(
            store.readIndex().rows.any { it.id == result.sessionId && it.type == com.huanfuli.lapsight.shared.track.ReviewEntryType.TimingSession },
            "Save action persists the draft into Review history",
        )
    }

    @Test
    fun recoveryDiscardActionRemovesDraftAndDoesNotEnterHistory() {
        val track = saveRealTrack()
        val controllerA = newController()
        val provider = SimulatedGpsProvider(scenarioId = GpsFixtureLibrary.CLEAN_10_LOOP)
        controllerA.startTiming(track.id)
        val recorder = controllerA.recorderForTest()!!
        provider.start(); repeat(480) { provider.nextSample()?.let { recorder.onSample(it) } }
        controllerA.stop()

        val controllerB = newController()
        val recovery = controllerB.loadUnfinishedDraft()
        assertNotNull(recovery)
        controllerB.handleRecoveryAction(recovery, DraftRecoveryAction.Discard)

        assertFalse(
            store.readIndex().rows.any { it.type == com.huanfuli.lapsight.shared.track.ReviewEntryType.TimingSession },
            "Discarded recovery draft must not enter history (D-16)",
        )
        assertNull(controllerB.loadUnfinishedDraft(), "no lingering draft after discard")
    }

    @Test
    fun resumedDraftReachesSameEngineStateAsFreshReplayOfSameSamples() {
        val track = saveRealTrack()

        // Session A: time a run and stop WITHOUT saving. The canonical multi-lap loop
        // matches the saved Track's (DEMO) start/finish so real laps + sectors are
        // produced; the exact samples are reused for the fresh-replay comparison.
        val fed = ReplayFixtures.multiLapLoop(listOf(40_000L, 32_000L, 36_000L))
        val controllerA = newController()
        controllerA.startTiming(track.id)
        val recorderA = controllerA.recorderForTest()!!
        fed.forEach { recorderA.onSample(it) }
        controllerA.stop()

        // App restart: resume the unfinished draft (replays the persisted samples).
        val controllerB = newController()
        val recovery = controllerB.loadUnfinishedDraft()
        assertNotNull(recovery)
        controllerB.handleRecoveryAction(recovery, DraftRecoveryAction.Resume)
        val resumedState = controllerB.recorderForTest()!!.timingState

        // A fresh, independent replay of the SAME samples through a brand-new pipeline.
        val controllerC = newController()
        controllerC.startTiming(track.id)
        val recorderC = controllerC.recorderForTest()!!
        fed.forEach { recorderC.onSample(it) }
        val freshState = recorderC.timingState

        // D-25/D-28: a resumed mid-draft session must yield the SAME engine state as a
        // fresh replay of the same samples — recovery introduces no algorithmic drift.
        assertEquals(
            freshState,
            resumedState,
            "a resumed draft must reach the identical engine state as a fresh replay (D-25/D-28)",
        )
        assertTrue(freshState.lapCount >= 1, "the partial run must complete laps so the check is meaningful")
    }

    @Test
    fun recoveryReturnsNullWhenNoDraftExists() {
        val controller = newController()
        assertNull(controller.loadUnfinishedDraft(), "no recovery prompt when there is no unfinished draft")
    }
}
