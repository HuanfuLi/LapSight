package com.huanfuli.lapsight.shared.session

import com.huanfuli.lapsight.shared.LocationSample
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.lap.LapEvent
import com.huanfuli.lapsight.shared.lap.SectorEvent
import com.huanfuli.lapsight.shared.storage.CURRENT_SESSION_SCHEMA_VERSION
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

/** Serializable mirror of [SectorEvent] persisted inside a [TimingSessionPayloadV1]. */
@Serializable
data class SectorEventDto(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val crossingMillis: Long,
    val splitMillis: Long,
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
    val schemaVersion: Int = CURRENT_SESSION_SCHEMA_VERSION,
    val session: TimingSession,
    val app: AppMetadata,
    val samples: List<LocationSampleDto>,
    val laps: List<LapDto>,
    val sectorEvents: List<SectorEventDto>,
    val gpsQuality: GpsQualitySummary,
    val totalDurationMillis: Long = 0L,
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
