package com.huanfuli.lapsight.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Bottom-navigation icons for the three-tab shell.
 *
 * The approved `material-icons-core` (pinned 1.7.3 per the Plan 03-02 gate) ships
 * `Icons.Filled.Settings` but omits `Speed` and `History`, so Drive and Review
 * use locally-built [ImageVector]s with simple, recognizable geometry. The
 * screen-rotation action uses `material-icons-extended` so it reads as rotation
 * instead of redo/refresh.
 */
val DriveTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "lapsight-drive",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // A "go/drive" arrowhead — reads as the primary operating surface.
        path(fill = SolidColor(Color.White)) {
            moveTo(8f, 5f)
            lineTo(8f, 19f)
            lineTo(18f, 12f)
            close()
        }
    }.build()
}

val ReviewTabIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "lapsight-review",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        // A list of saved sessions — reads as review/history.
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
        }
    }.build()
}

val SettingsTabIcon: ImageVector get() = Icons.Filled.Settings

val PlayActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "lapsight-play",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(8f, 5f)
            lineTo(8f, 19f)
            lineTo(19f, 12f)
            close()
        }
    }.build()
}

val StopActionIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "lapsight-stop",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(fill = SolidColor(Color.White)) {
            moveTo(7f, 7f)
            lineTo(17f, 7f)
            lineTo(17f, 17f)
            lineTo(7f, 17f)
            close()
        }
    }.build()
}

val RotateScreenIcon: ImageVector get() = Icons.Filled.ScreenRotation

/**
 * Live "you are here" heading arrow for the marking / timing course maps. Uses
 * `material-icons-extended`'s navigation glyph — the recognizable map location
 * pointer — so the live indicator matches the platform's own maps vocabulary.
 */
val LocationMarkerIcon: ImageVector get() = Icons.Filled.Navigation

/** Chevron back for detail screens (core icon set omits ArrowBack's style match). */
val BackIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "lapsight-back",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
        ) {
            moveTo(14.5f, 5.5f)
            lineTo(8f, 12f)
            lineTo(14.5f, 18.5f)
        }
    }.build()
}

/** Vertical-dots overflow for detail-screen action menus. */
val MoreActionsIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "lapsight-more",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        listOf(5.5f, 12f, 18.5f).forEach { cy ->
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, cy - 1.6f)
                curveTo(12.9f, cy - 1.6f, 13.6f, cy - 0.9f, 13.6f, cy)
                curveTo(13.6f, cy + 0.9f, 12.9f, cy + 1.6f, 12f, cy + 1.6f)
                curveTo(11.1f, cy + 1.6f, 10.4f, cy + 0.9f, 10.4f, cy)
                curveTo(10.4f, cy - 0.9f, 11.1f, cy - 1.6f, 12f, cy - 1.6f)
                close()
            }
        }
    }.build()
}

val AddActionIcon: ImageVector get() = Icons.Filled.Add
val ArchiveActionIcon: ImageVector get() = Icons.Filled.Archive
val CheckActionIcon: ImageVector get() = Icons.Filled.Check
val CloseActionIcon: ImageVector get() = Icons.Filled.Close
val DeleteActionIcon: ImageVector get() = Icons.Filled.Delete
val DropdownActionIcon: ImageVector get() = Icons.Filled.ArrowDropDown
val DuplicateActionIcon: ImageVector get() = Icons.Filled.ContentCopy
val EditActionIcon: ImageVector get() = Icons.Filled.Edit
val ExportActionIcon: ImageVector get() = Icons.Filled.FileDownload
val PauseActionIcon: ImageVector get() = Icons.Filled.Pause
val ReplayActionIcon: ImageVector get() = Icons.Filled.Replay
val ResumeActionIcon: ImageVector get() = Icons.Filled.PlayArrow
val SaveActionIcon: ImageVector get() = Icons.Filled.Save
val SaveSessionIcon: ImageVector get() = Icons.Filled.Folder
