package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.lap.LapEngineConfig
import com.huanfuli.lapsight.shared.nowEpochMillis
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.Track

/**
 * Plain-Kotlin controller for the formal timing-session lifecycle (D-13..D-20,
 * SESS-01).
 *
 * Owns no Compose dependency. [startTiming] loads a saved [Track], derives a
 * [CourseDefinition][com.huanfuli.lapsight.shared.lap.CourseDefinition] from the
 * Track's start/finish + sectors, constructs a [TimingSessionRecorder] over the
 * existing [LapEngine][com.huanfuli.lapsight.shared.lap.LapEngine], and creates an
 * [active draft][TimingDraftState.ActiveDraft]. It rejects a missing Track or a
 * Track without a confirmed start/finish with the exact UI-SPEC copy (D-19).
 *
 * After [stop], the user must explicitly [saveStoppedDraft] or [discardDraft]
 * before formal Review history is created (D-14, D-16).
 * [loadUnfinishedDraft] surfaces a recovery prompt on app launch (D-15).
 *
 * @param store local-first persistence for draft checkpoints, saved sessions,
 *   and the ghost-candidate derivation.
 * @param appMetadata captured on every save (D-25).
 * @param engineConfig lap-engine tuning; tests pass [LapEngineConfig.lenientForTests].
 * @param now clock used for ids/timestamps; injectable for deterministic tests.
 * @param sourceForTrack derives the [SourceMetadata] for a saved Track so the
 *   demo boundary stays explicit (D-42, D-43).
 */
