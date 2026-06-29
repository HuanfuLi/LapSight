package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DriveDisplayController
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.LocationFeedMode
import com.huanfuli.lapsight.shared.LocationSampleProvider
import com.huanfuli.lapsight.shared.LocationSource
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.PhoneGpsPermissionState
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.ThemeMode
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.session.DraftRecoveryAction
import com.huanfuli.lapsight.shared.session.DraftRecoveryPrompt
import com.huanfuli.lapsight.shared.session.SessionController
import com.huanfuli.lapsight.shared.session.SourceMetadata
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LocalSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Which bottom-navigation tab is active.
 */
enum class AppTab { Drive, Review, Settings }

/**
 * The persistent three-tab shell: Drive / Review / Settings bottom navigation
 * (D-26, D-27).
 *
 * The shell owns the app-wide window lock via [orientationController] (so the
 * lock applies regardless of tab) and the [sessionStore] used by Review. The
 * Drive tab owns the orientation toggle itself (D-29). In landscape on the
 * Drive tab the bottom navigation is hidden for a fullscreen mounted-dash mode
 * (D-29); the user returns to portrait to bring the navigation back.
 *
 * @param orientationController platform window lock; never sensor-driven.
 * @param sessionStore local-first store; defaults to an in-memory store so
 *   previews/tests need no platform storage root.
 */
