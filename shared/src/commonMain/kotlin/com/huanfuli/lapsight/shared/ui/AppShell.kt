package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huanfuli.lapsight.shared.DashOrientation
import com.huanfuli.lapsight.shared.OrientationController
import com.huanfuli.lapsight.shared.storage.InMemorySessionStore
import com.huanfuli.lapsight.shared.storage.LocalSessionStore

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
    sessionStore: LocalSessionStore = InMemorySessionStore(),
) {
    var tab by remember { mutableStateOf(AppTab.Drive) }
    var orientation by remember { mutableStateOf(DashOrientation.Portrait) }
    var savedVersion by remember { mutableStateOf(0L) }

    // Fullscreen mounted-dash: hide bottom nav while on Drive in landscape.
    val showBottomNav = !(tab == AppTab.Drive && orientation == DashOrientation.Landscape)

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
                    sessionStore = sessionStore,
                )
                AppTab.Review -> ReviewScreen(
                    sessionStore = sessionStore,
                    savedVersion = savedVersion,
                )
                AppTab.Settings -> SettingsScreen()
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
 * Minimal Settings tab: app identity + the closed-course safety note (SAFE-03).
 * Detailed preferences are deferred to a later phase.
 */
@Composable
private fun SettingsScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "LapSight",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Phone-first lap timing and review.",
            color = Color(0xFFCED7E2),
            fontSize = 16.sp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Closed-course use only. Phone GPS accuracy varies — this is not pro-grade timing.",
            color = Color(0xFFFFD166),
            fontSize = 16.sp,
            lineHeight = 22.sp,
        )
    }
}