class SessionController(
    private val store: LocalSessionStore,
    private val appMetadata: AppMetadata = AppMetadata(appVersion = "0.3.0"),
    private val engineConfig: LapEngineConfig = LapEngineConfig(),
    private val now: () -> Long = ::nowEpochMillisSafe,
    private val sourceForTrack: (Track) -> SourceMetadata = { track ->
        SourceMetadata(
            source = track.source.source,
            isSimulated = track.source.isSimulated,
            label = track.source.label,
        )
    },
) {
    private var recorder: TimingSessionRecorder? = null
    private var session: TimingSession? = null

    /**
     * Current immutable snapshot of the active draft, or null when no draft is
     * active/stopped.
     */
    fun snapshot(): SessionControllerSnapshot {
        val rec = recorder ?: return SessionControllerSnapshot(null)
        val payload = store.loadUnfinishedDraft() ?: return SessionControllerSnapshot(null)
        val state = if (rec.session.id == payload.session.id) {
            // If the recorder is still alive, the draft is active or stopped.
            // We infer "stopped" only after [stop] has been called; the
            // controller tracks that explicitly via the stopped flag.
            if (stopped) TimingDraftState.StoppedPendingSave else TimingDraftState.ActiveDraft
        } else {
            TimingDraftState.ActiveDraft
        }
        val draft = TimingDraftSnapshot(
            state = state,
            sessionId = payload.session.id,
            trackId = payload.session.trackId,
            trackName = payload.session.trackName,
            source = payload.session.source,
            checkpointedSampleCount = payload.samples.size,
            checkpointedLapCount = payload.laps.size,
            checkpointedSectorEventCount = payload.sectorEvents.size,
        )
        return SessionControllerSnapshot(draft)
    }

    private var stopped: Boolean = false

    /**
     * Start formal timing for [trackId]. Returns [StartTimingResult.Blocked]
     * with the exact UI-SPEC copy when the Track does not exist or has no
     * confirmed start/finish (D-19).
     */
    fun startTiming(trackId: String): StartTimingResult {
        val track = loadTrackForTiming(trackId)
            ?: return StartTimingResult.Blocked(START_TIMING_BLOCKED_COPY)
        if (track.startFinish == null) {
            return StartTimingResult.Blocked(START_TIMING_BLOCKED_COPY)
        }
        val course = courseFromTrack(track.startFinish, track.sectors)
            ?: return StartTimingResult.Blocked(START_TIMING_BLOCKED_COPY)
        val createdAt = now()
        val session = TimingSession(
            id = "session-$createdAt",
            trackId = track.id,
            trackName = track.name,
            createdAtEpochMillis = createdAt,
            source = sourceForTrack(track),
            startFinish = track.startFinish,
            sectors = track.sectors,
        )
        val rec = TimingSessionRecorder(
            session = session,
            course = course,
            config = engineConfig,
            store = store,
            app = appMetadata,
        )
        this.session = session
        this.recorder = rec
        this.stopped = false
        // Persist the initial empty checkpoint so recovery sees the draft.
        store.saveTimingDraft(
            session = session,
            samples = emptyList(),
            laps = emptyList(),
            sectorEvents = emptyList(),
            gpsQuality = GpsQualitySummary(
                sampleCount = 0,
                averageAccuracyMeters = null,
                source = session.source.source,
            ),
            totalDurationMillis = 0L,
            app = appMetadata,
        )
        return StartTimingResult.Started(session.id)
    }

    /**
     * Stop the timing run and transition to stoppedPendingSave. The user must
     * explicitly save or discard afterwards (D-14).
     */
    fun stop() {
        val rec = recorder ?: return
        rec.stop()
        stopped = true
    }

    /**
     * Promote the stopped draft into formal Review history (D-14). Returns
     * [SaveDraftResult.NothingToSave] when no draft is stopped.
     */
    fun saveStoppedDraft(): SaveDraftResult {
        val payload = store.loadUnfinishedDraft()
            ?: return SaveDraftResult.NothingToSave
        store.saveTimingSession(payload, appMetadata)
        recorder = null
        session = null
        stopped = false
        return SaveDraftResult.Saved(payload.session.id)
    }

    /**
     * Discard the unfinished draft so it never enters Review history (D-16).
     */
    fun discardDraft() {
        store.discardTimingDraft()
        recorder = null
        session = null
        stopped = false
    }

    /**
     * On app launch, return a recovery prompt for an unfinished draft with
     * Resume / Save / Discard actions (D-15). Returns null when no draft exists.
     */
    fun loadUnfinishedDraft(): DraftRecoveryPrompt? {
        val payload = store.loadUnfinishedDraft() ?: return null
        val actions = if (payload.laps.isNotEmpty() || payload.samples.size > 1) {
            listOf(DraftRecoveryAction.Resume, DraftRecoveryAction.Save, DraftRecoveryAction.Discard)
        } else {
            listOf(DraftRecoveryAction.Resume, DraftRecoveryAction.Discard)
        }
        return DraftRecoveryPrompt(
            sessionId = payload.session.id,
            trackId = payload.session.trackId,
            trackName = payload.session.trackName,
            state = TimingDraftState.StoppedPendingSave,
            availableActions = actions,
        )
    }

    /**
     * Handle a recovery action from [loadUnfinishedDraft]. Resume restarts the
     * recorder over the saved draft state; Save/Discard behave like the live
     * controller methods (D-15).
     */
    fun handleRecoveryAction(
        prompt: DraftRecoveryPrompt,
        action: DraftRecoveryAction,
    ): SaveDraftResult {
        return when (action) {
            DraftRecoveryAction.Resume -> {
                val payload = store.loadUnfinishedDraft() ?: return SaveDraftResult.NothingToSave
                val track = loadTrackForTiming(payload.session.trackId)
                if (track?.startFinish != null) {
                    val course = courseFromTrack(track.startFinish, track.sectors)!!
                    val rec = TimingSessionRecorder(
                        session = payload.session,
                        course = course,
                        config = engineConfig,
                        store = store,
                        app = appMetadata,
                    )
                    // Replay the already-captured samples so the in-memory engine
                    // state catches up to the persisted draft.
                    payload.samples.forEach { rec.onSample(it.toModel()) }
                    this.recorder = rec
                    this.session = payload.session
                    this.stopped = false
                }
                SaveDraftResult.Saved(prompt.sessionId)
            }
            DraftRecoveryAction.Save -> saveStoppedDraft()
            DraftRecoveryAction.Discard -> {
                discardDraft()
                SaveDraftResult.NothingToSave
            }
        }
    }

    /**
     * Test-only accessor for the live recorder so tests can feed samples after
     * [startTiming] returns. Not for production use.
     */
    fun recorderForTest(): TimingSessionRecorder? = recorder

    private fun loadTrackForTiming(trackId: String): Track? {
        val result = store.loadTrack(trackId)
        return (result as? com.huanfuli.lapsight.shared.storage.LoadResult.Loaded)?.value?.track
    }
}

/** Snapshot of the controller's active draft, rendered by the Drive UI. */
data class SessionControllerSnapshot(val activeDraft: TimingDraftSnapshot?)

/** Exact UI-SPEC copy shown while Start Timing is blocked (D-19). */
const val START_TIMING_BLOCKED_COPY: String =
    "Mark a track first. Timing needs a saved start/finish line."

/**
 * Default clock for the controller. Indirection so tests can inject a fixed
 * `now` without depending on the platform actual at construction time.
 */
private fun nowEpochMillisSafe(): Long = try {
    nowEpochMillis()
} catch (e: Throwable) {
    0L
}
