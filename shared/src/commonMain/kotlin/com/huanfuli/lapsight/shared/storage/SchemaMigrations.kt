package com.huanfuli.lapsight.shared.storage

import com.huanfuli.lapsight.shared.session.GhostReferencePayloadV1
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.ProgressPointDto
import com.huanfuli.lapsight.shared.session.TimingSessionPayloadV1
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityValidation
import com.huanfuli.lapsight.shared.ghost.GhostCompatibility
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSetup
import com.huanfuli.lapsight.shared.track.CourseSnapshot
import com.huanfuli.lapsight.shared.track.CurrentTrackSelection
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2
import com.huanfuli.lapsight.shared.track.LegacyCumulativeSplit
import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.track.SectorBoundary
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import com.huanfuli.lapsight.shared.track.TimingSessionPayloadV2
import com.huanfuli.lapsight.shared.track.TimingSessionV2
import com.huanfuli.lapsight.shared.track.TrackPayloadV1
import com.huanfuli.lapsight.shared.track.TrackProfile
import com.huanfuli.lapsight.shared.track.TrackProfilePayloadV2
import com.huanfuli.lapsight.shared.track.TrackReferenceLine
import com.huanfuli.lapsight.shared.track.TrackRevision
import com.huanfuli.lapsight.shared.session.GeoPointDto
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Frozen V1 → V2 schema dispatch and the pure V1 → V2 migration mapping (D-12..D-15).
 *
 * The load boundary parses a [JsonElement], inspects the literal `schemaVersion`,
 * decodes the matching FROZEN serializer, validates it, and maps it into the
 * current V2 domain. A newer/unknown version fails closed with
 * `Corrupt("unsupported schemaVersion ...")`; malformed JSON, unsafe opaque IDs,
 * non-finite/oversized geometry, and empty progress curves are typed
 * [LoadResult.Corrupt] rather than thrown (T-05-01).
 *
 * The mapping is intentionally pure and deterministic (T-05-02):
 * - `profileId` is the old `track.id` / session `trackId`; a duplicate would get a
 *   fresh id elsewhere, but migration never invents one.
 * - `revisionId = "<oldId>:r1"` and `geometryCompatibilityId = "<oldId>:g1"` are
 *   deterministic string identities; geometry is NEVER hashed (D-15).
 * - Legacy `SectorEventDto`s become [LegacyCumulativeSplit]s — preserved cumulative
 *   line splits, never relabeled as complete final Sectors (D-06).
 * - Current selection maps to `null`; migration never derives the newest/only Track
 *   (D-01, D-04).
 */
object SchemaMigrations {

    /** Reuse the canonical JSON so dispatch reads exactly what the store wrote. */
    val migrationJson = FileSessionStore.canonicalJson

    /** Upper bounds that reject pathologically oversized persisted geometry. */
    const val MAX_GEO_POINTS: Int = 200_000
    const val MAX_SAMPLES: Int = 5_000_000

    /** Highest legacy intermediate-line count that maps to V2 (2..6 Sectors). */
    const val MAX_LEGACY_SECTOR_LINES: Int = 5

    // --- Deterministic identity formulas (D-12, D-15) -------------------------

    /** Deterministic first-revision id for a migrated legacy Track. */
    fun migratedRevisionId(legacyId: String): String = "$legacyId:r1"

    /** Deterministic geometry-compatibility id for a migrated legacy Track. */
    fun migratedGeometryCompatibilityId(legacyId: String): String = "$legacyId:g1"

    /**
     * Migrated current selection is always empty (D-01, D-04): the user must pick a
     * Track after upgrade; migration never substitutes the newest or only profile.
     */
    fun migrateCurrentSelection(): CurrentTrackSelection = CurrentTrackSelection(profileId = null)

    // --- Validation primitives ------------------------------------------------

