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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.DisplaySettingsStore
import com.huanfuli.lapsight.shared.DriveDisplayController
import com.huanfuli.lapsight.shared.DriveDisplaySettings
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.SpeedUnit
import com.huanfuli.lapsight.shared.export.ExportShareTarget
import com.huanfuli.lapsight.shared.export.NoOpExportShareTarget
import com.huanfuli.lapsight.shared.session.DraftRecoveryAction
import com.huanfuli.lapsight.shared.session.DraftRecoveryPrompt
import com.huanfuli.lapsight.shared.session.SessionController
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
    displaySettingsStore: DisplaySettingsStore,
    sessionStore: LocalSessionStore = InMemorySessionStore(),
    exportShareTarget: ExportShareTarget = NoOpExportShareTarget,
) {
    var tab by remember { mutableStateOf(AppTab.Drive) }
    var orientation by remember { mutableStateOf(DashOrientation.Portrait) }
    var savedVersion by remember { mutableStateOf(0L) }
    val sessionController = remember { SessionController(store = sessionStore) }
    var recoveryPrompt by remember { mutableStateOf<DraftRecoveryPrompt?>(null) }
    var confirmDiscardDraft by remember { mutableStateOf(false) }
    var driveTimingActive by remember { mutableStateOf(false) }
    var displaySettings by remember { mutableStateOf(displaySettingsStore.load()) }
    var recoveryBusy by remember { mutableStateOf(false) }
    val uiScope = rememberCoroutineScope()

    // On launch, surface an unfinished draft recovery prompt (D-15).
    LaunchedEffect(Unit) {
        recoveryPrompt = sessionController.loadUnfinishedDraft()
    }

    val driveFullscreen = tab == AppTab.Drive && (
        (orientation == DashOrientation.Landscape && displaySettings.landscapeFullscreen) ||
            (driveTimingActive && displaySettings.fullscreenWhileTiming)
        )
    val showBottomNav = !driveFullscreen

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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
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
                        OutlinedButton(
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
                        Button(
                            onClick = { confirmDiscardDraft = true },
                            enabled = !recoveryBusy,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6B6B)),
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
                    sessionStore = sessionStore,
                    sessionController = sessionController,
                )
                AppTab.Review -> ReviewScreen(
                    sessionStore = sessionStore,
                    savedVersion = savedVersion,
                    exportShareTarget = exportShareTarget,
                )
                AppTab.Settings -> SettingsScreen(
                    settings = displaySettings,
                    onSettingsChanged = {
                        displaySettings = it
                        displaySettingsStore.save(it)
                    },
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
    unselectedIconColor = Color(0xFF9AA8B8),
    unselectedTextColor = Color(0xFF9AA8B8),
)

/**
 * Display and mounted-phone behavior controls. Safety copy belongs here instead
 * of competing with live telemetry on the Drive surface.
 */
@Composable
private fun SettingsScreen(
    settings: DriveDisplaySettings,
    onSettingsChanged: (DriveDisplaySettings) -> Unit,
) {
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
            color = Color(0xFF7E8DA0),
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
        HorizontalDivider(color = Color(0xFF263140))
        SettingToggleRow(
            label = "Fullscreen while timing",
            checked = settings.fullscreenWhileTiming,
            onCheckedChange = {
                onSettingsChanged(settings.copy(fullscreenWhileTiming = it))
            },
        )
        SettingToggleRow(
            label = "Landscape fullscreen",
            checked = settings.landscapeFullscreen,
            onCheckedChange = {
                onSettingsChanged(settings.copy(landscapeFullscreen = it))
            },
        )
        SettingToggleRow(
            label = "Keep screen awake while timing",
            checked = settings.keepScreenAwakeWhileTiming,
            onCheckedChange = {
                onSettingsChanged(settings.copy(keepScreenAwakeWhileTiming = it))
            },
        )
        HorizontalDivider(color = Color(0xFF263140))
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
