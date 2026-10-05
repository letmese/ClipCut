package com.clipcut.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * ClipCut theme. The product is dark-first (Base #0A0A0F). We still expose a
 * `light` fall-back so that androidx.compose.material3 defaults (which need
 * both lightScheme & darkScheme) keep working in previews.
 */
@Composable
fun ClipCutTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val darkColorScheme = androidx.compose.material3.darkColorScheme(
        primary = md_theme_dark_primary,
        onPrimary = md_theme_dark_onPrimary,
        primaryContainer = md_theme_dark_primaryContainer,
        onPrimaryContainer = md_theme_dark_onPrimaryContainer,
        secondary = md_theme_dark_secondary,
        tertiary = md_theme_dark_tertiary,
        background = md_theme_dark_background,
        onBackground = md_theme_dark_onBackground,
        surface = md_theme_dark_surface,
        onSurface = md_theme_dark_onSurface,
        surfaceVariant = md_theme_dark_surfaceVariant,
        onSurfaceVariant = md_theme_dark_onSurfaceVariant,
        outline = md_theme_dark_outline
    )
    MaterialTheme(
        colorScheme = darkColorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

object ClipCutColors {
    val Base: Color = md_theme_dark_background
    val Surface1: Color = com.clipcut.ui.theme.Surface1
    val Surface2: Color = com.clipcut.ui.theme.Surface2
    val Surface3: Color = com.clipcut.ui.theme.Surface3
    val Accent: Color = md_theme_dark_primary
    val AccentTeal: Color = md_theme_dark_secondary
    val AccentBlue: Color = md_theme_dark_tertiary
    val AccentGold: Color = com.clipcut.ui.theme.AccentGold
    val AccentViolet: Color = com.clipcut.ui.theme.AccentViolet
}
