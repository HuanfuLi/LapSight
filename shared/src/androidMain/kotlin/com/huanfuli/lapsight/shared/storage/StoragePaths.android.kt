package com.huanfuli.lapsight.shared.storage

import android.content.Context
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * Android app-private root backed by `Context.filesDir` (D-21).
 *
 * The platform supplies the capability (an application [Context]); shared code
 * owns all persistence logic. Call [initialize] once from the Android entrypoint
 * before any storage access.
 */
actual object StoragePaths {

    private var appContext: Context? = null

    /** Wires the application context. Safe to call multiple times. */
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    actual fun appPrivateRoot(): Path {
        val context = appContext
            ?: error("StoragePaths.initialize(context) must be called before appPrivateRoot()")
        return context.filesDir.absolutePath.toPath() / "lapsight"
    }

    actual fun fileSystem(): FileSystem = FileSystem.SYSTEM
}
