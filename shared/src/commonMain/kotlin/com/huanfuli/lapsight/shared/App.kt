package com.huanfuli.lapsight.shared

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
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
    sessionStore: LocalSessionStore = InMemorySessionStore(),
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = Color(0xFF05070A),
            surface = Color(0xFF101722),
            primary = Color(0xFF62E3FF),
            secondary = Color(0xFFFFD166),
        ),
    ) {
        AppShell(
            orientationController = orientationController,
            sessionStore = sessionStore,
        )
    }
}
