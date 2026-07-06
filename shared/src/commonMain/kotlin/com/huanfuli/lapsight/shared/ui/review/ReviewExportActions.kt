// Directory: ui/review — package stays `shared.ui` (see ReviewScreen.kt note).
package com.huanfuli.lapsight.shared.ui

import com.huanfuli.lapsight.shared.export.ExportArtifact
import com.huanfuli.lapsight.shared.export.ExportFailedException
import com.huanfuli.lapsight.shared.export.ExportFileNames
import com.huanfuli.lapsight.shared.export.ExportNotFoundException
import com.huanfuli.lapsight.shared.export.ExportShareResult
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.GpxExportService
import com.huanfuli.lapsight.shared.export.JsonExportService
import com.huanfuli.lapsight.shared.storage.LocalSessionStore

internal fun exportTimingSessionJson(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
): String? = shareReviewExport(exportShareTarget) {
    val bytes = JsonExportService(sessionStore).exportTimingSession(row.id)
    val fileName = ExportFileNames.forTimingSession(row.name, row.createdAtEpochMillis, "json")
    ExportArtifact(fileName, "application/json", bytes)
}

internal fun exportTimingSessionGpx(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
): String? = shareReviewExport(exportShareTarget) {
    val bytes = GpxExportService(sessionStore).exportTimingSession(row.id)
    val fileName = ExportFileNames.forTimingSession(row.name, row.createdAtEpochMillis, "gpx")
    ExportArtifact(fileName, "application/gpx+xml", bytes)
}

internal fun exportTrackJson(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
): String? = shareReviewExport(exportShareTarget) {
    val bytes = JsonExportService(sessionStore).exportTrack(row.id)
    val fileName = ExportFileNames.forTrack(row.name, row.createdAtEpochMillis)
    ExportArtifact(fileName, "application/json", bytes)
}

internal fun exportTrackMarkingJson(
    row: ReviewRowViewModel,
    sessionStore: LocalSessionStore,
    exportShareTarget: ExportShareTarget,
): String? = shareReviewExport(exportShareTarget) {
    val bytes = JsonExportService(sessionStore).exportTrackMarking(row.id)
    val fileName = ExportFileNames.forTrackMarking(row.name, row.createdAtEpochMillis)
    ExportArtifact(fileName, "application/json", bytes)
}

private fun shareReviewExport(
    exportShareTarget: ExportShareTarget,
    artifact: () -> ExportArtifact,
): String? = try {
    val exportArtifact = artifact()
    when (exportShareTarget.share(exportArtifact)) {
        is ExportShareResult.Shared, is ExportShareResult.Saved -> "Exported ${exportArtifact.fileName}"
        is ExportShareResult.Cancelled -> null
        is ExportShareResult.Failed -> exportFailureMessage()
    }
} catch (e: ExportNotFoundException) {
    exportFailureMessage()
} catch (e: ExportFailedException) {
    exportFailureMessage()
}

private fun exportFailureMessage(): String = "Export failed. Check device storage and try again."
