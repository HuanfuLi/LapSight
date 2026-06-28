package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityKey
import com.huanfuli.lapsight.shared.ghost.CourseCompatibilityValidation
import com.huanfuli.lapsight.shared.ghost.DeltaDisplayState
import com.huanfuli.lapsight.shared.ghost.DeltaUnavailableReason
import com.huanfuli.lapsight.shared.ghost.GhostCompatibility
import com.huanfuli.lapsight.shared.ghost.LiveDeltaSnapshot
import com.huanfuli.lapsight.shared.ghost.ProgressCurve
import com.huanfuli.lapsight.shared.ghost.ProgressPoint
import com.huanfuli.lapsight.shared.ghost.ReferenceLap
import com.huanfuli.lapsight.shared.lap.LapEvent
import com.huanfuli.lapsight.shared.lap.SectorEvent
import com.huanfuli.lapsight.shared.lap.SectorResult
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2
import com.huanfuli.lapsight.shared.track.SectorLineDto
import com.huanfuli.lapsight.shared.track.StartFinishLineDto
import kotlinx.serialization.Serializable

/**
 * Base canonical DTOs shared by saved tracks and timing sessions (D-17, D-24, D-25).
 *
 * These are the serializable mirrors of the in-memory domain types. They live in
 * the session package because tracks and timing sessions both persist the same
 * raw sample/metadata shapes, and internal saved JSON and exported JSON share the
 * same versioned schema where practical (D-24).
 */

/** Serializable geographic point in WGS84-style decimal degrees. */
@Serializable
data class GeoPointDto(
    val latitude: Double,
    val longitude: Double,
)

/**
 * Serializable mirror of [LocationSample]. Kept separate from the Phase 2 domain
 * model so the lap engine's input type stays free of serialization concerns while
 * saved payloads remain canonical and versioned.
 */
@Serializable
data class LocationSampleDto(
    val elapsedMillis: Long,
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null,
    val headingDegrees: Double? = null,
    val altitudeMeters: Double? = null,
    val source: LocationSource,
)

/** Source/provenance metadata so demo/simulated data stays visibly labeled (D-25, D-42). */
@Serializable
data class SourceMetadata(
    val source: LocationSource,
    val isSimulated: Boolean,
    val label: String? = null,
)

/** App/build metadata captured at save time where available (D-25). */
@Serializable
data class AppMetadata(
    val appVersion: String,
    val buildNumber: String? = null,
    val platform: String? = null,
)

/** Lightweight GPS-quality rollup used in index summaries and review (D-22). */
@Serializable
data class GpsQualitySummary(
    val sampleCount: Int,
    val averageAccuracyMeters: Double? = null,
    val source: LocationSource,
)

/** Persisted one-time pre-Timing course-distance outcome (D-22, D-23). */
@Serializable
enum class CoursePreflightDisposition {
    NotChecked,
    Ready,
    Unavailable,
    Blocked,
    Overridden,
}

/**
 * Immutable evidence of the wrong-course preflight used to start a session.
 *
 * A legacy session defaults to [CoursePreflightDisposition.NotChecked]. A
 * clearly-far result is never persisted into an active session unless the user
 * explicitly chooses the override, in which case [overrideUsed] is true and the
 * disposition is [CoursePreflightDisposition.Overridden].
 */
@Serializable
data class CoursePreflightSnapshot(
    val disposition: CoursePreflightDisposition,
    val overrideUsed: Boolean = false,
    val distanceMeters: Double? = null,
    val conservativeDistanceMeters: Double? = null,
    val thresholdMeters: Double? = null,
    val unavailableReason: CoursePreflightUnavailableReason? = null,
) {
    companion object {
        fun notChecked(): CoursePreflightSnapshot = CoursePreflightSnapshot(
            disposition = CoursePreflightDisposition.NotChecked,
        )

        fun from(
            result: CoursePreflightResult,
            overrideUsed: Boolean = false,
        ): CoursePreflightSnapshot = when (result) {
            is CoursePreflightResult.Ready -> CoursePreflightSnapshot(
                disposition = CoursePreflightDisposition.Ready,
                distanceMeters = result.distanceMeters,
                conservativeDistanceMeters = result.conservativeDistanceMeters,
                thresholdMeters = result.thresholdMeters,
            )
            is CoursePreflightResult.Blocked -> CoursePreflightSnapshot(
                disposition = if (overrideUsed) {
                    CoursePreflightDisposition.Overridden
                } else {
                    CoursePreflightDisposition.Blocked
                },
                overrideUsed = overrideUsed,
                distanceMeters = result.distanceMeters,
                conservativeDistanceMeters = result.conservativeDistanceMeters,
                thresholdMeters = result.thresholdMeters,
            )
            is CoursePreflightResult.Unavailable -> CoursePreflightSnapshot(
                disposition = CoursePreflightDisposition.Unavailable,
                unavailableReason = result.reason,
            )
        }
    }
}

