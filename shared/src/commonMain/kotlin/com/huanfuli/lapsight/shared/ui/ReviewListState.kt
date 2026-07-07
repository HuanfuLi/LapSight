package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.storage.LoadResult
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.track.ReviewEntryType
import com.huanfuli.lapsight.shared.track.ReviewIndex
import com.huanfuli.lapsight.shared.track.ReviewIndexRow

/**
 * Plain-Kotlin view model for a single Review list row (D-22, D-27, D-28).
 *
 * Derived from a [ReviewIndexRow] so the Review list screen renders Demo
 * provenance and source metadata visibly (D-42, T-03-10) without re-walking
 * raw payloads. Pure shared Kotlin — testable without Compose.
 */
data class ReviewRowViewModel(
    val id: String,
    val type: ReviewEntryType,
    val name: String,
    val createdAtEpochMillis: Long,
    val isDemo: Boolean,
    val sourceLabel: String,
    val sampleCount: Int?,
    val payloadPath: String,
    val bestLapMillis: Long? = null,
    val isArchived: Boolean = false,
) {
    /** Human-readable type label for the row. */
    val typeLabel: String
        get() = when (type) {
            ReviewEntryType.Track -> "Track"
            ReviewEntryType.TrackMarking -> "Raw capture"
            ReviewEntryType.TimingSession -> "Session"
        }
}

/**
 * Derives a list of [ReviewRowViewModel]s from a saved [ReviewIndex].
 *
 * Returns an empty list when nothing is saved yet — the Review screen renders
 * the empty state in that case (03-UI-SPEC).
 */
object ReviewListState {
    fun from(index: ReviewIndex): List<ReviewRowViewModel> =
        index.rows.map { rowToViewModel(it, isArchived = false) }

    fun from(store: LocalSessionStore): List<ReviewRowViewModel> =
        store.readIndex().rows.map { row ->
            rowToViewModel(row, isArchived = isArchivedTrack(row, store))
        }

    private fun isArchivedTrack(row: ReviewIndexRow, store: LocalSessionStore): Boolean {
        if (row.type != ReviewEntryType.Track) return false
        return (store.loadProfile(row.id) as? LoadResult.Loaded)?.value?.isArchived == true
    }

    private fun rowToViewModel(row: ReviewIndexRow, isArchived: Boolean): ReviewRowViewModel {
        val label = row.source.label
            ?: when (row.source.source) {
                LocationSource.Simulated -> "Simulated"
                LocationSource.PhoneGps -> "Phone GPS"
                LocationSource.ExternalGnss -> "External GNSS"
            }
        return ReviewRowViewModel(
            id = row.id,
            type = row.type,
            name = row.name,
            createdAtEpochMillis = row.createdAtEpochMillis,
            isDemo = row.source.isSimulated,
            sourceLabel = label,
            sampleCount = row.sampleCount,
            payloadPath = row.payloadPath,
            bestLapMillis = row.bestLapMillis,
            isArchived = isArchived,
        )
    }
}
