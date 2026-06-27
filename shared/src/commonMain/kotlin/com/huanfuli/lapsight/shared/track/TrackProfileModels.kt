package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.session.GeoPointDto
import com.huanfuli.lapsight.shared.session.GpsQualitySummary
import com.huanfuli.lapsight.shared.session.LapDto
import com.huanfuli.lapsight.shared.session.LocationSampleDto
import com.huanfuli.lapsight.shared.session.ProgressPointDto
import com.huanfuli.lapsight.shared.session.SourceMetadata
import kotlinx.serialization.Serializable

/**
 * V2 course-profile domain (Phase 5, D-12 through D-19).
 *
 * These types model a *logical* [TrackProfile] with a stable identity and an
 * append-only list of immutable [TrackRevision]s. They are deliberately separate
 * from the frozen V1 `TrackPayloadV1` / `TimingSessionPayloadV1` shapes so the old
 * on-disk meaning is never reinterpreted; `SchemaMigrations` dispatches on the
 * literal `schemaVersion` and maps V1 into these contracts.
 *
 * Identity rules (encoded here, proven by `SchemaMigrationTest`):
 * - [TrackProfile.profileId] is stable; a duplicate always gets a new id.
 * - [TrackRevision.revisionId] is the exact-history key and is never reused.
 * - [TrackRevision.geometryCompatibilityId] is an explicit, injected identity. It
 *   is carried forward unchanged by sector-only revisions and regenerated when the
 *   reference line or start/finish changes (D-15). It is NEVER a hash of serialized
 *   floating-point geometry.
 */

/** Selectable course variant under one physical revision (D-18). */
@Serializable
enum class CourseDirection {
    /** The direction the marking lap was driven. */
    Recorded,

    /** The reverse of the recorded direction (shared geometry, reversed progress). */
    Reverse,
}

/**
 * One intermediate timing boundary (a Split line) within a [CourseSetup].
 *
 * A boundary is NOT a Sector: for `N` configured Sectors there are `N-1`
 * boundaries (D-06). [line] carries the finite endpoints in canonical
 * latitude/longitude. [normalizedProgress] is the absolute arc-length anchor on
 * the *recorded* orientation (0.0..1.0); it is `null` for V1-migrated boundaries
 * until the offline editor (Plan 05-05) re-derives it from the reference line.
 */
@Serializable
data class SectorBoundary(
    val id: String,
    val order: Int,
    val pointA: GeoPointDto,
    val pointB: GeoPointDto,
    val normalizedProgress: Double? = null,
)

/**
 * The editable course configuration for a single revision (D-05 through D-11).
 *
 * [sectorsEnabled] is false when Sector timing is off; otherwise [sectorCount] is
 * 2..6 and [boundaries] holds exactly `sectorCount - 1` intermediate boundaries
 * in recorded order. [startFinish] is the confirmed timing boundary required for
 * formal Timing (D-05); it is nullable because a Track may be saved before
 * start/finish is confirmed, in which case the revision is simply not yet
 * timing-ready. [startFinishProgress] is the absolute normalized arc-length anchor
 * (0.0..1.0) of start/finish on the *recorded* orientation, so the offline editor
 * (Plan 05-05) and later direction transforms can regenerate finite endpoints from
 * progress alone instead of trusting persisted canvas coordinates (D-09, D-10). It
 * is additive and defaults to `null` for V1-migrated setups.
 */
@Serializable
data class CourseSetup(
    val startFinish: StartFinishLineDto? = null,
    val startFinishProgress: Double? = null,
    val sectorsEnabled: Boolean = false,
    val sectorCount: Int = 0,
    val boundaries: List<SectorBoundary> = emptyList(),
)

/**
 * An immutable geometry revision of a [TrackProfile] (D-12, D-13).
 *
 * Never mutated after it is appended. [ordinal] is 1-based and strictly increasing
 * in append order; [revisionId] is the exact-history navigation key.
 */
@Serializable
data class TrackRevision(
    val revisionId: String,
    val ordinal: Int,
    val createdAtEpochMillis: Long,
    val sourceMarkingSessionId: String?,
    val referenceLine: TrackReferenceLine,
    val courseSetup: CourseSetup,
    val geometryCompatibilityId: String,
)

/**
 * A stable logical Track profile containing immutable revisions (D-12 through D-16).
 *
 * Name and [preferredDirection] are mutable non-geometric metadata. Geometry edits
 * append a new [TrackRevision] instead of overwriting. [archivedAtEpochMillis] is
 * non-null once archived; archived profiles leave active selectors but retain all
 * revisions/sessions/Ghosts (D-16).
 */
@Serializable
data class TrackProfile(
    val profileId: String,
    val name: String,
    val createdAtEpochMillis: Long,
    val source: SourceMetadata,
    val revisions: List<TrackRevision> = emptyList(),
    val archivedAtEpochMillis: Long? = null,
    val preferredDirection: CourseDirection = CourseDirection.Recorded,
) {
    /** The most recently appended revision, or null for a profile with no geometry yet. */
    val latestRevision: TrackRevision? get() = revisions.maxByOrNull { it.ordinal }

    /** True once the profile has been archived (D-16). */
    val isArchived: Boolean get() = archivedAtEpochMillis != null
}

