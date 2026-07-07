package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.nowEpochMillis
import com.huanfuli.lapsight.shared.session.AppMetadata
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.storage.SchemaMigrations

/**
 * Resolves the explicit current Track selection with NO newest-Track fallback
 * (D-01 through D-04, SC-01, SC-03).
 *
 * The controller is plain Kotlin (no Compose, no file I/O). It reads only the
 * persisted [CurrentTrackSelection] and the exactly-named profile it points at; it
 * NEVER derives a Track from "newest" or "only" heuristics. Every unavailable
 * situation — no selection, a deleted (stale) profile, an archived profile, a
 * corrupt selection/profile, or a profile whose latest revision has no confirmed
 * start/finish — collapses into a distinct typed [CurrentProfileResolution] so the
 * UI can block Timing and route the user to the explicit selector.
 */
class TrackProfileController(
    private val store: LocalSessionStore,
) {

    /**
     * Resolves the persisted current selection into a typed result.
     *
     * Resolution is exact and fallback-free:
     * 1. No persisted selection (or a migrated `null` profile) -> [CurrentProfileResolution.None].
     * 2. A corrupt selection payload -> [CurrentProfileResolution.Corrupt].
     * 3. A selection pointing at a now-missing profile -> [CurrentProfileResolution.Stale].
     * 4. A corrupt profile payload -> [CurrentProfileResolution.Corrupt].
     * 5. An archived profile -> [CurrentProfileResolution.Archived].
     * 6. A profile whose latest revision is not timing-ready -> [CurrentProfileResolution.NotTimingReady].
     * 7. Otherwise -> [CurrentProfileResolution.Selected] with the exact profile, its
     *    latest timing-ready revision, and the remembered [CourseDirection].
     */
    /**
     * Creates the FIRST (or another) named V2 profile from a completed Track marking
     * (SC-01, Task 05-04-1).
     *
     * Behavior contract:
     * - The user-provided [name] is validated BEFORE any write: a blank name or a name
     *   carrying path-injection / control characters is rejected as
     *   [CreateProfileResult.Rejected] and NOTHING is persisted (T-05-07). A canonical
     *   storage path is NEVER derived from the name.
     * - Opaque identities (`profileId`, `revisionId`, `geometryCompatibilityId`) come
     *   from the completed marking's opaque [Track.id], never from the name. Two creates
     *   with the same name but different markings are two independent logical profiles.
     * - The created profile is persisted via [LocalSessionStore.saveProfile] (payload
     *   first / index last) with a single immutable first revision.
     * - Creating a profile does NOT set the current selection: selection stays explicit
     *   (D-01..D-04). The caller chooses when (and whether) to make it current.
     */
    fun saveProfile(track: Track, name: String, app: AppMetadata): CreateProfileResult {
        // Validate the user-provided name BEFORE any write so an unsafe or blank name
        // never reaches persistence (T-05-07). The name is metadata only; it never
        // forms a path.
        if (!isSafeProfileName(name)) {
            return CreateProfileResult.Rejected("blank or unsafe profile name")
        }
        // The opaque marking id is the only path-forming identity; reject it too if it
        // could escape its storage directory (defense in depth with the store guard).
        if (!SchemaMigrations.isSafeId(track.id)) {
            return CreateProfileResult.Rejected("unsafe profile id")
        }
        // Build the single immutable first revision with deterministic opaque geometry
        // identities (profileId / "<id>:r1" / "<id>:g1") via the canonical mapping, then
        // stamp the validated user name. Geometry IDs are never derived from the name.
        val profile = SchemaMigrations
            .migrateTrack(TrackPayloadV1(track = track, app = app))
            .copy(name = name.trim())
        // Persist payload-first / index-last. Creating a profile does NOT establish a
        // current selection: selection stays an explicit, separate step (D-01..D-04).
        store.saveProfile(profile, app)
        return CreateProfileResult.Created(profile)
    }

    /**
     * Appends an immutable geometry revision to an existing profile (Plan 05-06 Task 2;
     * D-12 through D-15).
     *
     * Behavior contract:
     * - A revision is only timing-meaningful with a confirmed start/finish, so a
     *   [courseSetup] whose [CourseSetup.startFinish] is null is rejected and NOTHING is
     *   written ([AppendRevisionResult.Rejected]); the offline editor (Plan 05-06 Task 1)
     *   only emits such a setup once `canSave` is true, this is defense in depth (D-05).
     * - The target profile is loaded as an aggregate; a missing or corrupt profile is a
     *   typed rejection, never a silent create.
     * - The new revision is APPENDED with a strictly increasing [TrackRevision.ordinal]
     *   and a deterministic exact-history [TrackRevision.revisionId]. Prior revisions are
     *   copied forward unchanged — historical geometry is never overwritten (D-12, D-13).
     * - Geometry compatibility follows D-15: a Sector-only edit (same reference line and
     *   same start/finish) carries the prior [TrackRevision.geometryCompatibilityId]
     *   forward so existing Ghost references stay reusable; a reference-line or
     *   start/finish change regenerates a fresh compatibility id. Compatibility is decided
     *   by identity comparison, never by hashing serialized floating-point geometry.
     * - On success the whole updated aggregate is persisted via [LocalSessionStore.saveProfile]
     *   (payload-first / index-last) and returned so the caller can show updated history
     *   immediately.
     */
    fun appendRevision(
        profileId: String,
        referenceLine: TrackReferenceLine,
        courseSetup: CourseSetup,
        app: AppMetadata,
        sourceMarkingSessionId: String? = null,
        now: () -> Long = ::nowEpochMillisSafe,
    ): AppendRevisionResult {
        // A confirmed start/finish is mandatory for a timing-ready revision (D-05). Reject
        // an invalid/partial setup WITHOUT writing so a half-finished edit never appends.
        if (courseSetup.startFinish == null ||
            (courseSetup.topology == CourseTopology.PointToPoint && courseSetup.finishLine == null)
        ) {
            return AppendRevisionResult.Rejected("course setup has no confirmed start/finish")
        }

        val profile = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return AppendRevisionResult.Rejected("no such profile")
            is LoadResult.Corrupt -> return AppendRevisionResult.Rejected("profile payload corrupt: ${loaded.reason}")
        }

        // Defense in depth on the append-only invariant: refuse to append onto a profile
        // whose stored revisions are not strictly increasing by ordinal (a corrupt
        // history), so a colliding/non-monotonic ordinal can never be produced (T-05-16).
        if (!revisionsStrictlyIncreasing(profile)) {
            return AppendRevisionResult.Rejected("corrupt revision history (non-increasing ordinals)")
        }
        // Bound the immutable history so a runaway caller cannot grow an aggregate without
        // limit; the cap is generous and only trips on clearly abnormal usage.
        if (profile.revisions.size >= MAX_REVISIONS_PER_PROFILE) {
            return AppendRevisionResult.Rejected("revision history is full")
        }

        val latest = profile.latestRevision
        val nextOrdinal = (latest?.ordinal ?: 0) + 1

        // D-15: carry compatibility forward for a Sector-only change; regenerate it when
        // the reference line or start/finish geometry changes.
        val geometryChanged = latest == null ||
            latest.referenceLine != referenceLine ||
            latest.courseSetup.topology != courseSetup.topology ||
            latest.courseSetup.startFinish != courseSetup.startFinish ||
            latest.courseSetup.finishLine != courseSetup.finishLine
        val geometryCompatibilityId = if (geometryChanged) {
            "$profileId:g$nextOrdinal"
        } else {
            latest.geometryCompatibilityId
        }

        val revision = TrackRevision(
            revisionId = "$profileId:r$nextOrdinal",
            ordinal = nextOrdinal,
            createdAtEpochMillis = now(),
            sourceMarkingSessionId = sourceMarkingSessionId ?: latest?.sourceMarkingSessionId,
            referenceLine = referenceLine,
            courseSetup = courseSetup,
            geometryCompatibilityId = geometryCompatibilityId,
        )

        // Append immutably: prior revisions are copied forward unchanged (D-12, D-13).
        val updated = profile.copy(revisions = profile.revisions + revision)
        store.saveProfile(updated, app)
        return AppendRevisionResult.Appended(profile = updated, revision = revision)
    }

    /**
     * Renames a profile's display name without touching geometry (D-12, SC-01).
     *
     * Behavior contract:
     * - The new [newName] is validated BEFORE any write; a blank or path-unsafe name is a
     *   [RenameProfileResult.Rejected] and NOTHING is persisted (T-05-16). The name is
     *   metadata only and never forms a storage path.
     * - Every immutable [TrackRevision] is copied forward unchanged: rename mutates only
     *   the profile's name, never its revision history (D-12).
     * - A missing or corrupt profile is a typed rejection, never a silent create.
     */
    fun renameProfile(profileId: String, newName: String, app: AppMetadata): RenameProfileResult {
        if (!isSafeProfileName(newName)) {
            return RenameProfileResult.Rejected("blank or unsafe profile name")
        }
        val profile = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return RenameProfileResult.Rejected("no such profile")
            is LoadResult.Corrupt -> return RenameProfileResult.Rejected("profile payload corrupt: ${loaded.reason}")
        }
        // Metadata-only change: revisions are carried forward byte-for-byte (D-12).
        val renamed = profile.copy(name = newName.trim())
        store.saveProfile(renamed, app)
        return RenameProfileResult.Renamed(renamed)
    }

    /**
     * Archives a profile: removes it from active selectors WITHOUT deleting any revision,
     * session, or Ghost, and clears ONLY a current selection that points at it (D-16,
     * D-01, D-03).
     *
     * Behavior contract:
     * - Archive is a reversible data-state change (an `archivedAtEpochMillis` stamp), never
     *   a file deletion (T-05-18). All revisions are preserved so old sessions/references
     *   remain loadable.
     * - It is idempotent: re-archiving keeps the ORIGINAL archive timestamp.
     * - If (and only if) the persisted current selection names THIS profile, the selection
     *   is atomically cleared; a non-matching selection is left untouched and no
     *   replacement Track is ever chosen (D-01/D-03).
     * - A missing or corrupt profile is a typed rejection.
     */
    fun archiveProfile(
        profileId: String,
        app: AppMetadata,
        now: () -> Long = ::nowEpochMillisSafe,
    ): ArchiveProfileResult {
        val profile = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return ArchiveProfileResult.Rejected("no such profile")
            is LoadResult.Corrupt -> return ArchiveProfileResult.Rejected("profile payload corrupt: ${loaded.reason}")
        }

        // Idempotent: keep the first archive timestamp; never delete or rewrite revisions.
        val archivedAt = profile.archivedAtEpochMillis ?: now()
        val archived = profile.copy(archivedAtEpochMillis = archivedAt)
        store.saveProfile(archived, app)

        // Clear ONLY a current selection that matches this profile; never select a
        // replacement (D-03). A missing/corrupt selection is treated as "not matching".
        val clearedCurrentSelection = when (val selection = store.loadCurrentSelection()) {
            is LoadResult.Loaded -> if (selection.value.profileId == profileId) {
                store.clearCurrentSelection()
                true
            } else {
                false
            }
            LoadResult.NotFound, is LoadResult.Corrupt -> false
        }

        return ArchiveProfileResult.Archived(
            profile = archived,
            clearedCurrentSelection = clearedCurrentSelection,
        )
    }

    /**
     * Restores an archived profile to the active Track list without changing current selection.
     *
     * Restore is metadata-only: revisions, sessions, and Ghost references are copied forward
     * unchanged. The user still explicitly chooses the current Track from Drive.
     */
    fun restoreProfile(profileId: String, app: AppMetadata): RestoreProfileResult {
        val profile = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return RestoreProfileResult.Rejected("no such profile")
            is LoadResult.Corrupt -> return RestoreProfileResult.Rejected("profile payload corrupt: ${loaded.reason}")
        }
        val restored = profile.copy(archivedAtEpochMillis = null)
        store.saveProfile(restored, app)
        return RestoreProfileResult.Restored(restored)
    }

    /**
     * Duplicates a profile into a fully INDEPENDENT logical profile (D-16).
     *
     * Behavior contract:
     * - [newName] and [newProfileId] are validated BEFORE any write; a blank/unsafe name or
     *   an unsafe id is a [DuplicateProfileResult.Rejected] and nothing is persisted
     *   (T-05-16). The duplicate's storage path is derived from the opaque
     *   [newProfileId], never from the name.
     * - The source profile is copied deeply: every revision gets a FRESH [TrackRevision.revisionId]
     *   and a fresh [TrackRevision.geometryCompatibilityId] tied to [newProfileId], so a copied
     *   Ghost reference can never collide with the source (D-16, T-05-18). The source's internal
     *   compatibility grouping (e.g. a sector-only edit sharing one id across revisions) is
     *   preserved by remapping each DISTINCT source compatibility id to one fresh id.
     * - The duplicate starts un-archived and is NOT made current (selection stays explicit).
     * - The source profile is left completely unchanged.
     */
    fun duplicateProfile(
        profileId: String,
        newName: String,
        newProfileId: String,
        app: AppMetadata,
        now: () -> Long = ::nowEpochMillisSafe,
    ): DuplicateProfileResult {
        if (!isSafeProfileName(newName)) {
            return DuplicateProfileResult.Rejected("blank or unsafe profile name")
        }
        if (!SchemaMigrations.isSafeId(newProfileId)) {
            return DuplicateProfileResult.Rejected("unsafe profile id")
        }
        val source = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return DuplicateProfileResult.Rejected("no such profile")
            is LoadResult.Corrupt -> return DuplicateProfileResult.Rejected("profile payload corrupt: ${loaded.reason}")
        }

        // Remap each DISTINCT source compatibility id to one fresh, profile-scoped id so the
        // duplicate keeps its internal sharing structure while being isolated from the source.
        val compatRemap = HashMap<String, String>()
        var freshCompatCounter = 0
        val copiedRevisions = source.revisions.sortedBy { it.ordinal }.map { revision ->
            val freshCompat = compatRemap.getOrPut(revision.geometryCompatibilityId) {
                freshCompatCounter += 1
                "$newProfileId:g$freshCompatCounter"
            }
            revision.copy(
                revisionId = "$newProfileId:r${revision.ordinal}",
                geometryCompatibilityId = freshCompat,
            )
        }

        val duplicate = TrackProfile(
            profileId = newProfileId,
            name = newName.trim(),
            createdAtEpochMillis = now(),
            source = source.source,
            revisions = copiedRevisions,
            archivedAtEpochMillis = null,
            preferredDirection = source.preferredDirection,
        )
        store.saveProfile(duplicate, app)
        return DuplicateProfileResult.Duplicated(source = source, duplicate = duplicate)
    }

    /**
     * Lists a profile's full immutable revision history in append order, including the
     * history of ARCHIVED profiles (exact-history navigation remains available even though
     * an archived profile leaves active selectors, D-13, D-16).
     */
    fun listHistory(profileId: String): ProfileHistoryResult {
        val profile = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return ProfileHistoryResult.NotFound
            is LoadResult.Corrupt -> return ProfileHistoryResult.Corrupt(loaded.reason)
        }
        return ProfileHistoryResult.History(
            profile = profile,
            revisions = profile.revisions.sortedBy { it.ordinal },
        )
    }

    fun resolveCurrent(): CurrentProfileResolution {
        val selection = when (val loaded = store.loadCurrentSelection()) {
            is LoadResult.Loaded -> loaded.value
            LoadResult.NotFound -> return CurrentProfileResolution.None
            is LoadResult.Corrupt -> return CurrentProfileResolution.Corrupt(loaded.reason)
        }

        val profileId = selection.profileId ?: return CurrentProfileResolution.None

        val profile = when (val loaded = store.loadProfile(profileId)) {
            is LoadResult.Loaded -> loaded.value
            // The selected profile is gone (deleted/never present): stale, NOT a
            // signal to silently pick another Track (D-03).
            LoadResult.NotFound -> return CurrentProfileResolution.Stale
            is LoadResult.Corrupt -> return CurrentProfileResolution.Corrupt(loaded.reason)
        }

        if (profile.isArchived) return CurrentProfileResolution.Archived

        // Normal selection resolves ONLY the latest revision (D-14); it must carry a
        // confirmed start/finish to be timing-ready (D-05).
        val revision = profile.latestRevision
        if (revision == null ||
            revision.courseSetup.startFinish == null ||
            (revision.courseSetup.topology == CourseTopology.PointToPoint && revision.courseSetup.finishLine == null)
        ) {
            return CurrentProfileResolution.NotTimingReady
        }

        return CurrentProfileResolution.Selected(
            profile = profile,
            revision = revision,
            direction = selection.direction,
        )
    }

    /**
     * True when [profile]'s stored revisions are strictly increasing by ordinal in append
     * order (the append-only invariant). A profile that violates it is treated as corrupt
     * history and refused for further appends.
     */
    private fun revisionsStrictlyIncreasing(profile: TrackProfile): Boolean {
        val ordinals = profile.revisions.map { it.ordinal }
        if (ordinals.any { it <= 0 }) return false
        for (i in 1 until ordinals.size) {
            if (ordinals[i] <= ordinals[i - 1]) return false
        }
        return true
    }

    companion object {
        /**
         * Upper bound on the number of immutable revisions a single profile may retain.
         * Generous enough that normal editing never approaches it; it only guards against a
         * runaway caller growing one aggregate without limit.
         */
        const val MAX_REVISIONS_PER_PROFILE: Int = 100
    }
}

