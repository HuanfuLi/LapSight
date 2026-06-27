package com.huanfuli.lapsight.shared.track

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
        if (revision == null || revision.courseSetup.startFinish == null) {
            return CurrentProfileResolution.NotTimingReady
        }

        return CurrentProfileResolution.Selected(
            profile = profile,
            revision = revision,
            direction = selection.direction,
        )
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
 * A profile name is safe when it is non-blank and cannot smuggle a path separator,
 * a parent-directory escape, or a control character — defense in depth so a name can
 * never become or influence a canonical storage path (T-05-07).
 */
fun isSafeProfileName(name: String): Boolean {
    val trimmed = name.trim()
    return trimmed.isNotBlank() &&
        !trimmed.contains('/') &&
        !trimmed.contains('\\') &&
        !trimmed.contains("..") &&
        trimmed.none { it.isISOControl() }
}
