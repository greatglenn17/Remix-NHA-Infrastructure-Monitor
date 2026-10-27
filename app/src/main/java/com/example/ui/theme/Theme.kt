package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF0F172A),
    primaryContainer = Color(0xFF1E293B),
    onPrimaryContainer = Color(0xFFF8FAFC),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF451A03),
    background = DarkNHAColors.background,
    onBackground = DarkNHAColors.textPrimary,
    surface = DarkNHAColors.surface,
    onSurface = DarkNHAColors.textPrimary,
    surfaceVariant = DarkNHAColors.surfaceVariant,
    onSurfaceVariant = DarkNHAColors.textSecondary,
    outline = DarkNHAColors.border
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0369A1),
    secondary = Color(0xFFD97706),
    onSecondary = Color(0xFFFFFFFF),
    background = LightNHAColors.background,
    onBackground = LightNHAColors.textPrimary,
    surface = LightNHAColors.surface,
    onSurface = LightNHAColors.textPrimary,
    surfaceVariant = LightNHAColors.surfaceVariant,
    onSurfaceVariant = LightNHAColors.textSecondary,
    outline = LightNHAColors.border
)

@Composable
fun NHATheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> darkTheme
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme
    val nhaColors = if (isDark) DarkNHAColors else LightNHAColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = nhaColors.background.toArgb()
            window.navigationBarColor = nhaColors.background.toArgb()
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDark
            insetsController.isAppearanceLightNavigationBars = !isDark
        }
    }

    CompositionLocalProvider(LocalNHAColors provides nhaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