/**
 * Typed outcome of resolving the persisted current Track selection (D-01..D-04).
 *
 * Every unavailable case is distinct so the UI can present an accurate message and
 * a direct selector action; none of them falls back to another profile.
 */
sealed interface CurrentProfileResolution {

    /** The exact persisted profile, its latest timing-ready revision, and direction. */
    data class Selected(
        val profile: TrackProfile,
        val revision: TrackRevision,
        val direction: CourseDirection,
    ) : CurrentProfileResolution

    /** No selection has been persisted (or migration left it `null`). */
    data object None : CurrentProfileResolution

    /** A selection exists but the profile it names no longer exists. */
    data object Stale : CurrentProfileResolution

    /** The selected profile has been archived; it leaves active selectors (D-16). */
    data object Archived : CurrentProfileResolution

    /** The selection or its profile payload is malformed. */
    data class Corrupt(val reason: String) : CurrentProfileResolution

    /** The selected profile's latest revision has no confirmed start/finish (D-05). */
    data object NotTimingReady : CurrentProfileResolution
}

/**
 * Typed outcome of the create-first-profile path ([TrackProfileController.saveProfile]).
 *
 * A name that is blank or carries unsafe characters yields [Rejected] and writes
 * nothing; a valid create yields [Created] with the freshly persisted profile. The
 * caller never needs to catch an exception to discover an invalid name.
 */
