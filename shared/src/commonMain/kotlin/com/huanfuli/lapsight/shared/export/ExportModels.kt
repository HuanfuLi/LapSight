package com.huanfuli.lapsight.shared.export

/**
 * A generated export artifact carrying the bytes, filename, and MIME type
 * ready for handoff to a platform share/save surface.
 */
data class ExportArtifact(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExportArtifact) return false
        return fileName == other.fileName &&
            mimeType == other.mimeType &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }

    override fun toString(): String = "ExportArtifact(fileName=$fileName, mimeType=$mimeType, bytes.size=${bytes.size})"
}

/** Outcome of an export generation call. */
sealed interface ExportResult {
    data class Success(val artifact: ExportArtifact) : ExportResult
    data object NotFound : ExportResult
    data class Failed(val reason: String) : ExportResult
}

/** Outcome of handing an artifact to the platform share/save surface. */
sealed interface ExportShareResult {
    data object Shared : ExportShareResult
    data object Saved : ExportShareResult
    data object Cancelled : ExportShareResult
    data class Failed(val reason: String) : ExportShareResult
}
