package com.huanfuli.lapsight.shared.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import lapsight.shared.generated.resources.Res
import lapsight.shared.generated.resources.ibmplexmono_medium
import lapsight.shared.generated.resources.ibmplexmono_regular
import lapsight.shared.generated.resources.ibmplexmono_semibold
import lapsight.shared.generated.resources.inter_bold
import lapsight.shared.generated.resources.inter_medium
import lapsight.shared.generated.resources.inter_regular
import lapsight.shared.generated.resources.inter_semibold
import org.jetbrains.compose.resources.Font

/**
 * LapSight design tokens — "racing instrument" identity.
 *
 * All color, type, shape, and spacing decisions flow through this file plus
 * [Spacing]. UI code must not use inline hex / `.sp` literals (the Pillar 6
 * blocker from the 5.1 UI review). The dark racing theme stays the default
 * (D-26); light remains a fully functional secondary theme.
 *
 * Two token layers:
 * - [MaterialTheme] color scheme / typography / shapes drive every stock M3
 *   component (buttons, dialogs, nav bar).
 * - [LapSightColors] (via [LapSightTheme.colors]) carries instrument semantics
 *   that Material roles cannot express unambiguously: timing deltas, readiness
 *   states, and the shared canvas palette for traces/charts. Racing
 *   conventions apply: green = faster/ready, red = slower/error, amber =
 *   caution only, purple = best lap/sector.
 */

val lapsightDarkColors = darkColorScheme(
    background = Color(0xFF05070A),
    surface = Color(0xFF101722),
    surfaceVariant = Color(0xFF1E293B),
    primary = Color(0xFF62E3FF),
    onPrimary = Color(0xFF00232E),
    secondary = Color(0xFFFFD166),
    onSecondary = Color(0xFF2A1E00),
    tertiary = Color(0xFF8CFF9B),
    onTertiary = Color(0xFF00210C),
    error = Color(0xFFFF6B6B),
    onError = Color(0xFF2A0A0A),
    errorContainer = Color(0xFFB8343A),
    onErrorContainer = Color(0xFFFFFFFF),
    onBackground = Color(0xFFEAF2FA),
    onSurface = Color(0xFFEAF2FA),
    onSurfaceVariant = Color(0xFF9AA8B8),
    outlineVariant = Color(0xFF24303D),
)

val lapsightLightColors = lightColorScheme(
    background = Color(0xFFF5F8FB),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    primary = Color(0xFF007B94),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF9A6400),
    onSecondary = Color(0xFFFFFFFF),
    tertiary = Color(0xFF1F8F4D),
    onTertiary = Color(0xFFFFFFFF),
    error = Color(0xFFB8343A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFB8343A),
    onErrorContainer = Color(0xFFFFFFFF),
    onBackground = Color(0xFF111820),
    onSurface = Color(0xFF111820),
    onSurfaceVariant = Color(0xFF516173),
    outlineVariant = Color(0xFFCBD5E1),
)

/**
 * Instrument semantics and canvas palette beyond the Material roles.
 *
 * The `trace*` roles are the single palette source for every Canvas renderer
 * (TraceView, track editor, speed charts) — [com.huanfuli.lapsight.shared.review.TraceLayer]
 * carries a role, never a color, so recorded data stays presentation-free.
 */
@Immutable
data class LapSightColors(
    val deltaFaster: Color,
    val deltaSlower: Color,
    val deltaNeutral: Color,
    val statusReady: Color,
    val statusCaution: Color,
    val statusError: Color,
    val recording: Color,
    val demo: Color,
    val traceReference: Color,
    val traceSession: Color,
    val traceMarking: Color,
    val traceOutlier: Color,
    val traceStartFinish: Color,
    val traceSector: Color,
    val traceBestLap: Color,
    val chartGrid: Color,
    val chartAxisLabel: Color,
    val dashBackground: Color,
    val cardBorder: Color,
)

val lapSightDarkExtendedColors = LapSightColors(
    deltaFaster = Color(0xFF8CFF9B),
    deltaSlower = Color(0xFFFF6B6B),
    deltaNeutral = Color(0xFF9AA8B8),
    statusReady = Color(0xFF8CFF9B),
    statusCaution = Color(0xFFFFD166),
    statusError = Color(0xFFFF6B6B),
    recording = Color(0xFFFF6B6B),
    demo = Color(0xFFFFD166),
    traceReference = Color(0xFF62E3FF),
    traceSession = Color(0xFF9AA8B8),
    traceMarking = Color(0xFF5B6B7C),
    traceOutlier = Color(0xFFFFB84D),
    traceStartFinish = Color(0xFF8CFF9B),
    traceSector = Color(0xFFFFD166),
    traceBestLap = Color(0xFFB48CFF),
    chartGrid = Color(0xFF24303D),
    chartAxisLabel = Color(0xFF9AA8B8),
    dashBackground = Color(0xFF05070A),
    cardBorder = Color(0xFF24303D),
)

val lapSightLightExtendedColors = LapSightColors(
    deltaFaster = Color(0xFF1F8F4D),
    deltaSlower = Color(0xFFB8343A),
    deltaNeutral = Color(0xFF516173),
    statusReady = Color(0xFF1F8F4D),
    statusCaution = Color(0xFF9A6400),
    statusError = Color(0xFFB8343A),
    recording = Color(0xFFB8343A),
    demo = Color(0xFF9A6400),
    traceReference = Color(0xFF007B94),
    traceSession = Color(0xFF64748B),
    traceMarking = Color(0xFF94A3B8),
    traceOutlier = Color(0xFFB45309),
    traceStartFinish = Color(0xFF1F8F4D),
    traceSector = Color(0xFF9A6400),
    traceBestLap = Color(0xFF7C3AED),
    chartGrid = Color(0xFFCBD5E1),
    chartAxisLabel = Color(0xFF516173),
    dashBackground = Color(0xFFF5F8FB),
    cardBorder = Color(0xFFCBD5E1),
)

