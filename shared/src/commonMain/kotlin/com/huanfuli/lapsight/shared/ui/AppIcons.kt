package com.huanfuli.lapsight.shared.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
 * `Icons.Filled.Settings` but omits `Speed` and `History` (those live in the
 * forbidden `material-icons-extended`). Rather than add a dependency, Drive and
 * Review use locally-built [ImageVector]s with simple, recognizable geometry.
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

val RotateScreenIcon: ImageVector get() = Icons.Filled.Refresh
