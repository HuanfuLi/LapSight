package com.huanfuli.lapsight.shared.ui

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * LapSight design tokens (Phase 5.1 UI hardening, D-36 / D-37).
 *
 * Centralises the color scheme and typography the mounted-phone dash renders so
 * that colors and type flow through [androidx.compose.material3.MaterialTheme]
 * semantic roles instead of the inline hex / `.sp` literals the UI review flagged
 * as the Pillar 6 (Visual Polish / Accessibility) blocker. This is a bounded
 * tokenization: the dark racing theme stays the default (D-26) and the dash
 * information hierarchy is unchanged (D-38 / D-39).
 *
 * Semantic color role mapping (the instrument state colors are routed onto
 * standard Material roles so there is one token system, not bespoke per-call hex):
 * - `primary`   accent / hero current-lap readout
 * - `secondary` caution / amber: not-Ready, raw-recording, DEMO, warnings, slower delta
 * - `tertiary`  success / green: Ready, GPS OK, faster delta, "track ready"
 * - `error`     destructive text (Discard) and slower-than-reference is handled by `secondary`
 * - `errorContainer` stop-action button container
 * - `onSurfaceVariant` neutral / gray (neutral delta, secondary labels)
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
 * Semantic typography mirroring the UI-SPEC "Typography" table:
 * Body 16/400/1.5, Label 14/600/1.2, Heading 24/600/1.2, Display 56/600/1.0.
 *
 * The four UI-SPEC roles anchor the named Material roles; the remaining roles
 * fill the dash's existing size steps so every readout maps to a semantic role
 * (preserving the current hierarchy, not redesigning it). The hero timing
 * readouts override weight to [FontWeight.Black] at their call sites and use
 * `autoSize` so glance sizing is clip-safe across viewports (D-31), replacing
 * the brittle `if (compact) 42.sp else 54.sp` pair.
 */
val lapsightTypography: Typography = Typography(
    // Display (UI-SPEC Display 56/600/1.0) — primary current-lap readout.
    displayLarge = TextStyle(fontSize = 56.sp, fontWeight = FontWeight.SemiBold, lineHeight = 56.sp),
    displayMedium = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.SemiBold, lineHeight = 48.sp),
    displaySmall = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, lineHeight = 44.sp),
    // Heading (UI-SPEC Heading 24/600/1.2) and adjacent title steps.
    headlineMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp),
    // Body (UI-SPEC Body 16/400/1.5) and smaller body steps.
    bodyLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Normal, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, lineHeight = 18.sp),
    // Label (UI-SPEC Label 14/600/1.2) and smaller caption labels.
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 17.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, lineHeight = 14.sp),
)
