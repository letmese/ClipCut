package com.clipcut.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

// ── Obsidian Precision Motion Design Tokens (Stitch UI) ──
val ObsidianBackground = Color(0xFF13121D)
val ObsidianSurfaceDim = Color(0xFF13121D)
val ObsidianSurfaceLowest = Color(0xFF0E0D18)
val ObsidianSurfaceLow = Color(0xFF1B1A26)
val ObsidianSurface = Color(0xFF201E2A)
val ObsidianSurfaceHigh = Color(0xFF2A2934)
val ObsidianSurfaceHighest = Color(0xFF353340)
val ObsidianSurfaceBright = Color(0xFF393844)
val ObsidianBorder = Color(0x33928EA3)
val ObsidianBorderVariant = Color(0xFF474557)

val ElectricCyan = Color(0xFF00F2FE)
val ElectricCyanDim = Color(0xFF00DCE6)
val VibrantViolet = Color(0xFF7C3AED)
val ElectricViolet = Color(0xFF4407FD)
val MutedIndigo = Color(0xFF7369D1)
val DeepCrimson = Color(0xFF9D2000)
val LuminescentAmber = Color(0xFFFACC15)
val EmeraldWave = Color(0xFF10B981)
val ProGold = LuminescentAmber
val ProGoldDim = Color(0xFFE5A800)
val GlassSurface = Color(0xCC161A23)
val GlassBorder = Color(0x24FFFFFF)
val GlassBorderSubtle = Color(0x14FFFFFF)

// ── md_theme_dark_* — required by API reference ──
val md_theme_dark_primary = ElectricCyan
val md_theme_dark_onPrimary = Color(0xFF00373A)
val md_theme_dark_primaryContainer = Color(0xFF006A70)
val md_theme_dark_onPrimaryContainer = Color(0xFFE0FDFF)
val md_theme_dark_secondary = VibrantViolet
val md_theme_dark_tertiary = LuminescentAmber
val md_theme_dark_background = ObsidianBackground
val md_theme_dark_onBackground = Color(0xFFE5E0F0)
val md_theme_dark_surface = ObsidianSurface
val md_theme_dark_onSurface = Color(0xFFE5E0F0)
val md_theme_dark_surfaceVariant = ObsidianSurfaceHigh
val md_theme_dark_onSurfaceVariant = Color(0xFFC8C4DA)
val md_theme_dark_outline = ObsidianBorder

// ── Accent palette ──
val AccentGold = LuminescentAmber
val AccentViolet = VibrantViolet
val AccentBlue = ElectricCyan
val AccentEmerald = EmeraldWave

/**
 * Dynamic theme and creative accent state holder.
 * State updates are backed by Compose Snapshot State (mutableStateOf),
 * allowing any reading Composable to recompose immediately when the user changes theme or accent.
 */
object ThemeState {
    var isDark by mutableStateOf(true)
    var accentColor by mutableStateOf(LuminescentAmber)
    var accentSecondary by mutableStateOf(VibrantViolet)

    var background by mutableStateOf(Color(0xFF0E0D18))
    var surface by mutableStateOf(Color(0xFF1B1A26))
    var surface1 by mutableStateOf(Color(0xFF13151F))
    var surface2 by mutableStateOf(Color(0xFF201E2A))
    var surface3 by mutableStateOf(Color(0xFF2A2934))
    var border by mutableStateOf(Color(0x33928EA3))

    var textPrimary by mutableStateOf(Color(0xFFE5E0F0))
    var textSecondary by mutableStateOf(Color(0xFFC8C4DA))
    var textTertiary by mutableStateOf(Color(0xFF928EA3))
    var textMuted by mutableStateOf(Color(0xFF6C687E))

    fun update(themeSetting: String, accentHex: String, isSystemInDark: Boolean) {
        val dark = when (themeSetting.lowercase()) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDark
        }
        isDark = dark

        val parsedAccent = runCatching {
            Color(android.graphics.Color.parseColor(accentHex))
        }.getOrDefault(LuminescentAmber)
        accentColor = parsedAccent

        if (dark) {
            background = Color(0xFF0E0D18)
            surface = Color(0xFF1B1A26)
            surface1 = Color(0xFF13151F)
            surface2 = Color(0xFF201E2A)
            surface3 = Color(0xFF2A2934)
            border = Color(0x33928EA3)
            textPrimary = Color(0xFFE5E0F0)
            textSecondary = Color(0xFFC8C4DA)
            textTertiary = Color(0xFF928EA3)
            textMuted = Color(0xFF6C687E)
        } else {
            background = Color(0xFFF4F5F8)
            surface = Color(0xFFFFFFFF)
            surface1 = Color(0xFFE5E7EB)
            surface2 = Color(0xFFFFFFFF)
            surface3 = Color(0xFFE5E7EB)
            border = Color(0x28000000)
            textPrimary = Color(0xFF111827)
            textSecondary = Color(0xFF4B5563)
            textTertiary = Color(0xFF6B7280)
            textMuted = Color(0xFF9CA3AF)
        }
    }
}

// ── Dynamic Surface tokens (design-system) ──
val Surface1: Color get() = ThemeState.surface1
val Surface2: Color get() = ThemeState.surface2
val Surface3: Color get() = ThemeState.surface3
val HairlineBorder: Color get() = ThemeState.border

// ── Dynamic Aliases required across screens and components ──
val DarkBackground: Color get() = ThemeState.background
val DarkSurface: Color get() = ThemeState.surface
val DarkSurfaceElevated: Color get() = ThemeState.surface2
val DarkSurfaceBorder: Color get() = ThemeState.border
val AccentPrimary: Color get() = ThemeState.accentColor
val AccentPrimaryVariant: Color get() = ThemeState.accentColor.copy(alpha = 0.8f)
val AccentSecondary: Color get() = ThemeState.accentSecondary

val TextPrimary: Color get() = ThemeState.textPrimary
val TextSecondary: Color get() = ThemeState.textSecondary
val TextTertiary: Color get() = ThemeState.textTertiary
val TextMuted: Color get() = ThemeState.textMuted

val TimelineClipBg: Color get() = ThemeState.surface3
val TimelineClipBorder: Color get() = ThemeState.border
val TimelinePlayhead: Color get() = LuminescentAmber
val AudioTrackColor: Color get() = EmeraldWave
val OverlayTrackColor: Color get() = VibrantViolet
