package com.huanfuli.lapsight.shared.export

/**
 * Platform boundary for handing an [ExportArtifact] to the OS share/save surface
 * (RESEARCH Open Question #3).
 *
 * Shared code owns the export pipeline up to the artifact; the platform actual
 * owns the system share sheet / save dialog. The bytes and filename cross only
 * through this contract so exports reach a user-accessible destination.
 *
 * Each platform provides its own implementation with its required constructor
 * parameters (e.g., Android Context). The shared code receives this via constructor
 * injection — it never constructs the platform target directly.
 */
interface ExportShareTarget {
    fun share(artifact: ExportArtifact): ExportShareResult
}

/**
 * No-op [ExportShareTarget] used as the default in Compose previews and tests
 * where no platform share surface is available.
 */
object NoOpExportShareTarget : ExportShareTarget {
    override fun share(artifact: ExportArtifact): ExportShareResult = ExportShareResult.Cancelled
}
