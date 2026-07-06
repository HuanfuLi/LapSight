package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LanguageMode
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
    val s = strings

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(spacing.md),
        verticalArrangement = Arrangement.spacedBy(spacing.md),
    ) {
        Text(
            text = s.settings,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.headlineMedium,
        )

        LapCard(title = s.units) {
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

        LapCard(title = s.locationSource) {
            SegmentedControl(
                options = listOf(s.phoneGps, s.simulated),
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
                locationFeedLocked -> s.locationLockedWhileTiming
                !phoneGpsAvailable -> s.phoneGpsUnavailable
                settings.locationFeedMode == LocationFeedMode.PhoneGps && !phoneGpsPermissionGranted ->
                    s.phoneGpsPermissionRequired
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
                    label = s.highRateGnss,
                    supporting = s.highRateGnssSupporting,
                    checked = settings.useDirectGnss,
                    enabled = !locationFeedLocked,
                    onCheckedChange = { onSettingsChanged(settings.copy(useDirectGnss = it)) },
                )
            }
        }

        LapCard(title = s.theme) {
            SegmentedControl(
                options = listOf(s.themeSystem, s.themeDark, s.themeLight),
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

        LapCard(title = s.languageTitle) {
            LanguageSelector(
                selected = settings.languageMode,
                onSelect = { mode -> onSettingsChanged(settings.copy(languageMode = mode)) },
            )
        }

        LapCard(title = s.whileTiming) {
            LapSwitchRow(
                label = s.fullscreenWhileTiming,
                checked = settings.fullscreenWhileTiming,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(fullscreenWhileTiming = it))
                },
            )
            LapSwitchRow(
                label = s.keepScreenAwakeWhileTiming,
                checked = settings.keepScreenAwakeWhileTiming,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(keepScreenAwakeWhileTiming = it))
                },
            )
        }

        LapCard(title = s.dashReadouts) {
            LapSwitchRow(
                label = s.speedTrace,
                checked = settings.showSpeedTrace,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(showSpeedTrace = it))
                },
            )
            LapSwitchRow(
                label = s.gpsDiagnostics,
                checked = settings.showGpsDiagnostics,
                onCheckedChange = {
                    onSettingsChanged(settings.copy(showGpsDiagnostics = it))
                },
            )
        }

        SafetyNote(
            text = s.safetySettings,
        )
        Spacer(Modifier.height(spacing.md))
    }
}

@Composable
private fun LanguageSelector(
    selected: LanguageMode,
    onSelect: (LanguageMode) -> Unit,
) {
    val spacing = LapSightTheme.spacing
    val s = strings
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, LapSightTheme.colors.cardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(horizontal = spacing.md, vertical = spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                Text(
                    text = s.languageModeLabel(selected),
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Icon(
                    imageVector = DropdownActionIcon,
                    contentDescription = s.languageTitle,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(),
        ) {
            LanguageMode.values().forEach { mode ->
                val isSelected = mode == selected
                DropdownMenuItem(
                    text = {
                        Text(
                            text = s.languageModeLabel(mode),
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = CheckActionIcon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelect(mode)
                    },
                )
            }
        }
    }
}
