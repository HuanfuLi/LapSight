package com.huanfuli.lapsight.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.ui.AppShell

/**
 * LapSight root composable (Plan 03-05 refactor).
 *
 * Shrinks to the dark racing theme (D-26) plus an [AppShell] handoff. The shell
 * owns the three-tab navigation and the app-wide window lock; the Drive tab
 * owns the marking capture and the orientation toggle; Review reads the
 * local-first store. The lap engine, timing recorder, trace renderer, export,
 * real GPS provider, map tiles, and glasses bridge are NOT wired here — they
 * belong to other plans.
 *
 * @param orientationController platform window lock; never sensor-driven.
 * @param sessionStore local-first store; defaults to an in-memory store so
 *   previews/tests need no platform storage root. The Android entrypoint
 *   injects a `FileSessionStore` over the real app-private root.
 */
@Composable
@Preview
fun App(
    orientationController: OrientationController = NoOpOrientationController,
    driveDisplayController: DriveDisplayController = NoOpDriveDisplayController,
    displaySettingsStore: DisplaySettingsStore = InMemoryDisplaySettingsStore(),
    sessionStore: LocalSessionStore = InMemorySessionStore(),
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
) {
    var displaySettings by remember { mutableStateOf(displaySettingsStore.load()) }
    val useDarkTheme = when (displaySettings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) lapsightDarkColors else lapsightLightColors,
    ) {
        AppShell(
            orientationController = orientationController,
            driveDisplayController = driveDisplayController,
            displaySettings = displaySettings,
            onDisplaySettingsChanged = { updated ->
                displaySettings = updated
                displaySettingsStore.save(updated)
            },
            sessionStore = sessionStore,
            exportShareTarget = exportShareTarget,
        )
    }
}

private val lapsightDarkColors = darkColorScheme(
    background = Color(0xFF05070A),
    surface = Color(0xFF101722),
    primary = Color(0xFF62E3FF),
    secondary = Color(0xFFFFD166),
    onBackground = Color(0xFFEAF2FA),
    onSurface = Color(0xFFEAF2FA),
    onSurfaceVariant = Color(0xFF9AA8B8),
)

private val lapsightLightColors = lightColorScheme(
    background = Color(0xFFF5F8FB),
    surface = Color(0xFFFFFFFF),
    primary = Color(0xFF007B94),
    secondary = Color(0xFF9A6400),
    onBackground = Color(0xFF111820),
    onSurface = Color(0xFF111820),
    onSurfaceVariant = Color(0xFF516173),
)
