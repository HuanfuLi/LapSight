package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.LocationSource
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
        index.rows.map(::rowToViewModel)

    private fun rowToViewModel(row: ReviewIndexRow): ReviewRowViewModel {
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
        )
    }
}
