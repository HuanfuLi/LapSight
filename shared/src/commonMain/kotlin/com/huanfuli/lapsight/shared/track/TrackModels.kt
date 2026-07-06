package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import kotlinx.serialization.Serializable

/**
 * Track-domain DTOs for the versioned local storage foundation (D-17 through D-25).
 *
 * These model the separate concepts from D-17: a raw continuous [TrackMarkingSession]
 * (no laps), a derived [TrackReferenceLine] (data shape only here; the extraction
 * algorithm is owned by Plan 03-04), and a saved [Track] course entity. Heavy
 * payloads are independent versioned JSON files; the [ReviewIndex] is the
 * lightweight metadata index that backs Review list screens (D-21 through D-23).
 */

/** A timing line defined by two geographic points (serializable mirror of the lap-domain line). */
@Serializable
data class StartFinishLineDto(
    val pointA: GeoPointDto,
    val pointB: GeoPointDto,
)

/** A sector line carrying stable identity and ordering. */
@Serializable
data class SectorLineDto(
    val id: String,
    val name: String,
    val order: Int,
    val pointA: GeoPointDto,
    val pointB: GeoPointDto,
)

/** Shape of a timed course. */
@Serializable
enum class CourseTopology {
    /** A normal closed circuit: one start/finish line opens and closes each lap. */
    Circuit,

    /** An open point-to-point course: a start line opens one run and a finish line closes it. */
    PointToPoint,
}

/**
 * Derived closed-loop reference polyline for a track (D-17, D-18).
 *
 * Data shape only in this plan: the points are populated later by Plan 03-04's
 * `ReferenceLineExtractor`. Kept separate from the fastest lap / ghost candidate.
 */
@Serializable
data class TrackReferenceLine(
    val points: List<GeoPointDto> = emptyList(),
    val isClosed: Boolean = true,
)

/**
 * One continuous GPS capture used to create a track (D-06, D-10, D-17).
 *
 * Holds raw samples only — never laps — so future algorithms can recompute a
 * better reference line without losing original evidence.
 */
@Serializable
data class TrackMarkingSession(
    val id: String,
    val createdAtEpochMillis: Long,
    val source: SourceMetadata,
    val samples: List<LocationSampleDto>,
)

/**
 * Saved course entity (D-17): reference line, start/finish, sector lines, metadata,
 * and a link to the source marking session. [referenceLine] and [startFinish] are
 * optional because a track may be saved before reference extraction / line
 * confirmation completes.
 */
@Serializable
data class Track(
    val id: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val sourceMarkingSessionId: String?,
    val source: SourceMetadata,
    val referenceLine: TrackReferenceLine? = null,
    val startFinish: StartFinishLineDto? = null,
    val finishLine: StartFinishLineDto? = null,
    val topology: CourseTopology = CourseTopology.Circuit,
    val sectors: List<SectorLineDto> = emptyList(),
)

/**
 * Versioned on-disk payload wrapper for a saved [Track] (D-23, D-25).
 *
 * FROZEN V1 shape: [schemaVersion] is the literal `1` and must never track the
 * mutable `CURRENT_*` constant. Phase 5 migration (`SchemaMigrations`) dispatches
 * on this literal before decoding, so the old shape can never be re-emitted under
 * a newer number. New geometry lives in the V2 `TrackProfilePayloadV2`.
 */
@Serializable
data class TrackPayloadV1(
    val schemaVersion: Int = 1,
    val track: Track,
    val app: AppMetadata,
)

/**
 * Versioned on-disk payload wrapper for a [TrackMarkingSession] (D-23, D-25).
 *
 * FROZEN V1 shape: [schemaVersion] is the literal `1` (see [TrackPayloadV1]).
 */
@Serializable
data class TrackMarkingPayloadV1(
    val schemaVersion: Int = 1,
    val marking: TrackMarkingSession,
    val app: AppMetadata,
)

/** Kind of Review entry an index row points at (D-28). */
@Serializable
enum class ReviewEntryType {
    Track,
    TrackMarking,
    TimingSession,
}

/**
 * A lightweight index row backing Review list screens (D-22).
 *
 * Carries only summary fields plus the relative payload path; full detail is
 * loaded from the canonical payload file on demand.
 */
@Serializable
data class ReviewIndexRow(
    val id: String,
    val type: ReviewEntryType,
    val name: String,
    val createdAtEpochMillis: Long,
    val source: SourceMetadata,
    val payloadPath: String,
    val sampleCount: Int? = null,
    val bestLapMillis: Long? = null,
)

/**
 * The metadata index that lists saved tracks/markings/sessions for Review (D-21, D-22).
 *
 * FROZEN V1 shape: [schemaVersion] is the literal `1`. The index is a rebuildable
 * cache; Phase 5 migration rebuilds V2 rows from canonical payloads rather than
 * trusting it as migration evidence.
 */
@Serializable
data class ReviewIndex(
    val schemaVersion: Int = 1,
    val rows: List<ReviewIndexRow> = emptyList(),
)
