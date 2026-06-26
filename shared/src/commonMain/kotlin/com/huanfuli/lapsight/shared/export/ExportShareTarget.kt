package com.huanfuli.lapsight.shared.export

/**
 * Platform boundary for handing an [ExportArtifact] to the OS share/save surface
 * (RESEARCH Open Question #3).
 *
 * Shared code owns the export pipeline up to the artifact; the platform actual
 * owns the system share sheet / save dialog. The bytes and filename cross only
 * through this contract so exports reach a user-accessible destination.
 *
 * STUB for RED phase — full expect/actual wiring in Task 2.
 */
interface ExportShareTarget {
    fun share(artifact: ExportArtifact): ExportShareResult
}
