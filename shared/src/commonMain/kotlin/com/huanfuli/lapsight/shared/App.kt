package com.huanfuli.lapsight.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.fixtures.GpsFixtureLibrary
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import com.huanfuli.lapsight.shared.ui.AppShell
import com.huanfuli.lapsight.shared.ui.LapSightTheme
import com.huanfuli.lapsight.shared.ui.ProvideLocalizedStrings

/**
 * LapSight root composable (Plan 03-05 refactor).
 *
 * Shrinks to the dark racing theme (D-26) plus an [AppShell] handoff. The shell
 * owns the three-tab navigation and the app-wide window lock; the Drive tab
 * owns the marking capture and the orientation toggle; Review reads the
 * local-first store. The lap engine and storage consume the same
 * [LocationSampleProvider] interface regardless of whether the selected feed is
 * phone GPS or a deterministic simulator.
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
    phoneGpsProvider: LocationSampleProvider? = null,
    phoneGpsPermission: PhoneGpsPermissionState = PhoneGpsPermissionState(),
    sessionStore: LocalSessionStore = InMemorySessionStore(),
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
) {
    var displaySettings by remember { mutableStateOf(displaySettingsStore.load()) }
    val simulatedGpsProvider = remember {
        SimulatedGpsProvider(scenarioId = GpsFixtureLibrary.VARIABLE_PACE_GHOST_UAT)
    }
    val useDarkTheme = when (displaySettings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Dark -> true
        ThemeMode.Light -> false
    }

    ProvideLocalizedStrings(languageMode = displaySettings.languageMode) {
        LapSightTheme(useDarkTheme = useDarkTheme) {
            AppShell(
                orientationController = orientationController,
                driveDisplayController = driveDisplayController,
                displaySettings = displaySettings,
                onDisplaySettingsChanged = { updated ->
                    displaySettings = updated
                    displaySettingsStore.save(updated)
                },
                simulatedGpsProvider = simulatedGpsProvider,
                phoneGpsProvider = phoneGpsProvider,
                phoneGpsPermission = phoneGpsPermission,
                sessionStore = sessionStore,
                exportShareTarget = exportShareTarget,
            )
        }
    }
}