@Composable
fun AppShell(
    orientationController: OrientationController,
    driveDisplayController: DriveDisplayController,
    displaySettings: DriveDisplaySettings,
    onDisplaySettingsChanged: (DriveDisplaySettings) -> Unit,
    simulatedGpsProvider: LocationSampleProvider,
    phoneGpsProvider: LocationSampleProvider? = null,
    phoneGpsPermission: PhoneGpsPermissionState = PhoneGpsPermissionState(),
    sessionStore: LocalSessionStore = InMemorySessionStore(),
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
) {
    var tab by remember { mutableStateOf(AppTab.Drive) }
    var orientation by remember { mutableStateOf(DashOrientation.Portrait) }
    var savedVersion by remember { mutableStateOf(0L) }
    val effectiveLocationFeedMode =
        if (displaySettings.locationFeedMode == LocationFeedMode.PhoneGps && phoneGpsProvider != null) {
            LocationFeedMode.PhoneGps
        } else {
            LocationFeedMode.Simulated
        }
    // The session source must follow the LIVE feed, not the Track's marking source
    // (D-04/D-42). Capture the current feed mode in a State the controller's
    // sourceForTrack lambda reads at startTiming time, so switching feeds before a
    // run is reflected in the saved session provenance.
    val liveFeedModeState = rememberUpdatedState(effectiveLocationFeedMode)
    val sessionController = remember {
        SessionController(
            store = sessionStore,
            sourceForTrack = { _ ->
                when (liveFeedModeState.value) {
                    LocationFeedMode.PhoneGps -> SourceMetadata(
                        source = LocationSource.PhoneGps,
                        isSimulated = false,
                    )
                    LocationFeedMode.Simulated -> SourceMetadata(
                        source = LocationSource.Simulated,
                        isSimulated = true,
                        label = "Demo",
                    )
                }
            },
        )
    }
    var recoveryPrompt by remember { mutableStateOf<DraftRecoveryPrompt?>(null) }
    var confirmDiscardDraft by remember { mutableStateOf(false) }
    var driveTimingActive by remember { mutableStateOf(false) }
    var recoveryBusy by remember { mutableStateOf(false) }
    var pendingPhoneGpsSelection by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    // On launch, surface an unfinished draft recovery prompt (D-15).
    LaunchedEffect(Unit) {
        recoveryPrompt = sessionController.loadUnfinishedDraft()
    }

    val driveFullscreen = tab == AppTab.Drive && (
        orientation == DashOrientation.Landscape ||
            (driveTimingActive && displaySettings.fullscreenWhileTiming)
        )
    val showBottomNav = !driveFullscreen
    val activeLocationProvider =
        if (effectiveLocationFeedMode == LocationFeedMode.PhoneGps) phoneGpsProvider!! else simulatedGpsProvider

    LaunchedEffect(pendingPhoneGpsSelection, phoneGpsPermission.isGranted) {
        if (pendingPhoneGpsSelection && phoneGpsPermission.isGranted) {
            pendingPhoneGpsSelection = false
            onDisplaySettingsChanged(displaySettings.copy(locationFeedMode = LocationFeedMode.PhoneGps))
        }
    }

    LaunchedEffect(driveFullscreen, driveTimingActive, displaySettings.keepScreenAwakeWhileTiming) {
        driveDisplayController.apply(
            fullscreen = driveFullscreen,
            keepScreenAwake =
                driveTimingActive && displaySettings.keepScreenAwakeWhileTiming,
        )
    }

    // Recovery prompt: Resume / Save / Discard; never auto-promotes to history (D-16).
    recoveryPrompt?.let { prompt ->
        if (confirmDiscardDraft) {
            AlertDialog(
                onDismissRequest = { confirmDiscardDraft = false },
                title = { Text("Discard unfinished session?") },
                text = { Text("Discard unfinished session? Recorded data will be lost.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmDiscardDraft = false
                            sessionController.handleRecoveryAction(prompt, DraftRecoveryAction.Discard)
                            recoveryPrompt = null
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B)),
                    ) { Text("Discard") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmDiscardDraft = false }) { Text("Cancel") }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { /* require an explicit choice */ },
                title = { Text("Unfinished session found") },
                text = { Text("You have a session that wasn't saved.") },
                confirmButton = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(
                            onClick = {
                                recoveryBusy = true
                                uiScope.launch {
                                    withContext(Dispatchers.Default) {
                                        sessionController.handleRecoveryAction(
                                            prompt,
                                            DraftRecoveryAction.Resume,
                                        )
                                    }
                                    recoveryPrompt = null
                                    recoveryBusy = false
                                    driveTimingActive = sessionController.timingRunSnapshot().isActive
                                    tab = AppTab.Drive
                                }
                            },
                            enabled = !recoveryBusy,
                        ) { Text("Resume") }
                        if (DraftRecoveryAction.Save in prompt.availableActions) {
                            TextButton(
                                onClick = {
                                    recoveryBusy = true
                                    uiScope.launch {
                                        withContext(Dispatchers.Default) {
                                            sessionController.handleRecoveryAction(
                                                prompt,
                                                DraftRecoveryAction.Save,
                                            )
                                        }
                                        recoveryPrompt = null
                                        recoveryBusy = false
                                        savedVersion++
                                    }
                                },
                                enabled = !recoveryBusy,
                            ) { Text("Save") }
                        }
                        TextButton(
                            onClick = { confirmDiscardDraft = true },
                            enabled = !recoveryBusy,
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF6B6B)),
                        ) { Text("Discard") }
                    }
                },
            )
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomNav) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    NavigationBarItem(
                        selected = tab == AppTab.Drive,
                        onClick = { tab = AppTab.Drive },
                        icon = { Icon(DriveTabIcon, contentDescription = "Drive") },
                        label = { Text("Drive") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.Review,
                        onClick = { tab = AppTab.Review },
                        icon = { Icon(ReviewTabIcon, contentDescription = "Review") },
                        label = { Text("Review") },
                        colors = navItemColors(),
                    )
                    NavigationBarItem(
                        selected = tab == AppTab.Settings,
                        onClick = { tab = AppTab.Settings },
                        icon = { Icon(SettingsTabIcon, contentDescription = "Settings") },
                        label = { Text("Settings") },
                        colors = navItemColors(),
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (tab) {
                AppTab.Drive -> DriveScreen(
                    orientationController = orientationController,
                    orientation = orientation,
                    onToggleOrientation = {
                        orientation = if (orientation == DashOrientation.Portrait) {
                            DashOrientation.Landscape
                        } else {
                            DashOrientation.Portrait
                        }
                    },
                    onSavedTrack = { savedVersion++ },
                    onSavedSession = { savedVersion++ },
                    onTimingActiveChanged = { driveTimingActive = it },
                    requestedTimingActive = driveTimingActive,
                    displaySettings = displaySettings,
                    locationFeedMode = effectiveLocationFeedMode,
                    locationProvider = activeLocationProvider,
                    phoneGpsPermission = phoneGpsPermission,
                    sessionStore = sessionStore,
                    sessionController = sessionController,
                )
                AppTab.Review -> ReviewScreen(
                    sessionStore = sessionStore,
                    savedVersion = savedVersion,
                    displaySettings = displaySettings,
                    exportShareTarget = exportShareTarget,
                )
                AppTab.Settings -> SettingsScreen(
                    settings = displaySettings,
                    phoneGpsAvailable = phoneGpsProvider != null && phoneGpsPermission.isSupported,
                    phoneGpsPermissionGranted = phoneGpsPermission.isGranted,
                    locationFeedLocked = driveTimingActive,
                    onRequestPhoneGps = {
                        pendingPhoneGpsSelection = true
                        phoneGpsPermission.requestPermission()
                    },
                    onSettingsChanged = onDisplaySettingsChanged,
                )
            }
        }
    }
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    indicatorColor = MaterialTheme.colorScheme.surface,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * Display and mounted-phone behavior controls. Safety copy belongs here instead
 * of competing with live telemetry on the Drive surface.
 */
