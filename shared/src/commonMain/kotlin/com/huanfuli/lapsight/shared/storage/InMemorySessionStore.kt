package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GhostCandidate
import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.GpsQualitySummary
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SectorEventDto
import com.huanfuli.lapsight.shared.session.SectorResultDto
import com.huanfuli.lapsight.shared.session.TimingSession
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.track.ReviewIndexRow
import com.huanfuli.lapsight.shared.track.Track
import com.huanfuli.lapsight.shared.track.TrackMarkingPayloadV1
import com.huanfuli.lapsight.shared.track.TrackMarkingSession
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2
import com.huanfuli.lapsight.shared.track.TimingSessionPayloadV2
import com.huanfuli.lapsight.shared.track.TrackProfile

/**
 * In-memory [LocalSessionStore] used as the default for Compose previews, tests,
 * and any context that has no platform storage root wired (D-21).
 *
 * It mirrors [FileSessionStore]'s contract — payload-before-index upsert order,
 * typed load results, schemaVersion validation — without touching the file
 * system. This is the store `App()` defaults to so previews/tests need no
 * `StoragePaths` initialization; the Android entrypoint injects a
 * `FileSessionStore` over the real app-private root.
 */
class InMemorySessionStore : LocalSessionStore {

    private val tracks = LinkedHashMap<String, TrackPayloadV1>()
    private val markings = LinkedHashMap<String, TrackMarkingPayloadV1>()
    private val sessions = LinkedHashMap<String, TimingSessionPayloadV1>()
    private var activeDraft: TimingSessionPayloadV1? = null
    private val rows = mutableListOf<ReviewIndexRow>()

    /** Reference laps keyed by "<trackId>__real|sim" so source slots stay apart (D-04). */
    private val references = LinkedHashMap<String, GhostReferencePayloadV1>()

    // V2 side-by-side state (Plan 05-02); the V1 maps above are retained untouched.
    private val profiles = LinkedHashMap<String, TrackProfile>()
    private val sessionsV2 = LinkedHashMap<String, TimingSessionPayloadV2>()
    private val referencesV2 = LinkedHashMap<String, GhostReferencePayloadV2>()

    /** Commit marker mirroring the file store's profile index (index-last semantics). */
    private val profileIndex = mutableListOf<String>()

    /** The single persisted explicit current-Track selection (D-01); null when unset. */
    private var currentSelection: CurrentTrackSelection? = null

    override fun saveTrackBundle(track: Track, marking: TrackMarkingSession, app: AppMetadata): SaveResult {
        // Payloads first (mirrors FileSessionStore), then the index row.
        markings[marking.id] = TrackMarkingPayloadV1(marking = marking, app = app)
        tracks[track.id] = TrackPayloadV1(track = track, app = app)
        upsertRows(
            listOf(
                ReviewIndexRow(
                    id = marking.id,
                    type = ReviewEntryType.TrackMarking,
                    name = track.name,
                    createdAtEpochMillis = marking.createdAtEpochMillis,
                    source = marking.source,
                    payloadPath = "markings/${marking.id}.json",
                    sampleCount = marking.samples.size,
                ),
                ReviewIndexRow(
                    id = track.id,
                    type = ReviewEntryType.Track,
                    name = track.name,
                    createdAtEpochMillis = track.createdAtEpochMillis,
                    source = track.source,
                    payloadPath = "tracks/${track.id}.json",
                ),
            ),
        )
        return SaveResult.Saved(
            trackPath = "tracks/${track.id}.json",
            markingPath = "markings/${marking.id}.json",
        )
    }

    override fun loadTrack(trackId: String): LoadResult<TrackPayloadV1> {
        val payload = tracks[trackId] ?: return LoadResult.NotFound
        val problem = validateSchema(payload.schemaVersion, payload.track.id.isBlank(), "missing track id")
        return if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(payload)
    }

    override fun loadTrackMarking(markingId: String): LoadResult<TrackMarkingPayloadV1> {
        val payload = markings[markingId] ?: return LoadResult.NotFound
        val badCoords = payload.marking.samples.any { !it.latitude.isFinite() || !it.longitude.isFinite() }
        val problem = when {
            payload.schemaVersion != CURRENT_TRACK_SCHEMA_VERSION -> "unsupported schemaVersion ${payload.schemaVersion}"
            payload.marking.id.isBlank() -> "missing marking id"
            badCoords -> "non-finite sample coordinates"
            else -> null
        }
        return if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(payload)
    }

