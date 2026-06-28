package com.huanfuli.lapsight.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.huanfuli.lapsight.shared.storage.StoragePaths

/**
 * iOS entry point.
 *
 * Injects the app-private [StoragePaths.fileSessionStore] so created profiles and
 * the current-Track selection survive a cold launch — mirroring Android
 * `MainActivity` (Plan 05-04). Without this, iOS would fall back to the
 * preview/test [com.huanfuli.lapsight.shared.storage.InMemorySessionStore] default
 * and silently lose every saved Track on relaunch (T-05-09). The iOS
 * `StoragePaths` actual resolves the sandbox `NSDocumentDirectory` directly, so no
 * `initialize` step is needed here.
 */
fun MainViewController() = ComposeUIViewController {
    App(
        displaySettingsStore = IosDisplaySettingsStore(),
        sessionStore = StoragePaths.fileSessionStore(),
    )
}