    /**
     * An opaque persisted id is safe only if it cannot escape its storage directory
     * or smuggle control characters (path-traversal / injection defense, T-05-01).
     */
    fun isSafeId(id: String): Boolean =
        id.isNotBlank() &&
            !id.contains('/') &&
            !id.contains('\\') &&
            !id.contains("..") &&
            id.none { it.isISOControl() }

    private fun isFiniteLatLon(point: GeoPointDto): Boolean =
        point.latitude.isFinite() &&
            point.longitude.isFinite() &&
            point.latitude in -90.0..90.0 &&
            point.longitude in -180.0..180.0

    private fun isFiniteLine(line: StartFinishLineDto): Boolean =
        isFiniteLatLon(line.pointA) && isFiniteLatLon(line.pointB)

    // --- Public version dispatch (Task 1) -------------------------------------

    /** Decodes a Track payload of either schema version into a V2 [TrackProfile]. */
    fun decodeTrackProfile(text: String): LoadResult<TrackProfile> = dispatch(text) { version, element ->
        when (version) {
            SCHEMA_VERSION_V1 -> {
                val v1 = migrationJson.decodeFromJsonElement<TrackPayloadV1>(element)
                validateTrackV1(v1)?.let { return@dispatch LoadResult.Corrupt(it) }
                val profile = migrateTrack(v1)
                validateProfile(profile)?.let { return@dispatch LoadResult.Corrupt(it) }
                LoadResult.Loaded(profile)
            }
            SCHEMA_VERSION_V2 -> {
                val v2 = migrationJson.decodeFromJsonElement<TrackProfilePayloadV2>(element)
                validateProfile(v2.profile)?.let { return@dispatch LoadResult.Corrupt(it) }
                LoadResult.Loaded(v2.profile)
            }
            else -> LoadResult.Corrupt("unsupported schemaVersion $version")
        }
    }

    /** Decodes a timing-session payload of either schema version into a V2 payload. */
    fun decodeTimingSession(text: String): LoadResult<TimingSessionPayloadV2> = dispatch(text) { version, element ->
        when (version) {
            SCHEMA_VERSION_V1 -> {
                val v1 = migrationJson.decodeFromJsonElement<TimingSessionPayloadV1>(element)
                validateTimingSessionV1(v1)?.let { return@dispatch LoadResult.Corrupt(it) }
                val v2 = migrateTimingSession(v1)
                validateTimingSessionV2(v2)?.let { return@dispatch LoadResult.Corrupt(it) }
                LoadResult.Loaded(v2)
            }
            SCHEMA_VERSION_V2 -> {
                val v2 = migrationJson.decodeFromJsonElement<TimingSessionPayloadV2>(element)
                validateTimingSessionV2(v2)?.let { return@dispatch LoadResult.Corrupt(it) }
                LoadResult.Loaded(v2)
            }
            else -> LoadResult.Corrupt("unsupported schemaVersion $version")
        }
    }

    /** Decodes a Ghost reference payload of either schema version into a V2 payload. */
    fun decodeGhostReference(text: String): LoadResult<GhostReferencePayloadV2> = dispatch(text) { version, element ->
        when (version) {
            SCHEMA_VERSION_V1 -> {
                val v1 = migrationJson.decodeFromJsonElement<GhostReferencePayloadV1>(element)
                validateGhostV1(v1)?.let { return@dispatch LoadResult.Corrupt(it) }
                val v2 = migrateGhostReference(v1)
                validateGhostV2(v2)?.let { return@dispatch LoadResult.Corrupt(it) }
                LoadResult.Loaded(v2)
            }
            SCHEMA_VERSION_V2 -> {
                val v2 = migrationJson.decodeFromJsonElement<GhostReferencePayloadV2>(element)
                validateGhostV2(v2)?.let { return@dispatch LoadResult.Corrupt(it) }
                LoadResult.Loaded(v2)
            }
            else -> LoadResult.Corrupt("unsupported schemaVersion $version")
        }
    }