val LocalLapSightColors = staticCompositionLocalOf { lapSightDarkExtendedColors }

/** Mono family for timing readouts; provided by [LapSightTheme] so previews work. */
val LocalMonoFontFamily = staticCompositionLocalOf<FontFamily> { FontFamily.Monospace }

/**
 * Corner radius scale. Deliberately tighter than stock M3 pills — flat-ish
 * corners read "instrument", full pills read "template".
 */
val lapsightShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(18.dp),
)

/** Bundled UI face: Inter (OFL, see THIRD_PARTY_LICENSES.md). */
@Composable
internal fun interFontFamily(): FontFamily = FontFamily(
    Font(Res.font.inter_regular, FontWeight.Normal),
    Font(Res.font.inter_medium, FontWeight.Medium),
    Font(Res.font.inter_semibold, FontWeight.SemiBold),
    Font(Res.font.inter_bold, FontWeight.Bold),
)

/** Bundled timing face: IBM Plex Mono — inherently tabular digits, no jitter. */
@Composable
internal fun plexMonoFontFamily(): FontFamily = FontFamily(
    Font(Res.font.ibmplexmono_regular, FontWeight.Normal),
    Font(Res.font.ibmplexmono_medium, FontWeight.Medium),
    Font(Res.font.ibmplexmono_semibold, FontWeight.SemiBold),
)

/** Tabular figures for Inter styles that render numbers outside the mono face. */
private const val TABULAR_FIGURES = "tnum"

/**
 * Semantic typography mirroring the UI-SPEC "Typography" table:
 * Body 16/400/1.5, Label 14/600/1.2, Heading 24/600/1.2, Display 56/600/1.0.
 *
 * Composable because the bundled fonts load through compose resources. Display
 * roles are sized for the dash readouts; the hero timing readouts additionally
 * switch to the mono family via `TimingText` and use `autoSize` for clip-safe
 * glance sizing (D-31).
 */
@Composable
fun lapsightTypography(): Typography {
    val inter = interFontFamily()
    return Typography(
        // Display (UI-SPEC Display 56/600/1.0) — primary current-lap readout.
        displayLarge = TextStyle(fontFamily = inter, fontSize = 56.sp, fontWeight = FontWeight.SemiBold, lineHeight = 56.sp, fontFeatureSettings = TABULAR_FIGURES),
        displayMedium = TextStyle(fontFamily = inter, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 48.sp, fontFeatureSettings = TABULAR_FIGURES),
        displaySmall = TextStyle(fontFamily = inter, fontSize = 40.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp, fontFeatureSettings = TABULAR_FIGURES),
        // Heading (UI-SPEC Heading 24/600/1.2) and adjacent title steps.
        headlineMedium = TextStyle(fontFamily = inter, fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
        headlineSmall = TextStyle(fontFamily = inter, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp),
        titleLarge = TextStyle(fontFamily = inter, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = inter, fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
        titleSmall = TextStyle(fontFamily = inter, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
        // Body (UI-SPEC Body 16/400/1.5) and smaller body steps.
        bodyLarge = TextStyle(fontFamily = inter, fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = inter, fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = inter, fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
        // Label (UI-SPEC Label 14/600/1.2) and smaller caption labels.
        labelLarge = TextStyle(fontFamily = inter, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp, fontFeatureSettings = TABULAR_FIGURES),
        // Caption/eyebrow roles carry letterspacing — uppercase section labels
        // and status chips read as instrument engraving, not shouting.
        labelMedium = TextStyle(fontFamily = inter, fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp, letterSpacing = 1.sp, fontFeatureSettings = TABULAR_FIGURES),
        labelSmall = TextStyle(fontFamily = inter, fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp, letterSpacing = 0.8.sp, fontFeatureSettings = TABULAR_FIGURES),
    )
}

/**
 * AutoSize bounds for the dash glance readouts (D-31): the display roles
 * anchor the type and autoSize shrinks within these bounds so long values
 * never clip. Part of the type scale, so they live with the other tokens.
 */
object LapSightAutoSize {
    val step = 2.sp
    val heroMin = 24.sp
    val heroMax = 56.sp
    val heroMaxCompact = 44.sp
    val speedMin = 20.sp
    val speedMax = 40.sp
    val speedMaxCompact = 34.sp
    val deltaMin = 22.sp
    val deltaMax = 44.sp
    val deltaMaxCompact = 32.sp
}

/**
 * Ambient accessors for the LapSight token layers. Mirrors the
 * `MaterialTheme` object + composable pairing.
 */
object LapSightTheme {
    val colors: LapSightColors
        @Composable @ReadOnlyComposable get() = LocalLapSightColors.current
    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current
    val monoFamily: FontFamily
        @Composable @ReadOnlyComposable get() = LocalMonoFontFamily.current
}

/**
 * Root theme wrapper: MaterialTheme (colors, bundled-font typography, shapes)
 * plus the LapSight semantic locals. The only place theme composition happens.
 */
@Composable
fun LapSightTheme(
    useDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (useDarkTheme) lapsightDarkColors else lapsightLightColors,
        typography = lapsightTypography(),
        shapes = lapsightShapes,
    ) {
        CompositionLocalProvider(
            LocalLapSightColors provides if (useDarkTheme) lapSightDarkExtendedColors else lapSightLightExtendedColors,
            LocalMonoFontFamily provides plexMonoFontFamily(),
            LocalSpacing provides Spacing(),
            content = content,
        )
    }
}
