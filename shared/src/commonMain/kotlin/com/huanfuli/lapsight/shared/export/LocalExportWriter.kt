package com.huanfuli.lapsight.shared.export

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Stages export artifacts under an app-private `exports/` directory (D-41, T-03-22).
 *
 * Accepts a generated [ExportArtifact] (bytes, filename, mimeType) and writes it
 * under the injected [exportsRoot]. Never exposes canonical payload paths — the
 * caller gets back only the staged export path, not the original saved payload file.
 *
 * Usage:
 * ```
 * val writer = LocalExportWriter(FileSystem.SYSTEM, StoragePaths.appPrivateRoot())
 * val path = writer.write(artifact)
 * ```
 */
class LocalExportWriter(
    private val fileSystem: FileSystem,
    private val exportsRoot: Path,
) {
    private val exportsDir: Path get() = exportsRoot / EXPORTS_DIR

    /**
     * Writes [artifact.bytes] to `exports/<artifact.fileName>` atomically and returns
     * the absolute path of the staged file.
     *
     * The export directory is created on first write. No caller-supplied paths are
     * accepted — the writer owns filename placement internally (T-03-20).
     */
    fun write(artifact: ExportArtifact): String {
        fileSystem.createDirectories(exportsDir)
        val target = exportsDir / artifact.fileName
        val tmp = exportsDir / "${artifact.fileName}$TMP_SUFFIX"
        fileSystem.write(tmp) { write(artifact.bytes) }
        fileSystem.atomicMove(tmp, target)
        return target.toString()
    }

    companion object {
        private const val EXPORTS_DIR = "exports"
        private const val TMP_SUFFIX = ".tmp"
    }
}
