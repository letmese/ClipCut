package com.clipcut.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.clipcut.ui.viewmodel.SettingsViewModel

/**
 * ClipCut theme with real-time responsive light/dark/system mode and dynamic creative accent.
 */
@Composable
fun ClipCutTheme(
    settingsViewModel: SettingsViewModel? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val vm = settingsViewModel ?: androidx.lifecycle.viewmodel.compose.viewModel<SettingsViewModel>()
    val themeMode by vm.theme.collectAsState()
    val accentHex by vm.accent.collectAsState()

    // Sync ThemeState immediately during composition
    ThemeState.update(themeMode, accentHex, darkTheme)

    val colorScheme = if (ThemeState.isDark) {
        darkColorScheme(
            primary = ThemeState.accentColor,
            onPrimary = Color.Black,
            primaryContainer = ThemeState.accentColor.copy(alpha = 0.3f),
            onPrimaryContainer = Color.White,
            secondary = VibrantViolet,
            tertiary = LuminescentAmber,
            background = ThemeState.background,
            onBackground = ThemeState.textPrimary,
            surface = ThemeState.surface,
            onSurface = ThemeState.textPrimary,
            surfaceVariant = ThemeState.surface3,
            onSurfaceVariant = ThemeState.textSecondary,
            outline = ThemeState.border
        )
    } else {
        lightColorScheme(
            primary = ThemeState.accentColor,
            onPrimary = Color.White,
            primaryContainer = ThemeState.accentColor.copy(alpha = 0.2f),
            onPrimaryContainer = Color.Black,
            secondary = VibrantViolet,
            tertiary = LuminescentAmber,
            background = ThemeState.background,
            onBackground = ThemeState.textPrimary,
            surface = ThemeState.surface,
            onSurface = ThemeState.textPrimary,
            surfaceVariant = ThemeState.surface3,
            onSurfaceVariant = ThemeState.textSecondary,
            outline = ThemeState.border
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}

object ClipCutColors {
    val Base: Color get() = ThemeState.background
    val Surface1: Color get() = ThemeState.surface1
    val Surface2: Color get() = ThemeState.surface2
    val Surface3: Color get() = ThemeState.surface3
    val Accent: Color get() = ThemeState.accentColor
    val AccentTeal: Color get() = EmeraldWave
    val AccentBlue: Color get() = ThemeState.accentColor
    val AccentGold: Color get() = LuminescentAmber
    val AccentViolet: Color get() = VibrantViolet
}