sealed interface CreateProfileResult {

    /** The profile was validated and persisted (but NOT auto-selected). */
    data class Created(val profile: TrackProfile) : CreateProfileResult

    /** The name was blank or unsafe; nothing was written. */
    data class Rejected(val reason: String) : CreateProfileResult
}

/**
 * Typed outcome of [TrackProfileController.appendRevision] (D-12..D-15).
 *
 * An invalid/partial edit or a missing/corrupt profile yields [Rejected] and writes
 * nothing; a valid save yields [Appended] with the whole updated aggregate plus the
 * freshly appended revision so the caller can render history immediately. The caller
 * never needs to catch an exception to discover a rejected edit.
 */
sealed interface AppendRevisionResult {

    /** The revision was appended and the updated profile aggregate persisted. */
    data class Appended(val profile: TrackProfile, val revision: TrackRevision) : AppendRevisionResult

    /** The edit was invalid, or the target profile was missing/corrupt; nothing was written. */
    data class Rejected(val reason: String) : AppendRevisionResult
}

/**
 * Typed outcome of [TrackProfileController.renameProfile] (D-12).
 *
 * A blank/unsafe name or a missing/corrupt profile yields [Rejected] and writes nothing;
 * a valid rename yields [Renamed] with the updated profile (revisions unchanged).
 */
