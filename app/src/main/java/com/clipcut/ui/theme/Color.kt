package com.clipcut.ui.theme

import androidx.compose.ui.graphics.Color

// ── Obsidian Precision Motion Design Tokens (Stitch UI) ──
val ObsidianBackground = Color(0xFF101319)
val ObsidianSurfaceDim = Color(0xFF101319)
val ObsidianSurfaceLowest = Color(0xFF0B0E14)
val ObsidianSurfaceLow = Color(0xFF191C22)
val ObsidianSurface = Color(0xFF1D2026)
val ObsidianSurfaceHigh = Color(0xFF272A31)
val ObsidianSurfaceHighest = Color(0xFF32353C)
val ObsidianBorder = Color(0xFF30363D)
val ObsidianBorderVariant = Color(0xFF3B494C)

val ElectricCyan = Color(0xFF00E5FF)
val ElectricCyanDim = Color(0xFF00DAF3)
val ProGold = Color(0xFFFFB703)
val ProGoldDim = Color(0xFFFFBA27)

// ── md_theme_dark_* — required by API reference ──
val md_theme_dark_primary = ElectricCyan
val md_theme_dark_onPrimary = Color(0xFF00363D)
val md_theme_dark_primaryContainer = Color(0xFF00626E)
val md_theme_dark_onPrimaryContainer = Color(0xFFC3F5FF)
val md_theme_dark_secondary = ProGold
val md_theme_dark_tertiary = Color(0xFF9CF0FF)
val md_theme_dark_background = ObsidianBackground
val md_theme_dark_onBackground = Color(0xFFE1E2EB)
val md_theme_dark_surface = ObsidianSurface
val md_theme_dark_onSurface = Color(0xFFE1E2EB)
val md_theme_dark_surfaceVariant = ObsidianSurfaceHigh
val md_theme_dark_onSurfaceVariant = Color(0xFFBAC9CC)
val md_theme_dark_outline = ObsidianBorder

// ── Accent palette ──
val AccentGold = ProGold
val AccentViolet = Color(0xFF9B5DE5)
val AccentBlue = Color(0xFF00DAF3)

// ── Surface tokens (design-system) ──
val Surface1 = ObsidianSurfaceLow
val Surface2 = ObsidianSurface
val Surface3 = ObsidianSurfaceHigh
val HairlineBorder = ObsidianBorder

// ── Aliases required by existing screens (API reference) ──
val DarkBackground: Color = md_theme_dark_background
val DarkSurface: Color = md_theme_dark_surface
val DarkSurfaceElevated: Color = Surface2
val DarkSurfaceBorder: Color = HairlineBorder
val AccentPrimary: Color = md_theme_dark_primary
val AccentPrimaryVariant: Color = md_theme_dark_primaryContainer
val AccentSecondary: Color = md_theme_dark_secondary

val TextPrimary: Color = Color(0xFFE1E2EB)
val TextSecondary: Color = Color(0xFFBAC9CC)
val TextTertiary: Color = Color(0xFF849396)
val TextMuted: Color = Color(0xFF5A6668)

val TimelineClipBg: Color = ObsidianSurfaceHigh
val TimelineClipBorder: Color = ObsidianBorder
val TimelinePlayhead: Color = ElectricCyan
val AudioTrackColor: Color = ElectricCyanDim
val OverlayTrackColor: Color = Color(0xFFB388FF)