    override fun readIndex(): ReviewIndex = ReviewIndex(rows = rows.toList())

    override fun saveTimingDraft(
        session: TimingSession,
        samples: List<LocationSampleDto>,
        laps: List<LapDto>,
        sectorEvents: List<SectorEventDto>,
        gpsQuality: GpsQualitySummary,
        totalDurationMillis: Long,
        app: AppMetadata,
        sectorResults: List<SectorResultDto>,
    ) {
        activeDraft = TimingSessionPayloadV1(
            session = session,
            app = app,
            samples = samples,
            laps = laps,
            sectorEvents = sectorEvents,
            gpsQuality = gpsQuality,
            totalDurationMillis = totalDurationMillis,
            sectorResults = sectorResults,
        )
    }

    override fun loadUnfinishedDraft(): TimingSessionPayloadV1? = activeDraft

    override fun saveTimingSession(payload: TimingSessionPayloadV1, app: AppMetadata): SaveResult {
        // Canonical payload first, then the index row (mirrors FileSessionStore).
        sessions[payload.session.id] = payload
        upsertRows(
            listOf(
                ReviewIndexRow(
                    id = payload.session.id,
                    type = ReviewEntryType.TimingSession,
                    name = payload.session.trackName,
                    createdAtEpochMillis = payload.session.createdAtEpochMillis,
                    source = payload.session.source,
                    payloadPath = "sessions/${payload.session.id}.json",
                    sampleCount = payload.samples.size,
                    bestLapMillis = payload.laps.minByOrNull { it.durationMillis }?.durationMillis,
                ),
            ),
        )
        activeDraft = null
        return SaveResult.Saved(
            trackPath = "sessions/${payload.session.id}.json",
            markingPath = "drafts/timing.json",
        )
    }

    override fun discardTimingDraft() {
        activeDraft = null
    }

    override fun loadTimingSession(sessionId: String): LoadResult<TimingSessionPayloadV1> {
        val payload = sessions[sessionId] ?: return LoadResult.NotFound
        val problem = when {
            payload.schemaVersion != CURRENT_SESSION_SCHEMA_VERSION ->
                "unsupported schemaVersion ${payload.schemaVersion}"
            payload.session.id.isBlank() -> "missing session id"
            payload.session.trackId.isBlank() -> "missing track id"
            else -> null
        }
        return if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(payload)
    }

    override fun ghostCandidateForTrack(trackId: String): GhostCandidate? {
        var best: GhostCandidate? = null
        for ((_, payload) in sessions) {
            if (payload.session.trackId != trackId) continue
            // Real candidates EXCLUDE simulated sessions (D-20, D-43).
            if (payload.session.source.isSimulated) continue
            val bestLap = payload.laps.minByOrNull { it.durationMillis } ?: continue
            val candidate = GhostCandidate(
                trackId = trackId,
                sessionId = payload.session.id,
                lapNumber = bestLap.lapNumber,
                lapDurationMillis = bestLap.durationMillis,
            )
            if (best == null || candidate.lapDurationMillis < best!!.lapDurationMillis) {
                best = candidate
            }
        }
        return best
    }

    override fun loadReferenceLap(trackId: String, isSimulated: Boolean): LoadResult<GhostReferencePayloadV1> {
        val payload = references[referenceKey(trackId, isSimulated)] ?: return LoadResult.NotFound
        val problem = when {
            payload.schemaVersion != CURRENT_GHOST_REFERENCE_SCHEMA_VERSION ->
                "unsupported schemaVersion ${payload.schemaVersion}"
            payload.trackId.isBlank() -> "missing track id"
            payload.trackId != trackId -> "track id mismatch"
            payload.source.isSimulated != isSimulated -> "source boundary mismatch"
            payload.progressPoints.size < 2 -> "reference progress curve too short"
            else -> null
        }
        return if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(payload)
    }

    override fun saveReferenceLap(payload: GhostReferencePayloadV1, app: AppMetadata): SaveResult {
        val key = referenceKey(payload.trackId, payload.source.isSimulated)
        references[key] = payload
        return SaveResult.Saved(trackPath = "references/$key.json", markingPath = "references/$key.json")
    }

    // --- V2 course profiles + side-by-side migration (D-12..D-14) -----------