@Composable
private fun SettingsScreen(
    settings: DriveDisplaySettings,
    phoneGpsAvailable: Boolean,
    phoneGpsPermissionGranted: Boolean,
    locationFeedLocked: Boolean,
    onRequestPhoneGps: () -> Unit,
    onSettingsChanged: (DriveDisplaySettings) -> Unit,
) {
    val effectiveLocationFeedMode =
        if (phoneGpsAvailable) settings.locationFeedMode else LocationFeedMode.Simulated

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "Settings",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "SPEED UNIT",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            UnitButton(
                label = "km/h",
                selected = settings.speedUnit == SpeedUnit.KilometersPerHour,
                onClick = {
                    onSettingsChanged(settings.copy(speedUnit = SpeedUnit.KilometersPerHour))
                },
                modifier = Modifier.weight(1f),
            )
            UnitButton(
                label = "mph",
                selected = settings.speedUnit == SpeedUnit.MilesPerHour,
                onClick = {
                    onSettingsChanged(settings.copy(speedUnit = SpeedUnit.MilesPerHour))
                },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "LOCATION SOURCE",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SourceButton(
                label = "Phone GPS",
                selected = effectiveLocationFeedMode == LocationFeedMode.PhoneGps,
                enabled = phoneGpsAvailable && !locationFeedLocked,
                onClick = {
                    if (phoneGpsPermissionGranted) {
                        onSettingsChanged(settings.copy(locationFeedMode = LocationFeedMode.PhoneGps))
                    } else {
                        onRequestPhoneGps()
                    }
                },
                modifier = Modifier.weight(1f),
            )
            SourceButton(
                label = "Simulated",
                selected = effectiveLocationFeedMode == LocationFeedMode.Simulated,
                enabled = !locationFeedLocked,
                onClick = {
                    onSettingsChanged(settings.copy(locationFeedMode = LocationFeedMode.Simulated))
                },
                modifier = Modifier.weight(1f),
            )
        }
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
                color = Color(0xFFFFD166),
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            text = "THEME",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ThemeButton(
                label = "System",
                selected = settings.themeMode == ThemeMode.System,
                onClick = { onSettingsChanged(settings.copy(themeMode = ThemeMode.System)) },
                modifier = Modifier.weight(1f),
            )
            ThemeButton(
                label = "Dark",
                selected = settings.themeMode == ThemeMode.Dark,
                onClick = { onSettingsChanged(settings.copy(themeMode = ThemeMode.Dark)) },
                modifier = Modifier.weight(1f),
            )
            ThemeButton(
                label = "Light",
                selected = settings.themeMode == ThemeMode.Light,
                onClick = { onSettingsChanged(settings.copy(themeMode = ThemeMode.Light)) },
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingToggleRow(
            label = "Fullscreen while timing",
            checked = settings.fullscreenWhileTiming,
            onCheckedChange = {
                onSettingsChanged(settings.copy(fullscreenWhileTiming = it))
            },
        )
        SettingToggleRow(
            label = "Keep screen awake while timing",
            checked = settings.keepScreenAwakeWhileTiming,
            onCheckedChange = {
                onSettingsChanged(settings.copy(keepScreenAwakeWhileTiming = it))
            },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingToggleRow(
            label = "Speed trace",
            checked = settings.showSpeedTrace,
            onCheckedChange = {
                onSettingsChanged(settings.copy(showSpeedTrace = it))
            },
        )
        SettingToggleRow(
            label = "GPS diagnostics",
            checked = settings.showGpsDiagnostics,
            onCheckedChange = {
                onSettingsChanged(settings.copy(showGpsDiagnostics = it))
            },
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Closed-course/private-track use only. Configure the display while stationary.",
            color = Color(0xFFFFD166),
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun SourceButton(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
            Text(label)
        }
    }
}

@Composable
private fun UnitButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}

@Composable
private fun ThemeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label)
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
