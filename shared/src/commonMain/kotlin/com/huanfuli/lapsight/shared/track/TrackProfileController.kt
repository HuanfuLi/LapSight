package com.huanfuli.lapsight.shared.track

import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore

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