/**
 * The identity that decides whether a fastest lap / Ghost reference is reusable
 * (D-15, D-16, D-19).
 *
 * `revisionId` is intentionally absent: sector-only revisions must remain
 * compatible, so the key uses [geometryCompatibilityId] instead. [profileId]
 * keeps duplicated-but-identical geometry on independent history.
 */
@Serializable
data class CourseCompatibilityKey(
    val profileId: String,
    val geometryCompatibilityId: String,
    val direction: CourseDirection,
    val isSimulated: Boolean,
)

/**
 * A preserved V1 cumulative line split (the legacy `SectorEventDto` meaning).
 *
 * V1 stored only a cumulative split from lap start to a timing line; it never
 * modeled a complete final Sector. Migration keeps this shape verbatim so history
 * is never silently relabeled as a complete-coverage Sector result (D-06, D-15).
 */
@Serializable
data class LegacyCumulativeSplit(
    val lapNumber: Int,
    val sectorId: String,
    val sectorOrder: Int,
    val crossingMillis: Long,
    val cumulativeSplitMillis: Long,
)

/**
 * The immutable course identity + geometry captured into every V2 session (D-14).
 *
 * Historical Review/export must render THIS snapshot, never the profile's latest
 * geometry, so a later revision or archive cannot rewrite an old session visually.
 * [isLegacyMigrated] marks snapshots reconstructed from a V1 session; their
 * [legacySplits] are cumulative line splits, not complete Sectors.
 */
@Serializable
data class CourseSnapshot(
    val profileId: String,
    val revisionId: String,
    val geometryCompatibilityId: String,
    val direction: CourseDirection,
    val referenceLine: TrackReferenceLine,
    val startFinish: StartFinishLineDto,
    val boundaries: List<SectorBoundary> = emptyList(),
    val legacySplits: List<LegacyCumulativeSplit> = emptyList(),
    val isLegacyMigrated: Boolean = false,
) {
    /** The compatibility key implied by this snapshot for a given source boundary. */
    fun compatibilityKey(isSimulated: Boolean): CourseCompatibilityKey = CourseCompatibilityKey(
        profileId = profileId,
        geometryCompatibilityId = geometryCompatibilityId,
        direction = direction,
        isSimulated = isSimulated,
    )
}

/**
 * A formal timing session bound to a profile revision via an immutable
 * [courseSnapshot] (D-14, D-19). Replaces the V1 `TimingSession`'s loose
 * start/finish + sector lines.
 */
@Serializable
data class TimingSessionV2(
    val id: String,
    val profileId: String,
    val trackName: String,
    val createdAtEpochMillis: Long,
    val source: SourceMetadata,
    val courseSnapshot: CourseSnapshot,
)

/**
 * The persisted explicit current-Track selection (D-01 through D-04).
 *
 * [profileId] is nullable and has NO V1 fallback: migration sets it to `null` so
 * the user must explicitly choose a Track after upgrade; the app never derives the
 * newest or only profile.
 */
@Serializable
data class CurrentTrackSelection(
    val profileId: String? = null,
    val direction: CourseDirection = CourseDirection.Recorded,
)

// --- V2 on-disk payloads (literal schemaVersion = 2) --------------------------

/**
 * Versioned on-disk payload wrapper for a [TrackProfile] aggregate.
 *
 * [schemaVersion] is the literal `2` and must never collide with the frozen V1
 * `1`. The whole profile (metadata + immutable revisions) is one atomic payload.
 */
@Serializable
data class TrackProfilePayloadV2(
    val schemaVersion: Int = 2,
    val profile: TrackProfile,
    val app: AppMetadata,
)

/**
 * Versioned on-disk payload wrapper for a V2 timing session.
 *
 * [schemaVersion] is the literal `2`. The immutable [TimingSessionV2.courseSnapshot]
 * is the canonical historical geometry; raw [samples] and [laps] are preserved.
 */
@Serializable
data class TimingSessionPayloadV2(
    val schemaVersion: Int = 2,
    val session: TimingSessionV2,
    val app: AppMetadata,
    val samples: List<LocationSampleDto> = emptyList(),
    val laps: List<LapDto> = emptyList(),
    val gpsQuality: GpsQualitySummary,
    val totalDurationMillis: Long = 0L,
)

/**
 * Versioned on-disk payload wrapper for a V2 Ghost reference lap.
 *
 * [schemaVersion] is the literal `2`. The reference is keyed by a full
 * [CourseCompatibilityKey] (profile + geometry + direction + source) rather than a
 * bare trackId, so sector-only revisions reuse it while geometry/direction/source
 * changes isolate it (D-15, D-19).
 */
@Serializable
data class GhostReferencePayloadV2(
    val schemaVersion: Int = 2,
    val compatibilityKey: CourseCompatibilityKey,
    val sessionId: String,
    val lapNumber: Int,
    val durationMillis: Long,
    val source: SourceMetadata,
    val totalDistanceMeters: Double,
    val samples: List<LocationSampleDto> = emptyList(),
    val progressPoints: List<ProgressPointDto> = emptyList(),
    val app: AppMetadata,
)
