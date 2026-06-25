package com.huanfuli.lapsight.shared.storage

import okio.FileSystem
import okio.Path

/**
 * Platform boundary for the app-private storage root (D-21).
 *
 * Shared code owns persistence logic; each platform supplies only the app-private
 * root directory (Android `filesDir`, iOS `NSDocumentDirectory` sandbox) and the
 * platform [FileSystem]. The store accepts these via injection so tests can supply
 * a fake/temp root and a fake file system without touching real platform paths.
 */
expect object StoragePaths {
    /** Returns the app-private root directory for LapSight data. */
    fun appPrivateRoot(): Path

    /** Returns the platform [FileSystem] used for app-private I/O. */
    fun fileSystem(): FileSystem
}