/** Maps a domain [LocationSample] to its serializable DTO. */
fun LocationSample.toDto(): LocationSampleDto = LocationSampleDto(
    elapsedMillis = elapsedMillis,
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    headingDegrees = headingDegrees,
    altitudeMeters = altitudeMeters,
    source = source,
)

/** Maps a [LocationSampleDto] back to the domain [LocationSample]. */
fun LocationSampleDto.toModel(): LocationSample = LocationSample(
    elapsedMillis = elapsedMillis,
    latitude = latitude,
    longitude = longitude,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
    speedMetersPerSecond = speedMetersPerSecond,
    headingDegrees = headingDegrees,
    altitudeMeters = altitudeMeters,
    source = source,
)

/** Derives a [GpsQualitySummary] from a captured sample list. */
fun gpsQualitySummaryOf(samples: List<LocationSampleDto>, source: LocationSource): GpsQualitySummary {
    val accuracies = samples.mapNotNull { it.horizontalAccuracyMeters }
    val average = if (accuracies.isEmpty()) null else accuracies.sum() / accuracies.size
    return GpsQualitySummary(
        sampleCount = samples.size,
        averageAccuracyMeters = average,
        source = source,
    )
}

// --- Formal timing session domain (D-13..D-20) --------------------------------

/**
 * Explicit lifecycle state of a timing-session draft (D-13..D-16).
 *
 * A draft persists continuously while active so it can survive crash/restart;
 * after Stop the user must explicitly Save or Discard before formal history is
 * created (D-14, D-16). A [Saved] session lives in Review history; a
 * [Discarded] draft never does.
 */
enum class TimingDraftState {
    /** An active timing run; samples and events are checkpointed continuously. */
    ActiveDraft,

    /** Timing stopped; awaiting explicit Save or Discard (D-14). */
    StoppedPendingSave,

    /** Saved into formal Review history. Terminal for the draft. */
    Saved,

    /** Discarded; removed from draft storage and never enters Review history (D-16). */
    Discarded,
}

/**
 * Serializable mirror of [LapEvent] persisted inside a [TimingSessionPayloadV1].
 *
 * Kept as a separate DTO so the lap engine's domain type stays free of
 * serialization concerns while saved payloads remain canonical and versioned.
 */
@Serializable
data class LapDto(
    val lapNumber: Int,
    val startMillis: Long,
    val endMillis: Long,
) {
    val durationMillis: Long get() = endMillis - startMillis
}

/**
 * Serializable mirror of the LEGACY line-centric [SectorEvent] persisted inside a
 * [TimingSessionPayloadV1] (a `LegacyCumulativeSplit`).
 *
 * This carries only a single cumulative [splitMillis] measured from the lap start
 * to one sector-LINE crossing. It is preserved verbatim for V1 history and is
 * NEVER relabeled as a complete Sector interval; the complete-interval V2 shape is
 * [SectorResultDto] with both an adjacent duration and a separate cumulative split
 * (D-06, D-11).
 */
@Serializable
data class SectorEventDto(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val crossingMillis: Long,
    val splitMillis: Long,
)

/**
 * Serializable mirror of the complete-interval [SectorResult] persisted inside a
 * [TimingSessionPayloadV1] (D-06, D-11).
 *
 * Unlike the legacy [SectorEventDto], this carries BOTH the adjacent-crossing
 * [durationMillis] and the separate [cumulativeSplitMillis] from the lap start, so
 * historical Review/export can render complete Sector coverage without recomputing
 * it from line crossings.
 */
@Serializable
data class SectorResultDto(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val startedAtMillis: Long,
    val endedAtMillis: Long,
    val durationMillis: Long,
    val cumulativeSplitMillis: Long,
)

/** Maps a domain [SectorResult] to its serializable V2 DTO. */
fun SectorResult.toDto(): SectorResultDto = SectorResultDto(
    lapNumber = lapNumber,
    sectorId = sectorId,
    sectorOrder = sectorOrder,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    durationMillis = durationMillis,
    cumulativeSplitMillis = cumulativeSplitMillis,
)

