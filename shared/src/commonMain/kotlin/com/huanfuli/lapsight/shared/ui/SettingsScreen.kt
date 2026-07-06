package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.ThemeMode
import com.huanfuli.lapsight.shared.ui.components.LapCard
import com.huanfuli.lapsight.shared.ui.components.LapSwitchRow
import com.huanfuli.lapsight.shared.ui.components.SafetyNote
import com.huanfuli.lapsight.shared.ui.components.SegmentedControl

/**
 * Display and mounted-phone behavior controls. Safety copy belongs here instead
 * of competing with live telemetry on the Drive surface.
 *
 * Grouped into instrument-panel cards; every selector is a [SegmentedControl]
 * and every toggle row is a full-width ≥48dp target.
 */
@Composable
internal fun SettingsScreen(
    settings: DriveDisplaySettings,
    phoneGpsAvailable: Boolean,
    phoneGpsPermissionGranted: Boolean,
    locationFeedLocked: Boolean,
    onRequestPhoneGps: () -> Unit,
    onSettingsChanged: (DriveDisplaySettings) -> Unit,
) {
    val effectiveLocationFeedMode =
        if (phoneGpsAvailable) settings.locationFeedMode else LocationFeedMode.Simulated
    val spacing = LapSightTheme.spacing

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = "Settings",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )

        LapCard(title = "Units") {
            SegmentedControl(
                options = listOf("km/h", "mph"),
                selectedIndex = when (settings.speedUnit) {
                    SpeedUnit.KilometersPerHour -> 0
                    SpeedUnit.MilesPerHour -> 1
                },
                onSelect = { index ->
                    onSettingsChanged(
                        settings.copy(
                            speedUnit = if (index == 0) {
                                SpeedUnit.KilometersPerHour
                            } else {
                                SpeedUnit.MilesPerHour
                            },
                        ),
                    )
                },
            )
        }

        LapCard(title = "Location source") {
            SegmentedControl(
                options = listOf("Phone GPS", "Simulated"),
                selectedIndex = when (effectiveLocationFeedMode) {
                    LocationFeedMode.PhoneGps -> 0
                    LocationFeedMode.Simulated -> 1
                },
                onSelect = { index ->
                    if (index == 0) {
                        if (phoneGpsPermissionGranted) {
                            onSettingsChanged(settings.copy(locationFeedMode = LocationFeedMode.PhoneGps))
                        } else {
                            onRequestPhoneGps()
                        }
                    } else {
                        onSettingsChanged(settings.copy(locationFeedMode = LocationFeedMode.Simulated))
                    }
                },
                optionEnabled = { index ->
                    when (index) {
                        0 -> phoneGpsAvailable && !locationFeedLocked
                        else -> !locationFeedLocked
                    }
                },
            )
            val sourceNote = when {
                locationFeedLocked -> "Location source is locked while timing is active."
                !phoneGpsAvailable -> "Phone GPS is not wired on this platform yet."
                settings.locationFeedMode == LocationFeedMode.PhoneGps && !phoneGpsPermissionGranted ->
                    "Allow location permission before using Phone GPS."
                else -> null
            }
            sourceNote?.let {
                Text(
                    text = it,
                    color = LapSightTheme.colors.statusCaution,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (phoneGpsAvailable) {
                LapSwitchRow(
                    label = "High-rate GNSS",
                    supporting = "Raw phone GPS for faster fixes and satellite/L5 quality. " +
                        "Applies on the next feed start.",
                    checked = settings.useDirectGnss,
                    enabled = !locationFeedLocked,
                    onCheckedChange = { onSettingsChanged(settings.copy(useDirectGnss = it)) },
                )
            }
        }

        LapCard(title = "Theme") {
            SegmentedControl(
                options = listOf("System", "Dark", "Light"),
                selectedIndex = when (settings.themeMode) {
                    ThemeMode.System -> 0
                    ThemeMode.Dark -> 1
                    ThemeMode.Light -> 2
                },
                onSelect = { index ->
                    onSettingsChanged(
                        settings.copy(
                            themeMode = when (index) {
                                0 -> ThemeMode.System
                                1 -> ThemeMode.Dark
                                else -> ThemeMode.Light
                            },
                        ),
                    )
                },
            )
        }

        LapCard(title = "While timing") {
            LapSwitchRow(
                label = "Fullscreen while timing",
                checked = settings.fullscreenWhileTiming,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(fullscreenWhileTiming = it))
                },
            )
            LapSwitchRow(
                label = "Keep screen awake while timing",
                checked = settings.keepScreenAwakeWhileTiming,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(keepScreenAwakeWhileTiming = it))
                },
            )
        }

        LapCard(title = "Dash readouts") {
            LapSwitchRow(
                label = "Speed trace",
                checked = settings.showSpeedTrace,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(showSpeedTrace = it))
                },
            )
            LapSwitchRow(
                label = "GPS diagnostics",
                checked = settings.showGpsDiagnostics,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(showGpsDiagnostics = it))
                },
            )
        }

        SafetyNote(
            text = "Closed-course/private-track use only. Configure the display while stationary.",
        )
        Spacer(Modifier.height(spacing.md))
    }
}