sealed interface RenameProfileResult {

    /** The name was updated; every immutable revision was carried forward unchanged. */
    data class Renamed(val profile: TrackProfile) : RenameProfileResult

    /** The name was blank/unsafe, or the profile was missing/corrupt; nothing was written. */
    data class Rejected(val reason: String) : RenameProfileResult
}

/**
 * Typed outcome of [TrackProfileController.archiveProfile] (D-16, D-01/D-03).
 *
 * [Archived] reports the archived aggregate and whether a matching current selection was
 * cleared; a missing/corrupt profile yields [Rejected]. Archive never deletes data.
 */
sealed interface ArchiveProfileResult {

    /** The profile was archived (history preserved). [clearedCurrentSelection] is true iff the current selection matched it. */
    data class Archived(
        val profile: TrackProfile,
        val clearedCurrentSelection: Boolean,
    ) : ArchiveProfileResult

    /** The target profile was missing or corrupt; nothing was written. */
    data class Rejected(val reason: String) : ArchiveProfileResult
}

/** Typed outcome of [TrackProfileController.restoreProfile]. */
sealed interface RestoreProfileResult {

    /** The profile is active again; no current Track was auto-selected. */
    data class Restored(val profile: TrackProfile) : RestoreProfileResult

    /** The target profile was missing or corrupt; nothing was written. */
    data class Rejected(val reason: String) : RestoreProfileResult
}