/** Maps a persisted [SectorResultDto] back to the domain [SectorResult]. */
fun SectorResultDto.toModel(): SectorResult = SectorResult(
    lapNumber = lapNumber,
    sectorId = sectorId,
    sectorOrder = sectorOrder,
    startedAtMillis = startedAtMillis,
    endedAtMillis = endedAtMillis,
    durationMillis = durationMillis,
    cumulativeSplitMillis = cumulativeSplitMillis,
)

/**
 * A formal timing session linked to a saved [Track] (D-17, D-19).
 *
 * The [trackId] is the stored Track.id acting as a foreign key; a
 * [TimingSession] without a saved Track/start-finish is invalid and the
 * [SessionController] refuses to start one (D-19).
 */
@Serializable
data class TimingSession(
    val id: String,
    val trackId: String,
    val trackName: String,
    val createdAtEpochMillis: Long,
    val source: SourceMetadata,
    val startFinish: StartFinishLineDto,
    val sectors: List<SectorLineDto> = emptyList(),
    /**
     * The Course Direction this run was timed under (D-18). Defaulted to
     * [CourseDirection.Recorded] so V1 history written before this field decodes
     * cleanly; a Reverse run snapshots its flipped configuration here so Review and
     * Ghost compatibility never confuse it with the recorded direction (D-19, SC-04).
     */
    val direction: CourseDirection = CourseDirection.Recorded,
    /**
     * Full reference/Ghost compatibility slot implied by this historical session.
     *
     * V1 sessions decode into the deterministic migrated Recorded key. Newer callers
     * may supply a profile/revision-derived key; selection never uses [trackId] or
     * exact revision id alone for Ghost comparison (D-15/D-19).
     */
    val courseCompatibilityKey: CourseCompatibilityKey = GhostCompatibility
        .migratedV1Key(trackId = trackId, isSimulated = source.isSimulated)
        .copy(direction = direction),
    /**
     * One-time whole-course preflight evidence captured before Timing starts.
     * This metadata never participates in active lap validity or Ghost matching.
     */
    val coursePreflight: CoursePreflightSnapshot = CoursePreflightSnapshot.notChecked(),
)

/**
 * Canonical, versioned payload for a saved timing session (D-23, D-25).
 *
 * Embeds raw samples, completed laps, sector events, the course definition,
 * GPS-quality summary, and source/app metadata. Internal saved JSON and
 * exported JSON share this same versioned schema (D-24).
 */
@Serializable
data class TimingSessionPayloadV1(
    val schemaVersion: Int = 1,
    val session: TimingSession,
    val app: AppMetadata,
    val samples: List<LocationSampleDto>,
    val laps: List<LapDto>,
    val sectorEvents: List<SectorEventDto>,
    val gpsQuality: GpsQualitySummary,
    val totalDurationMillis: Long = 0L,
    /**
     * Complete-interval V2 Sector results (D-06, D-11). Defaulted empty so V1
     * history written before this field decodes cleanly and keeps its legacy
     * cumulative [sectorEvents] meaning; a saved V2 session additionally carries
     * these complete intervals with separate duration and cumulative split.
     */
    val sectorResults: List<SectorResultDto> = emptyList(),
)

/**
 * Per-Track fastest valid lap candidate derived from saved [TimingSession]s
 * (D-18, D-20). Stored separately from the [TrackReferenceLine].
 *
 * Real candidates EXCLUDE any session whose [SourceMetadata.isSimulated] is
 * true so demo data never pollutes future ghost/delta state (D-43).
 *
 * @property trackId the saved Track this candidate belongs to.
 * @property sessionId the saved TimingSession that produced the lap.
 * @property lapNumber one-based lap index inside the source session.
 * @property lapDurationMillis duration of the fastest valid lap.
 */
@Serializable
data class GhostCandidate(
    val trackId: String,
    val sessionId: String,
    val lapNumber: Int,
    val lapDurationMillis: Long,
)

/**
 * Immutable snapshot of the active draft state rendered by the Drive UI.
 *
 * Carries only summary counts and the draft state so the UI does not re-walk
 * raw checkpoints. The full payload is persisted by the store and loaded on
 * demand by Review (D-22, D-32).
 */
data class TimingDraftSnapshot(
    val state: TimingDraftState,
    val sessionId: String,
    val trackId: String,
    val trackName: String,
    val source: SourceMetadata,
    val checkpointedSampleCount: Int,
    val checkpointedLapCount: Int,
    val checkpointedSectorEventCount: Int,
)