    /**
     * Decodes the lightweight Review index. The index is a rebuildable cache, so V1
     * and V2 share the same shape here; the store rebuilds rows from canonical
     * payloads (Plan 05-02). Unsafe row ids are rejected (T-05-01).
     */
    fun decodeReviewIndex(text: String): LoadResult<ReviewIndex> = dispatch(text) { version, element ->
        when (version) {
            SCHEMA_VERSION_V1, SCHEMA_VERSION_V2 -> {
                val index = migrationJson.decodeFromJsonElement<ReviewIndex>(element)
                index.rows.firstOrNull { !isSafeId(it.id) }?.let {
                    return@dispatch LoadResult.Corrupt("unsafe index row id")
                }
                LoadResult.Loaded(index)
            }
            else -> LoadResult.Corrupt("unsupported schemaVersion $version")
        }
    }

    // --- Pure V1 → V2 migration mapping (Task 2) ------------------------------

    /**
     * Maps a frozen [TrackPayloadV1] to a V2 [TrackProfile] with a single immutable
     * first revision. Legacy sector lines become intermediate [SectorBoundary]s
     * (1..5 lines → 2..6 Sectors); they carry `normalizedProgress = null` until the
     * offline editor re-derives arc-length anchors. Default direction is Recorded.
     */
    fun migrateTrack(payload: TrackPayloadV1): TrackProfile {
        val track = payload.track
        val boundaries = track.sectors
            .sortedBy { it.order }
            .mapIndexed { index, line ->
                SectorBoundary(
                    id = line.id,
                    order = index + 1,
                    pointA = line.pointA,
                    pointB = line.pointB,
                    normalizedProgress = null,
                )
            }
        val sectorsEnabled = boundaries.isNotEmpty()
        val revision = TrackRevision(
            revisionId = migratedRevisionId(track.id),
            ordinal = 1,
            createdAtEpochMillis = track.createdAtEpochMillis,
            sourceMarkingSessionId = track.sourceMarkingSessionId,
            referenceLine = track.referenceLine ?: TrackReferenceLine(points = emptyList(), isClosed = true),
            courseSetup = CourseSetup(
                startFinish = track.startFinish,
                sectorsEnabled = sectorsEnabled,
                sectorCount = if (sectorsEnabled) boundaries.size + 1 else 0,
                boundaries = boundaries,
            ),
            geometryCompatibilityId = migratedGeometryCompatibilityId(track.id),
        )
        return TrackProfile(
            profileId = track.id,
            name = track.name,
            createdAtEpochMillis = track.createdAtEpochMillis,
            source = track.source,
            revisions = listOf(revision),
            archivedAtEpochMillis = null,
            preferredDirection = CourseDirection.Recorded,
        )
    }

    /**
     * Maps a frozen [TimingSessionPayloadV1] to a V2 payload. The session's start/
     * finish + sector lines and cumulative split events are preserved verbatim
     * inside an immutable [CourseSnapshot] (`isLegacyMigrated = true`); the legacy
     * splits are NEVER promoted to complete final Sectors (D-06).
     */
    fun migrateTimingSession(payload: TimingSessionPayloadV1): TimingSessionPayloadV2 {
        val session = payload.session
        val boundaries = session.sectors
            .sortedBy { it.order }
            .mapIndexed { index, line ->
                SectorBoundary(
                    id = line.id,
                    order = index + 1,
                    pointA = line.pointA,
                    pointB = line.pointB,
                    normalizedProgress = null,
                )
            }
        val legacySplits = payload.sectorEvents.map {
            LegacyCumulativeSplit(
                lapNumber = it.lapNumber,
                sectorId = it.sectorId,
                sectorOrder = it.sectorOrder,
                crossingMillis = it.crossingMillis,
                cumulativeSplitMillis = it.splitMillis,
            )
        }
        val snapshot = CourseSnapshot(
            profileId = session.trackId,
            revisionId = migratedRevisionId(session.trackId),
            geometryCompatibilityId = migratedGeometryCompatibilityId(session.trackId),
            direction = CourseDirection.Recorded,
            referenceLine = TrackReferenceLine(points = emptyList(), isClosed = true),
            startFinish = session.startFinish,
            boundaries = boundaries,
            legacySplits = legacySplits,
            isLegacyMigrated = true,
        )
        return TimingSessionPayloadV2(
            session = TimingSessionV2(
                id = session.id,
                profileId = session.trackId,
                trackName = session.trackName,
                createdAtEpochMillis = session.createdAtEpochMillis,
                source = session.source,
                courseSnapshot = snapshot,
            ),
            app = payload.app,
            samples = payload.samples,
            laps = payload.laps,
            gpsQuality = payload.gpsQuality,
            totalDurationMillis = payload.totalDurationMillis,
        )
    }

