package com.huanfuli.lapsight.shared.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Android [ExportShareTarget] that stages the [ExportArtifact] bytes to a
 * cache file and launches [Intent.ACTION_SEND] via a [FileProvider] content URI.
 *
 * The OS share sheet then delivers the file to external apps, Downloads, or
 * other user-chosen destinations — fulfilling the SESS-04/SESS-05 requirement
 * that exports are reachable by external tools.
 *
 * @param context the Android context (Activity or Application) used for
 *   cache directory access and intent launching.
 * @param authority the FileProvider authority declared in AndroidManifest.xml
 *   (e.g., "com.huanfuli.lapsight.fileprovider").
 */
class AndroidExportShareTarget(
    private val context: Context,
    private val authority: String = "com.huanfuli.lapsight.fileprovider",
) : ExportShareTarget {

    override fun share(artifact: ExportArtifact): ExportShareResult {
        return try {
            val exportDir = File(context.cacheDir, "export")
            exportDir.mkdirs()
            val file = File(exportDir, artifact.fileName)
            file.writeBytes(artifact.bytes)

            val uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = artifact.mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(intent)
            ExportShareResult.Shared
        } catch (e: Exception) {
            ExportShareResult.Failed(e.message ?: "Share failed")
        }
    }
}