/**
 * Read-only production timing/delta view rendered by the mounted-phone Drive
 * surface (GHOST-03, D-13..D-19).
 *
 * Carries everything the active timing UI needs WITHOUT peeking into recorder
 * internals or [SessionController.recorderForTest]: the primary current-lap time,
 * last/best lap, lap count, latest speed/accuracy, source provenance, and the
 * value-only [deltaDisplay]. The delta and the non-delta metrics are independent,
 * so an unavailable delta (`--`) never erases the other readouts (D-19, T-04-11).
 *
 * @property isActive true while a timing run is live (recorder present).
 * @property deltaDisplay value-only `--` / `+x.xxxs` / `-x.xxxs` readout (D-14).
 * @property source provenance so the DEMO badge stays visible for simulated runs
 *   (D-42); null when no run is active.
 */
data class TimingRunSnapshot(
    val isActive: Boolean,
    val lapCount: Int,
    val currentLapMillis: Long?,
    val lastLapMillis: Long?,
    val bestLapMillis: Long?,
    val sessionElapsedMillis: Long,
    val checkpointedSampleCount: Int,
    val speedMetersPerSecond: Double?,
    val accuracyMeters: Double?,
    val source: SourceMetadata?,
    val deltaDisplay: DeltaDisplayState,
    val referenceLapMillis: Long?,
) {
    companion object {
        /** Inactive snapshot: no run, neutral `--` delta, other metrics empty. */
        fun inactive(): TimingRunSnapshot = TimingRunSnapshot(
            isActive = false,
            lapCount = 0,
            currentLapMillis = null,
            lastLapMillis = null,
            bestLapMillis = null,
            sessionElapsedMillis = 0L,
            checkpointedSampleCount = 0,
            speedMetersPerSecond = null,
            accuracyMeters = null,
            source = null,
            deltaDisplay = DeltaDisplayState.from(
                LiveDeltaSnapshot.Unavailable(DeltaUnavailableReason.NoCurrentLap),
            ),
            referenceLapMillis = null,
        )
    }
}

/**
 * Recovery prompt state returned by [SessionController.loadUnfinishedDraft] on
 * app launch (D-15). Offers Resume / Save / Discard and never auto-promotes a
 * draft into formal Review history (D-16).
 */
data class DraftRecoveryPrompt(
    val sessionId: String,
    val trackId: String,
    val trackName: String,
    val state: TimingDraftState,
    val availableActions: List<DraftRecoveryAction>,
)

/** Actions the user can take on a recovered draft (D-15). */
enum class DraftRecoveryAction { Resume, Save, Discard }

/** Outcome of [SessionController.startTiming]. */
sealed interface StartTimingResult {
    /** Timing started; an active draft exists. */
    data class Started(val sessionId: String) : StartTimingResult

    /**
     * Timing blocked because no saved Track with a confirmed start/finish
     * exists (D-19). [message] is the exact UI-SPEC copy.
     */
    data class Blocked(val message: String) : StartTimingResult
}

/** Outcome of saving a stopped draft. */
sealed interface SaveDraftResult {
    /** The draft was promoted to formal Review history. */
    data class Saved(val sessionId: String) : SaveDraftResult

    /** No stopped draft was available to save. */
    data object NothingToSave : SaveDraftResult
}

// --- Ghost reference-lap persistence (D-01..D-05, D-12, D-24) ------------------

/**
 * Serializable mirror of [ProgressPoint] persisted inside a
 * [GhostReferencePayloadV1] (D-05/D-06).
 *
 * Kept separate from the pure ghost domain model so the algorithm layer stays
 * free of serialization concerns while saved payloads remain canonical and
 * versioned.
 */
@Serializable
data class ProgressPointDto(
    val elapsedMillis: Long,
    val progressMeters: Double,
    val normalizedProgress: Double,
    val latitude: Double,
    val longitude: Double,
    val localX: Double,
    val localY: Double,
    val speedMetersPerSecond: Double? = null,
    val headingDegrees: Double? = null,
    val horizontalAccuracyMeters: Double? = null,
)

/**
 * Canonical, versioned payload for one persisted ghost reference lap (D-05, D-25).
 *
 * Stores BOTH the raw best-lap samples (for replay/export/future algorithm work)
 * and the precomputed progress curve (so live delta does not rescan raw GPS).
 * Real and simulated references are isolated by [SourceMetadata.isSimulated] so a
 * demo lap never becomes a real Track's ghost target (D-04, D-24). References are
 * scoped per [trackId] (D-03).
 */
