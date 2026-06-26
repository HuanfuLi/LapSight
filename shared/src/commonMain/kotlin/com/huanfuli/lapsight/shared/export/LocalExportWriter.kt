package com.huanfuli.lapsight.shared.export

/**
 * Stages export artifacts under an app-private `exports/` directory (D-41).
 *
 * Returns filename/path metadata; never exposes canonical payload paths.
 *
 * STUB for RED phase. Full implementation in Task 2.
 */
class LocalExportWriter(
    private val exportRoot: String,
) {
    fun write(artifact: ExportArtifact): String {
        // STUB: returns a placeholder path.
        throw NotImplementedError("LocalExportWriter not implemented (RED phase)")
    }
}
