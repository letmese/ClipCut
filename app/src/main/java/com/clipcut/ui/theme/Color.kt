package com.clipcut.ui.theme

import androidx.compose.ui.graphics.Color

// ── md_theme_dark_* — required by API reference ──
val md_theme_dark_primary = Color(0xFFE94560)
val md_theme_dark_onPrimary = Color(0xFFFFFFFF)
val md_theme_dark_primaryContainer = Color(0xFF5C1A28)
val md_theme_dark_onPrimaryContainer = Color(0xFFFFD9DD)
val md_theme_dark_secondary = Color(0xFF06D6A0)
val md_theme_dark_tertiary = Color(0xFF4EA8DE)
val md_theme_dark_background = Color(0xFF0A0A0F)
val md_theme_dark_onBackground = Color(0xFFFFFFFF)
val md_theme_dark_surface = Color(0xFF12121A)
val md_theme_dark_onSurface = Color(0xFFFFFFFF)
val md_theme_dark_surfaceVariant = Color(0xFF23232F)
val md_theme_dark_onSurfaceVariant = Color(0xB3FFFFFF)
val md_theme_dark_outline = Color(0x33FFFFFF)

// ── Accent palette ──
val AccentGold = Color(0xFFFFC857)
val AccentViolet = Color(0xFF9B5DE5)
val AccentBlue = md_theme_dark_tertiary

// ── Surface tokens (design-system) ──
val Surface1 = Color(0xFF12121A)
val Surface2 = Color(0xFF1A1A24)
val Surface3 = Color(0xFF23232F)
val HairlineBorder = Color(0x33FFFFFF)

// ── Aliases required by existing screens (API reference) ──
val DarkBackground: Color = md_theme_dark_background
val DarkSurface: Color = md_theme_dark_surface
val DarkSurfaceElevated: Color = Surface2
val DarkSurfaceBorder: Color = HairlineBorder
val AccentPrimary: Color = md_theme_dark_primary
val AccentPrimaryVariant: Color = md_theme_dark_primaryContainer
val AccentSecondary: Color = md_theme_dark_secondary

val TextPrimary: Color = Color(0xFFFFFFFF)
val TextSecondary: Color = Color(0xCCFFFFFF)
val TextTertiary: Color = Color(0x99FFFFFF)
val TextMuted: Color = Color(0x4DFFFFFF)

val TimelineClipBg: Color = Color(0xFF2A2A3A)
val TimelineClipBorder: Color = Color(0x33FFFFFF)
val TimelinePlayhead: Color = md_theme_dark_primary
val AudioTrackColor: Color = md_theme_dark_secondary
val OverlayTrackColor: Color = AccentViolet