    /**
     * Maps a frozen [GhostReferencePayloadV1] to a V2 payload keyed by a full
     * [CourseCompatibilityKey]. Default direction is Recorded; the legacy
     * real/simulated source slot is preserved so a real lookup never returns a
     * simulated reference (D-04, D-19).
     */
    fun migrateGhostReference(payload: GhostReferencePayloadV1): GhostReferencePayloadV2 = GhostReferencePayloadV2(
        compatibilityKey = GhostCompatibility.migratedV1Key(
            trackId = payload.trackId,
            isSimulated = payload.source.isSimulated,
        ),
        sessionId = payload.sessionId,
        lapNumber = payload.lapNumber,
        durationMillis = payload.durationMillis,
        source = payload.source,
        totalDistanceMeters = payload.totalDistanceMeters,
        samples = payload.samples,
        progressPoints = payload.progressPoints,
        app = payload.app,
    )

    // --- Internal dispatch + validators ---------------------------------------

    private inline fun <T> dispatch(
        text: String,
        decode: (Int, JsonElement) -> LoadResult<T>,
    ): LoadResult<T> = try {
        val element: JsonElement = migrationJson.parseToJsonElement(text)
        val obj: JsonObject = element.jsonObject
        val version = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: return LoadResult.Corrupt("missing schemaVersion")
        decode(version, element)
    } catch (e: SerializationException) {
        LoadResult.Corrupt(e.message ?: "malformed JSON")
    } catch (e: IllegalArgumentException) {
        LoadResult.Corrupt(e.message ?: "invalid payload")
    }

    private fun validateTrackV1(payload: TrackPayloadV1): String? {
        val track = payload.track
        if (!isSafeId(track.id)) return "unsafe track id"
        track.sourceMarkingSessionId?.let { if (!isSafeId(it)) return "unsafe marking id" }
        track.referenceLine?.let { line ->
            if (line.points.size > MAX_GEO_POINTS) return "reference line too large"
            if (line.points.any { !isFiniteLatLon(it) }) return "non-finite reference point"
        }
        track.startFinish?.let { if (!isFiniteLine(it)) return "non-finite start/finish" }
        if (track.sectors.size > MAX_LEGACY_SECTOR_LINES) return "too many legacy sector lines"
        track.sectors.forEach { line ->
            if (!isSafeId(line.id)) return "unsafe sector id"
            if (!isFiniteLine(StartFinishLineDto(line.pointA, line.pointB))) return "non-finite sector line"
        }
        return null
    }

    private fun validateTimingSessionV1(payload: TimingSessionPayloadV1): String? {
        val session = payload.session
        if (!isSafeId(session.id)) return "unsafe session id"
        if (!isSafeId(session.trackId)) return "unsafe track id"
        if (!isFiniteLine(session.startFinish)) return "non-finite start/finish"
        if (payload.samples.size > MAX_SAMPLES) return "too many samples"
        return validateSamples(payload.samples)
    }

