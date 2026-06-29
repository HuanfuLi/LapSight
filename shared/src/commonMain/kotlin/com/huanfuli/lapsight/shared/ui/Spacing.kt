package com.huanfuli.lapsight.shared.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * LapSight spacing scale (Phase 5.1 UI-SPEC "Spacing Scale", D-36 Visual Polish).
 *
 * All structural padding and gaps on the Drive dash and shell flow through this
 * 4.dp-multiple scale instead of inline `.dp` literals, so spacing is a single
 * design-system source of truth rather than ad-hoc values bypassing theming
 * (the Pillar 6 / D-37 blocker the UI review flagged).
 *
 * Token values mirror the UI-SPEC table exactly:
 * `xs 4 / sm 8 / md 16 / lg 24 / xl 32 / xxl 48 / xxxl 64`.
 *
 * This is a spacing scale only. Component dimensions that are not "spacing"
 * (button heights, icon sizes, corner radii, stroke widths) are deliberately
 * left as component-local literals and are out of scope for this scale.
 */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 24.dp,
    val xl: Dp = 32.dp,
    val xxl: Dp = 48.dp,
    val xxxl: Dp = 64.dp,
)

/**
 * Ambient [Spacing] scale. Provided once at the app root (see `App.kt`) so every
 * Drive/shell composable reads spacing tokens via `LocalSpacing.current`.
 */
val LocalSpacing = staticCompositionLocalOf { Spacing() }
