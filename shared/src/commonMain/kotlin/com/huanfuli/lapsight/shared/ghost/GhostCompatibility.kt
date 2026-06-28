package com.huanfuli.lapsight.shared.ghost

import com.huanfuli.lapsight.shared.track.CourseCompatibilityKey as TrackCourseCompatibilityKey
import com.huanfuli.lapsight.shared.track.CourseDirection
import com.huanfuli.lapsight.shared.track.CourseSnapshot
import com.huanfuli.lapsight.shared.track.GhostReferencePayloadV2

/**
 * Ghost/reference compatibility contract (Phase 5, D-15/D-16/D-18/D-19).
 *
 * The serialized key lives with the course-profile DTOs so session snapshots and
 * V2 payloads can persist it. This typealias exposes the same value object from
 * the ghost package, where reference selection and validation use it.
 */
typealias CourseCompatibilityKey = TrackCourseCompatibilityKey

/** Result of validating a compatibility key against surrounding reference data. */
sealed interface CourseCompatibilityValidation {
    data object Valid : CourseCompatibilityValidation
    data class Invalid(val reason: String) : CourseCompatibilityValidation
}

/**
 * Central gate for full compatibility equality and structural validation.
 *
 * Compatibility is explicit identity, not a floating-point geometry hash:
 * profile, geometry compatibility id, direction, and source slot must all match.
 */
object GhostCompatibility {

    /** Deterministic key assigned to V1 Track-scoped references during migration. */
    fun migratedV1Key(trackId: String, isSimulated: Boolean): CourseCompatibilityKey = CourseCompatibilityKey(
        profileId = trackId,
        geometryCompatibilityId = "$trackId:g1",
        direction = CourseDirection.Recorded,
        isSimulated = isSimulated,
    )

    /** Validate the key's opaque ids before any comparison or storage lookup. */
    fun validateKey(key: CourseCompatibilityKey): CourseCompatibilityValidation = when {
        !isSafeId(key.profileId) ->
            CourseCompatibilityValidation.Invalid("unsafe profile id")
        !isSafeId(key.geometryCompatibilityId) ->
            CourseCompatibilityValidation.Invalid("unsafe geometry compatibility id")
        else -> CourseCompatibilityValidation.Valid
    }

    /** Validate that a session/course snapshot implies exactly [key]. */
    fun validateSnapshotKey(
        snapshot: CourseSnapshot,
        key: CourseCompatibilityKey,
    ): CourseCompatibilityValidation {
        validateKey(key).let { if (it is CourseCompatibilityValidation.Invalid) return it }
        return when {
            snapshot.profileId != key.profileId ->
                CourseCompatibilityValidation.Invalid("snapshot profile id mismatch")
            snapshot.geometryCompatibilityId != key.geometryCompatibilityId ->
                CourseCompatibilityValidation.Invalid("snapshot geometry compatibility mismatch")
            snapshot.direction != key.direction ->
                CourseCompatibilityValidation.Invalid("snapshot direction mismatch")
            else -> CourseCompatibilityValidation.Valid
        }
    }

    /** Validate a V2 persisted reference before it can be treated as compatible. */
    fun validateReferencePayload(payload: GhostReferencePayloadV2): CourseCompatibilityValidation {
        validateKey(payload.compatibilityKey).let { if (it is CourseCompatibilityValidation.Invalid) return it }
        return when {
            payload.source.isSimulated != payload.compatibilityKey.isSimulated ->
                CourseCompatibilityValidation.Invalid("source boundary mismatch")
            else -> CourseCompatibilityValidation.Valid
        }
    }

    /** True only when both references occupy the same profile/geometry/direction/source slot. */
    fun isSameSlot(a: CourseCompatibilityKey, b: CourseCompatibilityKey): Boolean = a == b

    private fun isSafeId(id: String): Boolean =
        id.isNotBlank() &&
            !id.contains('/') &&
            !id.contains('\\') &&
            !id.contains("..") &&
            id.none { it.isISOControl() }
}