    override fun migrate(app: AppMetadata): MigrationResult {
        val skipped = mutableListOf<MigrationSkip>()
        val migratedProfileIds = mutableListOf<String>()

        // Route every source payload through the SAME SchemaMigrations dispatch the
        // file store uses (encode the held V1 object, decode through version dispatch),
        // so the externally observable migration result is identical (D-12).
        var profilesMigrated = 0
        for ((_, v1) in tracks.toList()) {
            val text = FileSessionStore.canonicalJson.encodeToString(TrackPayloadV1.serializer(), v1)
            when (val decoded = SchemaMigrations.decodeTrackProfile(text)) {
                is LoadResult.Loaded -> {
                    profiles[decoded.value.profileId] = decoded.value
                    migratedProfileIds += decoded.value.profileId
                    profilesMigrated++
                }
                is LoadResult.Corrupt -> skipped += MigrationSkip(v1.track.id, decoded.reason)
                LoadResult.NotFound -> Unit
            }
        }

        var sessionsMigrated = 0
        for ((_, v1) in sessions.toList()) {
            val text = FileSessionStore.canonicalJson.encodeToString(TimingSessionPayloadV1.serializer(), v1)
            when (val decoded = SchemaMigrations.decodeTimingSession(text)) {
                is LoadResult.Loaded -> {
                    sessionsV2[decoded.value.session.id] = decoded.value
                    sessionsMigrated++
                }
                is LoadResult.Corrupt -> skipped += MigrationSkip(v1.session.id, decoded.reason)
                LoadResult.NotFound -> Unit
            }
        }

        var referencesMigrated = 0
        for ((key, v1) in references.toList()) {
            val text = FileSessionStore.canonicalJson.encodeToString(GhostReferencePayloadV1.serializer(), v1)
            when (val decoded = SchemaMigrations.decodeGhostReference(text)) {
                is LoadResult.Loaded -> {
                    referencesV2[key] = decoded.value
                    referencesMigrated++
                }
                is LoadResult.Corrupt -> skipped += MigrationSkip(v1.trackId, decoded.reason)
                LoadResult.NotFound -> Unit
            }
        }

        // Index last (commit point); migration never records a current selection.
        for (id in migratedProfileIds) if (id !in profileIndex) profileIndex += id

        return MigrationResult(profilesMigrated, sessionsMigrated, referencesMigrated, skipped)
    }

    override fun saveProfile(profile: TrackProfile, app: AppMetadata): SaveResult {
        require(SchemaMigrations.isSafeId(profile.profileId)) { "unsafe profile id" }
        profiles[profile.profileId] = profile
        if (profile.profileId !in profileIndex) profileIndex += profile.profileId
        return SaveResult.Saved(
            trackPath = "profiles/${profile.profileId}.json",
            markingPath = "profiles-index.json",
        )
    }

    override fun loadProfile(profileId: String): LoadResult<TrackProfile> {
        if (!SchemaMigrations.isSafeId(profileId)) return LoadResult.Corrupt("unsafe profile id")
        val profile = profiles[profileId] ?: return LoadResult.NotFound
        return LoadResult.Loaded(profile)
    }

    override fun listActiveProfiles(): List<TrackProfile> =
        profileIndex.mapNotNull { profiles[it] }.filterNot { it.isArchived }

    // --- Explicit current-Track selection (D-01..D-04) ----------------------

    override fun loadCurrentSelection(): LoadResult<CurrentTrackSelection> {
        val selection = currentSelection ?: return LoadResult.NotFound
        // Mirror the file store's defense in depth: an unsafe persisted id is corrupt.
        if (selection.profileId != null && !SchemaMigrations.isSafeId(selection.profileId)) {
            return LoadResult.Corrupt("unsafe profile id")
        }
        return LoadResult.Loaded(selection)
    }

    override fun setCurrentSelection(selection: CurrentTrackSelection) {
        require(selection.profileId == null || SchemaMigrations.isSafeId(selection.profileId)) {
            "unsafe profile id"
        }
        currentSelection = selection
    }

    override fun clearCurrentSelection() {
        currentSelection = null
    }

    private fun referenceKey(trackId: String, isSimulated: Boolean): String =
        "${trackId}__${if (isSimulated) "sim" else "real"}"

    private fun upsertRows(newRows: List<ReviewIndexRow>) {
        val keys = newRows.map { it.id to it.type }.toSet()
        rows.removeAll { (it.id to it.type) in keys }
        rows += newRows
    }

    private fun validateSchema(schemaVersion: Int, idMissing: Boolean, idMessage: String): String? = when {
        schemaVersion != CURRENT_TRACK_SCHEMA_VERSION -> "unsupported schemaVersion $schemaVersion"
        idMissing -> idMessage
        else -> null
    }
}