/**
 * Typed outcome of [TrackProfileController.duplicateProfile] (D-16).
 *
 * [Duplicated] carries the unchanged [source] and the freshly forked [duplicate] (new
 * profile/revision/compatibility ids); a blank/unsafe name, unsafe id, or missing/corrupt
 * source yields [Rejected].
 */
sealed interface DuplicateProfileResult {

    /** An independent copy was created with fresh identities; the source is unchanged. */
    data class Duplicated(val source: TrackProfile, val duplicate: TrackProfile) : DuplicateProfileResult

    /** The name/id was unsafe, or the source was missing/corrupt; nothing was written. */
    data class Rejected(val reason: String) : DuplicateProfileResult
}

/**
 * Typed outcome of [TrackProfileController.listHistory] (D-13, D-16).
 *
 * [History] carries the profile and its revisions in append order (archived profiles
 * included); a missing profile yields [NotFound] and a malformed payload yields [Corrupt].
 */
sealed interface ProfileHistoryResult {

    /** The profile's full immutable revision history, ordered by ordinal. */
    data class History(val profile: TrackProfile, val revisions: List<TrackRevision>) : ProfileHistoryResult

    /** No profile exists for the requested id. */
    data object NotFound : ProfileHistoryResult

    /** The profile payload was malformed. */
    data class Corrupt(val reason: String) : ProfileHistoryResult
}

/** Wall-clock epoch millis that never throws (mirrors the SessionController guard). */
private fun nowEpochMillisSafe(): Long = try {
    nowEpochMillis()
} catch (_: Throwable) {
    0L
}

/**
 * A profile name is safe when it is non-blank and cannot smuggle a path separator,
 * a parent-directory escape, or a control character — defense in depth so a name can
 * never become or influence a canonical storage path (T-05-07).
 */
fun isSafeProfileName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.isNotBlank() &&
        trimmed.length in 1..50 &&
        !trimmed.contains('/') &&
        !trimmed.contains('\\') &&
        !trimmed.contains("..") &&
        trimmed.none { it.isISOControl() }
}
