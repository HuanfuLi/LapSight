package com.huanfuli.lapsight.shared.storage

import kotlinx.cinterop.ExperimentalForeignApi
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

/**
 * iOS app-private root backed by the app sandbox `NSDocumentDirectory` (D-21).
 *
 * The platform supplies the sandbox path; shared code owns all persistence logic.
 */
actual object StoragePaths {

    @OptIn(ExperimentalForeignApi::class)
    actual fun appPrivateRoot(): Path {
        val documents = NSFileManager.defaultManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val path = documents?.path ?: error("Unable to resolve iOS documents directory")
        return path.toPath() / "lapsight"
    }

    actual fun fileSystem(): FileSystem = FileSystem.SYSTEM

    actual fun fileSessionStore(): LocalSessionStore =
        FileSessionStore(fileSystem = fileSystem(), root = appPrivateRoot())
}
