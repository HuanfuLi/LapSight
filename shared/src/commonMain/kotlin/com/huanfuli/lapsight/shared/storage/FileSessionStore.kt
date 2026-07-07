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
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityKey
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityValidation
import com.huanfuli.lapsight.shared.ghost.GhostCompatibility
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
import com.huanfuli.lapsight.shared.track.TrackProfilePayloadV2
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path

/**
 * Okio-backed [LocalSessionStore] (D-21 through D-25).
 *
 * Layout under the injected app-private [root]:
 * ```
 * <root>/tracks/<trackId>.json       canonical Track payload
 * <root>/markings/<markingId>.json   canonical TrackMarkingSession payload
 * <root>/index.json                  lightweight Review metadata index
 * ```
 *
 * Every write goes to a sibling `*.tmp` file and is then atomically moved into
 * place to avoid partial-write corruption. Payloads are written before the index
 * so a failed index write never leaves a dangling index row (D-22, D-23). Loads
 * return a typed [LoadResult]; malformed JSON is reported as [LoadResult.Corrupt]
 * rather than crashing the caller (D-21). No database/ORM schema is created.
 */
class FileSessionStore(
    private val fileSystem: FileSystem,
    private val root: Path,
    private val json: Json = canonicalJson,
) : LocalSessionStore {

    private val tracksDir: Path get() = root / TRACKS_DIR
    private val markingsDir: Path get() = root / MARKINGS_DIR
    private val sessionsDir: Path get() = root / SESSIONS_DIR
    private val draftsDir: Path get() = root / DRAFTS_DIR
    private val referencesDir: Path get() = root / REFERENCES_DIR
    private val indexPath: Path get() = root / INDEX_FILE
    private val activeDraftPath: Path get() = draftsDir / ACTIVE_DRAFT_FILE

    // V2 side-by-side layout (D-12..D-14): written alongside the V1 originals above.
    private val profilesDir: Path get() = root / PROFILES_DIR
    private val sessionsV2Dir: Path get() = root / SESSIONS_V2_DIR
    private val referencesV2Dir: Path get() = root / REFERENCES_V2_DIR
    private val profilesIndexPath: Path get() = root / PROFILES_INDEX_FILE
    private val currentSelectionPath: Path get() = root / CURRENT_SELECTION_FILE

    /** Per-Track reference file, separated by source boundary (D-03, D-04). */
    private fun referencePath(trackId: String, isSimulated: Boolean): Path =
        referencesDir / "${trackId}__${if (isSimulated) SIM_SLOT else REAL_SLOT}.json"

    override fun saveTrackBundle(track: Track, marking: TrackMarkingSession, app: AppMetadata): SaveResult {
        val markingPayload = TrackMarkingPayloadV1(marking = marking, app = app)
        val trackPayload = TrackPayloadV1(track = track, app = app)

        val markingPath = markingsDir / "${marking.id}.json"
        val trackPath = tracksDir / "${track.id}.json"

        // Payloads first: write the recoverable evidence before touching the index.
        writeAtomically(markingPath, json.encodeToString(markingPayload))
        writeAtomically(trackPath, json.encodeToString(trackPayload))

        // Index last: a failure here leaves payloads intact and no dangling row.
        val updated = upsertRows(
            readIndex(),
            listOf(
                ReviewIndexRow(
                    id = marking.id,
                    type = ReviewEntryType.TrackMarking,
                    name = track.name,
                    createdAtEpochMillis = marking.createdAtEpochMillis,
                    source = marking.source,
                    payloadPath = "$MARKINGS_DIR/${marking.id}.json",
                    sampleCount = marking.samples.size,
                ),
                ReviewIndexRow(
                    id = track.id,
                    type = ReviewEntryType.Track,
                    name = track.name,
                    createdAtEpochMillis = track.createdAtEpochMillis,
                    source = track.source,
                    payloadPath = "$TRACKS_DIR/${track.id}.json",
                ),
            ),
        )
        writeAtomically(indexPath, json.encodeToString(updated))

        return SaveResult.Saved(
            trackPath = trackPath.toString(),
            markingPath = markingPath.toString(),
        )
    }

    override fun loadTrack(trackId: String): LoadResult<TrackPayloadV1> =
        load(tracksDir / "$trackId.json") { payload ->
            when {
                payload.schemaVersion != CURRENT_TRACK_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.track.id.isBlank() -> "missing track id"
                else -> null
            }
        }

    override fun loadTrackMarking(markingId: String): LoadResult<TrackMarkingPayloadV1> =
        load(markingsDir / "$markingId.json") { payload ->
            when {
                payload.schemaVersion != CURRENT_TRACK_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.marking.id.isBlank() -> "missing marking id"
                payload.marking.samples.any { !it.latitude.isFinite() || !it.longitude.isFinite() } ->
                    "non-finite sample coordinates"
                else -> null
            }
        }

    override fun readIndex(): ReviewIndex {
        if (!fileSystem.exists(indexPath)) return ReviewIndex()
        return try {
            val text = fileSystem.read(indexPath) { readUtf8() }
            json.decodeFromString<ReviewIndex>(text)
        } catch (e: SerializationException) {
            // A corrupt index must not crash list screens; treat as empty and let a
            // subsequent save rebuild it.
            ReviewIndex()
        } catch (e: IllegalArgumentException) {
            ReviewIndex()
        }
    }

    // --- Timing session drafts (D-13..D-20) --------------------------------

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
        val payload = TimingSessionPayloadV1(
            session = session,
            app = app,
            samples = samples,
            laps = laps,
            sectorEvents = sectorEvents,
            gpsQuality = gpsQuality,
            totalDurationMillis = totalDurationMillis,
            sectorResults = sectorResults,
        )
        // Drafts persist continuously (D-13). Atomic temp-write + move avoids
        // partial-write corruption on crash (Pitfall 2).
        writeAtomically(activeDraftPath, json.encodeToString(payload))
    }

    override fun loadUnfinishedDraft(): TimingSessionPayloadV1? {
        if (!fileSystem.exists(activeDraftPath)) return null
        return try {
            val text = fileSystem.read(activeDraftPath) { readUtf8() }
            json.decodeFromString<TimingSessionPayloadV1>(text)
        } catch (e: SerializationException) {
            // Corrupt/missing draft must not crash recovery (T-03-11).
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    override fun saveTimingSession(payload: TimingSessionPayloadV1, app: AppMetadata): SaveResult {
        val sessionPath = sessionsDir / "${payload.session.id}.json"
        // Canonical payload first (D-22, D-23), then the index row.
        writeAtomically(sessionPath, json.encodeToString(payload))
        val updated = upsertRows(
            readIndex(),
            listOf(
                ReviewIndexRow(
                    id = payload.session.id,
                    type = ReviewEntryType.TimingSession,
                    name = payload.session.trackName,
                    createdAtEpochMillis = payload.session.createdAtEpochMillis,
                    source = payload.session.source,
                    payloadPath = "$SESSIONS_DIR/${payload.session.id}.json",
                    sampleCount = payload.samples.size,
                    bestLapMillis = payload.laps.minByOrNull { it.durationMillis }?.durationMillis,
                ),
            ),
        )
        writeAtomically(indexPath, json.encodeToString(updated))
        // Clear the draft now that the canonical session is saved (D-14).
        if (fileSystem.exists(activeDraftPath)) {
            fileSystem.delete(activeDraftPath)
        }
        return SaveResult.Saved(
            trackPath = sessionPath.toString(),
            markingPath = activeDraftPath.toString(),
        )
    }

    override fun discardTimingDraft() {
        if (fileSystem.exists(activeDraftPath)) {
            fileSystem.delete(activeDraftPath)
        }
    }

    override fun loadTimingSession(sessionId: String): LoadResult<TimingSessionPayloadV1> =
        load(sessionsDir / "$sessionId.json") { payload ->
            when {
                payload.schemaVersion != CURRENT_SESSION_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.session.id.isBlank() -> "missing session id"
                payload.session.trackId.isBlank() -> "missing track id"
                else -> null
            }
        }

    override fun ghostCandidateForTrack(trackId: String): GhostCandidate? {
        val index = readIndex()
        val sessionIds = index.rows
            .filter {
                it.type == ReviewEntryType.TimingSession &&
                    it.name.isNotBlank() &&
                    // The index row does not carry trackId directly; we resolve
                    // candidates by scanning saved payloads and filtering on
                    // trackId + source. isSimulated lives on SourceMetadata.
                    true
            }
            .map { it.id }

        var best: GhostCandidate? = null
        for (id in sessionIds) {
            val payload = loadTimingSession(id)
            if (payload !is LoadResult.Loaded) continue
            val value = payload.value
            if (value.session.trackId != trackId) continue
            // Real candidates EXCLUDE simulated sessions (D-20, D-43).
            if (value.session.source.isSimulated) continue
            val bestLap = value.laps.minByOrNull { it.durationMillis } ?: continue
            val candidate = GhostCandidate(
                trackId = trackId,
                sessionId = value.session.id,
                lapNumber = bestLap.lapNumber,
                lapDurationMillis = bestLap.durationMillis,
            )
            if (best == null || candidate.lapDurationMillis < best!!.lapDurationMillis) {
                best = candidate
            }
        }
        return best
    }

    override fun loadReferenceLap(trackId: String, isSimulated: Boolean): LoadResult<GhostReferencePayloadV1> =
        load(referencePath(trackId, isSimulated)) { payload ->
            when {
                payload.schemaVersion != CURRENT_GHOST_REFERENCE_SCHEMA_VERSION ->
                    "unsupported schemaVersion ${payload.schemaVersion}"
                payload.trackId.isBlank() -> "missing track id"
                payload.trackId != trackId -> "track id mismatch"
                // Defense in depth: the stored source must match the requested
                // real/simulated boundary so a real lookup never returns a
                // simulated reference even if a file was misplaced (D-04, D-24).
                payload.source.isSimulated != isSimulated -> "source boundary mismatch"
                payload.progressPoints.size < 2 -> "reference progress curve too short"
                else -> null
            }
        }

    override fun saveReferenceLap(payload: GhostReferencePayloadV1, app: AppMetadata): SaveResult {
        val path = referencePath(payload.trackId, payload.source.isSimulated)
        writeAtomically(path, json.encodeToString(payload))
        return SaveResult.Saved(trackPath = path.toString(), markingPath = path.toString())
    }

    // --- V2 course profiles + side-by-side migration (D-12..D-14) -----------

    private fun profilePath(profileId: String): Path = profilesDir / "$profileId.json"

    override fun migrate(app: AppMetadata): MigrationResult {
        val skipped = mutableListOf<MigrationSkip>()
        val migratedProfileIds = mutableListOf<String>()

        // 1) Profiles from V1 Tracks. Decode + validate BEFORE writing any V2 payload
        //    (decode rejects unsafe ids / bad geometry, so no unsafe path is built).
        var profilesMigrated = 0
        for (path in listJsonPayloads(tracksDir)) {
            when (val decoded = SchemaMigrations.decodeTrackProfile(readText(path))) {
                is LoadResult.Loaded -> {
                    val profile = decoded.value
                    writeAtomically(
                        profilePath(profile.profileId),
                        json.encodeToString(TrackProfilePayloadV2.serializer(), TrackProfilePayloadV2(profile = profile, app = app)),
                    )
                    migratedProfileIds += profile.profileId
                    profilesMigrated++
                }
                is LoadResult.Corrupt -> skipped += MigrationSkip(path.name, decoded.reason)
                LoadResult.NotFound -> Unit
            }
        }

        // 2) Sessions from V1 timing sessions (their app metadata is preserved).
        var sessionsMigrated = 0
        for (path in listJsonPayloads(sessionsDir)) {
            when (val decoded = SchemaMigrations.decodeTimingSession(readText(path))) {
                is LoadResult.Loaded -> {
                    val payload = decoded.value
                    writeAtomically(
                        sessionsV2Dir / "${payload.session.id}.json",
                        json.encodeToString(TimingSessionPayloadV2.serializer(), payload),
                    )
                    sessionsMigrated++
                }
                is LoadResult.Corrupt -> skipped += MigrationSkip(path.name, decoded.reason)
                LoadResult.NotFound -> Unit
            }
        }

        // 3) Ghost references from V1 reference slots (real/sim slot preserved).
        var referencesMigrated = 0
        for (path in listJsonPayloads(referencesDir)) {
            when (val decoded = SchemaMigrations.decodeGhostReference(readText(path))) {
                is LoadResult.Loaded -> {
                    val payload = decoded.value
                    writeAtomically(referencePath(payload.compatibilityKey), json.encodeToString(GhostReferencePayloadV2.serializer(), payload))
                    referencesMigrated++
                }
                is LoadResult.Corrupt -> skipped += MigrationSkip(path.name, decoded.reason)
                LoadResult.NotFound -> Unit
            }
        }

        // 4) Index LAST: the single commit point. A fault before this leaves every V1
        //    original readable and the migration recoverable on retry (T-05-04). The
        //    profile index never records a current selection (D-01/D-04).
        val merged = (readProfileIndex().profileIds + migratedProfileIds).distinct()
        writeAtomically(profilesIndexPath, json.encodeToString(ProfileIndex.serializer(), ProfileIndex(profileIds = merged)))

        return MigrationResult(profilesMigrated, sessionsMigrated, referencesMigrated, skipped)
    }

    override fun saveProfile(profile: TrackProfile, app: AppMetadata): SaveResult {
        // Reject an unsafe id before any Okio path is built (T-05-03).
        require(SchemaMigrations.isSafeId(profile.profileId)) { "unsafe profile id" }
        val path = profilePath(profile.profileId)
        // Payload first, then the index (mirrors saveTrackBundle ordering).
        writeAtomically(path, json.encodeToString(TrackProfilePayloadV2.serializer(), TrackProfilePayloadV2(profile = profile, app = app)))
        val index = readProfileIndex()
        if (profile.profileId !in index.profileIds) {
            writeAtomically(
                profilesIndexPath,
                json.encodeToString(ProfileIndex.serializer(), index.copy(profileIds = index.profileIds + profile.profileId)),
            )
        }
        val updated = upsertRows(
            readIndex(),
            listOf(
                ReviewIndexRow(
                    id = profile.profileId,
                    type = ReviewEntryType.Track,
                    name = profile.name,
                    createdAtEpochMillis = profile.createdAtEpochMillis,
                    source = profile.source,
                    payloadPath = "$PROFILES_DIR/${profile.profileId}.json",
                )
            )
        )
        writeAtomically(indexPath, json.encodeToString(updated))
        return SaveResult.Saved(trackPath = path.toString(), markingPath = profilesIndexPath.toString())
    }

    override fun loadProfile(profileId: String): LoadResult<TrackProfile> {
        if (!SchemaMigrations.isSafeId(profileId)) return LoadResult.Corrupt("unsafe profile id")
        val path = profilePath(profileId)
        if (!fileSystem.exists(path)) return LoadResult.NotFound
        return SchemaMigrations.decodeTrackProfile(readText(path))
    }

    override fun listActiveProfiles(): List<TrackProfile> =
        readProfileIndex().profileIds.mapNotNull { id ->
            (loadProfile(id) as? LoadResult.Loaded)?.value
        }.filterNot { it.isArchived }

    // --- Explicit current-Track selection (D-01..D-04) ----------------------

    override fun loadCurrentSelection(): LoadResult<CurrentTrackSelection> {
        if (!fileSystem.exists(currentSelectionPath)) return LoadResult.NotFound
        return try {
            val text = fileSystem.read(currentSelectionPath) { readUtf8() }
            val value = json.decodeFromString(CurrentTrackSelection.serializer(), text)
            // Defense in depth: an unsafe persisted profileId is corrupt and must
            // never be turned into an Okio path by a later loadProfile (T-05-05).
            if (value.profileId != null && !SchemaMigrations.isSafeId(value.profileId)) {
                LoadResult.Corrupt("unsafe profile id")
            } else {
                LoadResult.Loaded(value)
            }
        } catch (e: SerializationException) {
            LoadResult.Corrupt(e.message ?: "malformed JSON")
        } catch (e: IllegalArgumentException) {
            LoadResult.Corrupt(e.message ?: "invalid payload")
        }
    }

    override fun setCurrentSelection(selection: CurrentTrackSelection) {
        // Reject an unsafe id before any write (T-05-05); null means "no current Track".
        require(selection.profileId == null || SchemaMigrations.isSafeId(selection.profileId)) {
            "unsafe profile id"
        }
        // Payload-first atomic write (sibling temp + atomicMove), like every other
        // canonical payload, so a crashed write never leaves a torn selection file.
        writeAtomically(
            currentSelectionPath,
            json.encodeToString(CurrentTrackSelection.serializer(), selection),
        )
    }

    override fun clearCurrentSelection() {
        if (fileSystem.exists(currentSelectionPath)) fileSystem.delete(currentSelectionPath)
    }

    override fun deleteTimingSession(sessionId: String): DeleteResult {
        if (!SchemaMigrations.isSafeId(sessionId)) return DeleteResult.Rejected("unsafe session id")
        val removedPayload = deleteIfExists(sessionsDir / "$sessionId.json")
        val removedV2Payload = deleteIfExists(sessionsV2Dir / "$sessionId.json")
        val removedReferences = deleteReferencesForSession(sessionId)
        val removedRow = removeIndexRow(sessionId, ReviewEntryType.TimingSession)
        return if (removedPayload || removedV2Payload || removedReferences || removedRow) {
            DeleteResult.Deleted
        } else {
            DeleteResult.NotFound
        }
    }

    override fun deleteTrackMarking(markingId: String): DeleteResult {
        if (!SchemaMigrations.isSafeId(markingId)) return DeleteResult.Rejected("unsafe marking id")
        val removedPayload = deleteIfExists(markingsDir / "$markingId.json")
        val removedRow = removeIndexRow(markingId, ReviewEntryType.TrackMarking)
        return if (removedPayload || removedRow) {
            DeleteResult.Deleted
        } else {
            DeleteResult.NotFound
        }
    }

    override fun deleteTrack(trackId: String): DeleteResult {
        if (!SchemaMigrations.isSafeId(trackId)) return DeleteResult.Rejected("unsafe track id")
        val removedTrackPayload = deleteIfExists(tracksDir / "$trackId.json")
        val removedProfilePayload = deleteIfExists(profilePath(trackId))
        val removedProfileIndex = removeProfileIndexId(trackId)
        val removedReferences = deleteReferencesForTrack(trackId)
        val removedRow = removeIndexRow(trackId, ReviewEntryType.Track)
        val clearedSelection = when (val selection = loadCurrentSelection()) {
            is LoadResult.Loaded -> if (selection.value.profileId == trackId) {
                clearCurrentSelection()
                true
            } else {
                false
            }
            LoadResult.NotFound, is LoadResult.Corrupt -> false
        }
        return if (
            removedTrackPayload ||
            removedProfilePayload ||
            removedProfileIndex ||
            removedReferences ||
            removedRow ||
            clearedSelection
        ) {
            DeleteResult.Deleted
        } else {
            DeleteResult.NotFound
        }
    }

    private fun readProfileIndex(): ProfileIndex {
        if (!fileSystem.exists(profilesIndexPath)) return ProfileIndex()
        return try {
            json.decodeFromString(ProfileIndex.serializer(), fileSystem.read(profilesIndexPath) { readUtf8() })
        } catch (e: SerializationException) {
            ProfileIndex()
        } catch (e: IllegalArgumentException) {
            ProfileIndex()
        }
    }

    /** Lists committed `*.json` payloads in [dir], skipping in-flight `*.tmp` files. */
    private fun listJsonPayloads(dir: Path): List<Path> =
        (fileSystem.listOrNull(dir) ?: emptyList())
            .filter { it.name.endsWith(".json") && !it.name.endsWith(TMP_SUFFIX) }
            .sortedBy { it.name }

    private fun deleteIfExists(path: Path): Boolean {
        if (!fileSystem.exists(path)) return false
        fileSystem.delete(path)
        return true
    }

    private fun removeIndexRow(id: String, type: ReviewEntryType): Boolean {
        val index = readIndex()
        val updatedRows = index.rows.filterNot { it.id == id && it.type == type }
        if (updatedRows.size == index.rows.size) return false
        writeAtomically(indexPath, json.encodeToString(index.copy(rows = updatedRows)))
        return true
    }

    private fun removeProfileIndexId(profileId: String): Boolean {
        val index = readProfileIndex()
        val updatedIds = index.profileIds.filterNot { it == profileId }
        if (updatedIds.size == index.profileIds.size) return false
        writeAtomically(profilesIndexPath, json.encodeToString(ProfileIndex.serializer(), index.copy(profileIds = updatedIds)))
        return true
    }

    private fun deleteReferencesForSession(sessionId: String): Boolean {
        var removed = false
        for (path in listJsonPayloads(referencesDir)) {
            val payload = decodeOrNull<GhostReferencePayloadV1>(path) ?: continue
            if (payload.sessionId == sessionId) {
                removed = deleteIfExists(path) || removed
            }
        }
        for (path in listJsonPayloads(referencesV2Dir)) {
            val payload = decodeOrNull<GhostReferencePayloadV2>(path) ?: continue
            if (payload.sessionId == sessionId) {
                removed = deleteIfExists(path) || removed
            }
        }
        return removed
    }

    private fun deleteReferencesForTrack(trackId: String): Boolean {
        var removed = false
        for (path in listJsonPayloads(referencesDir)) {
            val payload = decodeOrNull<GhostReferencePayloadV1>(path) ?: continue
            if (payload.trackId == trackId) {
                removed = deleteIfExists(path) || removed
            }
        }
        for (path in listJsonPayloads(referencesV2Dir)) {
            val payload = decodeOrNull<GhostReferencePayloadV2>(path) ?: continue
            if (payload.compatibilityKey.profileId == trackId) {
                removed = deleteIfExists(path) || removed
            }
        }
        return removed
    }

    private inline fun <reified T> decodeOrNull(path: Path): T? = try {
        json.decodeFromString<T>(readText(path))
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun readText(path: Path): String = fileSystem.read(path) { readUtf8() }

    override fun loadReferenceLap(key: CourseCompatibilityKey): LoadResult<GhostReferencePayloadV2> {
        validateReferenceKey(key)?.let { return LoadResult.Corrupt(it) }
        return load(referencePath(key)) { payload ->
            validateReferencePayloadForRequest(payload, key)
        }
    }

    override fun saveReferenceLap(payload: GhostReferencePayloadV2, app: AppMetadata): SaveResult {
        validateReferencePayloadForRequest(payload, payload.compatibilityKey)?.let {
            throw IllegalArgumentException(it)
        }
        val path = referencePath(payload.compatibilityKey)
        writeAtomically(path, json.encodeToString(GhostReferencePayloadV2.serializer(), payload))
        return SaveResult.Saved(trackPath = path.toString(), markingPath = path.toString())
    }

    private fun validateReferenceKey(key: CourseCompatibilityKey): String? = when (
        val validation = GhostCompatibility.validateKey(key)
    ) {
        CourseCompatibilityValidation.Valid -> null
        is CourseCompatibilityValidation.Invalid -> validation.reason
    }

    private fun validateReferencePayloadForRequest(
        payload: GhostReferencePayloadV2,
        requestKey: CourseCompatibilityKey,
    ): String? {
        validateReferenceKey(requestKey)?.let { return it }
        return when {
            payload.schemaVersion != SCHEMA_VERSION_V2 -> "unsupported schemaVersion ${payload.schemaVersion}"
            payload.compatibilityKey != requestKey -> "compatibility key mismatch"
            else -> when (val validation = GhostCompatibility.validateReferencePayload(payload)) {
                CourseCompatibilityValidation.Valid -> when {
                    !SchemaMigrations.isSafeId(payload.sessionId) -> "unsafe session id"
                    payload.progressPoints.size < 2 -> "reference progress curve too short"
                    else -> null
                }
                is CourseCompatibilityValidation.Invalid -> validation.reason
            }
        }
    }

    /** Exact V2 reference filename: profile + geometry compatibility + direction + source slot. */
    private fun referencePath(key: CourseCompatibilityKey): Path =
        referencesV2Dir / "${referenceV2Name(key)}.json"

    private fun referenceV2Name(key: CourseCompatibilityKey): String {
        val slot = if (key.isSimulated) SIM_SLOT else REAL_SLOT
        return listOf(
            encodePathComponent(key.profileId),
            encodePathComponent(key.geometryCompatibilityId),
            key.direction.name,
            slot,
        ).joinToString("__")
    }

    private fun encodePathComponent(value: String): String = buildString {
        value.forEach { ch ->
            val safe = ch in 'a'..'z' || ch in 'A'..'Z' || ch in '0'..'9' || ch == '-' || ch == '_'
            if (safe) {
                append(ch)
            } else {
                append('~')
                append(ch.code.toString(16).padStart(4, '0'))
            }
        }
    }

    private inline fun <reified T> load(path: Path, validate: (T) -> String?): LoadResult<T> {
        if (!fileSystem.exists(path)) return LoadResult.NotFound
        return try {
            val text = fileSystem.read(path) { readUtf8() }
            val value = json.decodeFromString<T>(text)
            val problem = validate(value)
            if (problem != null) LoadResult.Corrupt(problem) else LoadResult.Loaded(value)
        } catch (e: SerializationException) {
            LoadResult.Corrupt(e.message ?: "malformed JSON")
        } catch (e: IllegalArgumentException) {
            LoadResult.Corrupt(e.message ?: "invalid payload")
        }
    }

    private fun writeAtomically(path: Path, content: String) {
        path.parent?.let { fileSystem.createDirectories(it) }
        val tmp = path.parent!! / "${path.name}$TMP_SUFFIX"
        fileSystem.write(tmp) { writeUtf8(content) }
        fileSystem.atomicMove(tmp, path)
    }

    private fun upsertRows(index: ReviewIndex, rows: List<ReviewIndexRow>): ReviewIndex {
        val keys = rows.map { it.id to it.type }.toSet()
        val retained = index.rows.filterNot { (it.id to it.type) in keys }
        return index.copy(rows = retained + rows)
    }

    companion object {
        private const val TRACKS_DIR = "tracks"
        private const val MARKINGS_DIR = "markings"
        private const val SESSIONS_DIR = "sessions"
        private const val DRAFTS_DIR = "drafts"
        private const val REFERENCES_DIR = "references"
        private const val ACTIVE_DRAFT_FILE = "timing.json"
        private const val INDEX_FILE = "index.json"
        private const val TMP_SUFFIX = ".tmp"

        // V2 course-profile layout (Plan 05-02). Kept distinct from the V1 dirs so
        // migration is strictly additive and every V1 original survives.
        private const val PROFILES_DIR = "profiles"
        private const val SESSIONS_V2_DIR = "sessions-v2"
        private const val REFERENCES_V2_DIR = "references-v2"
        private const val PROFILES_INDEX_FILE = "profiles-index.json"
        private const val CURRENT_SELECTION_FILE = "current-selection.json"

        /** Filename slot suffixes keeping real and simulated references apart (D-04). */
        private const val REAL_SLOT = "real"
        private const val SIM_SLOT = "sim"

        /** Canonical JSON used for both saved and exported payloads (D-24). */
        val canonicalJson: Json = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    }
}

/**
 * The rebuildable commit marker for migrated/saved V2 profiles (Plan 05-02).
 *
 * It records only profile ids; the canonical aggregate lives in each
 * `profiles/<id>.json`. Written LAST in [FileSessionStore.migrate] / [saveProfile]
 * so a fault before this write never exposes a half-migrated profile. It is a cache
 * (like `index.json`) and a corrupt index is treated as empty, not fatal.
 */
@Serializable
internal data class ProfileIndex(
    val schemaVersion: Int = SCHEMA_VERSION_V2,
    val profileIds: List<String> = emptyList(),
)