    private fun validateGhostV1(payload: GhostReferencePayloadV1): String? {
        if (!isSafeId(payload.trackId)) return "unsafe track id"
        if (!isSafeId(payload.sessionId)) return "unsafe session id"
        if (payload.progressPoints.size < 2) return "reference progress curve too short"
        if (payload.progressPoints.size > MAX_GEO_POINTS) return "reference progress curve too large"
        if (payload.samples.size > MAX_SAMPLES) return "too many samples"
        return validateSamples(payload.samples) ?: validateProgressPoints(payload.progressPoints)
    }

    private fun validateProfile(profile: TrackProfile): String? {
        if (!isSafeId(profile.profileId)) return "unsafe profile id"
        if (profile.revisions.isEmpty()) return "profile has no revisions"
        var previousOrdinal = 0
        for (revision in profile.revisions) {
            if (!isSafeId(revision.revisionId)) return "unsafe revision id"
            if (revision.geometryCompatibilityId.isBlank()) return "missing geometry compatibility id"
            if (revision.ordinal <= previousOrdinal) return "revision ordinals not strictly increasing"
            previousOrdinal = revision.ordinal
            validateCourseSetup(revision.courseSetup)?.let { return it }
            if (revision.referenceLine.points.size > MAX_GEO_POINTS) return "reference line too large"
            if (revision.referenceLine.points.any { !isFiniteLatLon(it) }) return "non-finite reference point"
        }
        return null
    }

    private fun validateCourseSetup(setup: CourseSetup): String? {
        setup.startFinish?.let { if (!isFiniteLine(it)) return "non-finite start/finish" }
        if (setup.sectorsEnabled) {
            if (setup.sectorCount !in 2..6) return "sector count out of range"
            if (setup.boundaries.size != setup.sectorCount - 1) return "boundary count must be sectorCount - 1"
        }
        setup.boundaries.forEach { boundary ->
            if (!isSafeId(boundary.id)) return "unsafe boundary id"
            if (!isFiniteLine(StartFinishLineDto(boundary.pointA, boundary.pointB))) return "non-finite boundary line"
        }
        return null
    }

    private fun validateTimingSessionV2(payload: TimingSessionPayloadV2): String? {
        val session = payload.session
        if (!isSafeId(session.id)) return "unsafe session id"
        if (!isSafeId(session.profileId)) return "unsafe profile id"
        val snapshot = session.courseSnapshot
        if (!isSafeId(snapshot.profileId)) return "unsafe snapshot profile id"
        if (!isSafeId(snapshot.revisionId)) return "unsafe snapshot revision id"
        if (snapshot.geometryCompatibilityId.isBlank()) return "missing geometry compatibility id"
        if (!isFiniteLine(snapshot.startFinish)) return "non-finite start/finish"
        if (payload.samples.size > MAX_SAMPLES) return "too many samples"
        return validateSamples(payload.samples)
    }

    private fun validateGhostV2(payload: GhostReferencePayloadV2): String? {
        val key = payload.compatibilityKey
        when (val compatibility = GhostCompatibility.validateReferencePayload(payload)) {
            CourseCompatibilityValidation.Valid -> Unit
            is CourseCompatibilityValidation.Invalid -> return compatibility.reason
        }
        if (!isSafeId(payload.sessionId)) return "unsafe session id"
        if (payload.progressPoints.size < 2) return "reference progress curve too short"
        if (payload.progressPoints.size > MAX_GEO_POINTS) return "reference progress curve too large"
        if (payload.samples.size > MAX_SAMPLES) return "too many samples"
        return validateSamples(payload.samples) ?: validateProgressPoints(payload.progressPoints)
    }

    private fun validateSamples(samples: List<LocationSampleDto>): String? {
        if (samples.any { !it.latitude.isFinite() || !it.longitude.isFinite() }) return "non-finite sample coordinates"
        return null
    }

    private fun validateProgressPoints(points: List<ProgressPointDto>): String? {
        if (points.any { !it.latitude.isFinite() || !it.longitude.isFinite() || !it.normalizedProgress.isFinite() }) {
            return "non-finite progress point"
        }
        return null
    }
}