@Serializable
data class GhostReferencePayloadV1(
    val schemaVersion: Int = 1,
    val trackId: String,
    val sessionId: String,
    val lapNumber: Int,
    val durationMillis: Long,
    val source: SourceMetadata,
    val totalDistanceMeters: Double,
    val samples: List<LocationSampleDto>,
    val progressPoints: List<ProgressPointDto>,
    val app: AppMetadata,
)

/** Maps a domain [ProgressPoint] to its serializable DTO. */
fun ProgressPoint.toDto(): ProgressPointDto = ProgressPointDto(
    elapsedMillis = elapsedMillis,
    progressMeters = progressMeters,
    normalizedProgress = normalizedProgress,
    latitude = latitude,
    longitude = longitude,
    localX = localX,
    localY = localY,
    speedMetersPerSecond = speedMetersPerSecond,
    headingDegrees = headingDegrees,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
)

/** Maps a [ProgressPointDto] back to the domain [ProgressPoint]. */
fun ProgressPointDto.toModel(): ProgressPoint = ProgressPoint(
    elapsedMillis = elapsedMillis,
    progressMeters = progressMeters,
    normalizedProgress = normalizedProgress,
    latitude = latitude,
    longitude = longitude,
    localX = localX,
    localY = localY,
    speedMetersPerSecond = speedMetersPerSecond,
    headingDegrees = headingDegrees,
    horizontalAccuracyMeters = horizontalAccuracyMeters,
)

/**
 * Maps a domain [ReferenceLap] to a serializable [GhostReferencePayloadV1].
 *
 * The [source] carries the real/simulated boundary used for storage isolation;
 * its [SourceMetadata.isSimulated] must match the reference lap's own flag.
 */
fun ReferenceLap.toReferencePayload(source: SourceMetadata, app: AppMetadata): GhostReferencePayloadV1 =
    GhostReferencePayloadV1(
        trackId = trackId,
        sessionId = sessionId,
        lapNumber = lapNumber,
        durationMillis = durationMillis,
        source = source,
        totalDistanceMeters = progressCurve.totalDistanceMeters,
        samples = rawSamples.map { it.toDto() },
        progressPoints = progressCurve.points.map { it.toDto() },
        app = app,
    )

/**
 * Maps a domain [ReferenceLap] to a V2 payload keyed by the full compatibility
 * slot. The source flag must agree with [ReferenceLap.compatibilityKey] so a
 * real lookup can never consume a simulated payload (T-05-21).
 */
fun ReferenceLap.toReferencePayloadV2(source: SourceMetadata, app: AppMetadata): GhostReferencePayloadV2 {
    val payload = GhostReferencePayloadV2(
        compatibilityKey = compatibilityKey,
        sessionId = sessionId,
        lapNumber = lapNumber,
        durationMillis = durationMillis,
        source = source,
        totalDistanceMeters = progressCurve.totalDistanceMeters,
        samples = rawSamples.map { it.toDto() },
        progressPoints = progressCurve.points.map { it.toDto() },
        app = app,
    )
    when (val validation = GhostCompatibility.validateReferencePayload(payload)) {
        CourseCompatibilityValidation.Valid -> Unit
        is CourseCompatibilityValidation.Invalid -> throw IllegalArgumentException(validation.reason)
    }
    return payload
}

/** Maps a persisted [GhostReferencePayloadV1] back to the domain [ReferenceLap]. */
fun GhostReferencePayloadV1.toReferenceLap(): ReferenceLap = ReferenceLap(
    trackId = trackId,
    sessionId = sessionId,
    lapNumber = lapNumber,
    durationMillis = durationMillis,
    isSimulated = source.isSimulated,
    rawSamples = samples.map { it.toModel() },
    progressCurve = ProgressCurve(
        totalDistanceMeters = totalDistanceMeters,
        points = progressPoints.map { it.toModel() },
    ),
    compatibilityKey = GhostCompatibility.migratedV1Key(
        trackId = trackId,
        isSimulated = source.isSimulated,
    ),
)

/** Maps a persisted V2 reference payload back to the domain [ReferenceLap]. */
fun GhostReferencePayloadV2.toReferenceLap(): ReferenceLap = ReferenceLap(
    trackId = compatibilityKey.profileId,
    sessionId = sessionId,
    lapNumber = lapNumber,
    durationMillis = durationMillis,
    isSimulated = compatibilityKey.isSimulated,
    rawSamples = samples.map { it.toModel() },
    progressCurve = ProgressCurve(
        totalDistanceMeters = totalDistanceMeters,
        points = progressPoints.map { it.toModel() },
    ),
    compatibilityKey = compatibilityKey,
)
