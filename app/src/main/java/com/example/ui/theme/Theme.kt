package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.data.model.AppThemeMode

private val DarkColorScheme = darkColorScheme(
    // One accent: Figma blue, lifted from #0D99FF to pass 4.5:1 on the grey panels.
    // Secondary follows it so no second accent creeps in.
    primary = Color(0xFF3DADFF),
    onPrimary = Color(0xFF1E1E1E),
    primaryContainer = Color(0xFF1B3A57),
    onPrimaryContainer = Color(0xFFE5F4FF),
    secondary = Color(0xFF3DADFF),
    onSecondary = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF1B3A57),
    onSecondaryContainer = Color(0xFFE5F4FF),
    background = DarkNHAColors.background,
    onBackground = DarkNHAColors.textPrimary,
    surface = DarkNHAColors.surface,
    onSurface = DarkNHAColors.textPrimary,
    surfaceVariant = DarkNHAColors.surfaceVariant,
    onSurfaceVariant = DarkNHAColors.textSecondary,
    outline = Color(0xFF8A8A8A),
    outlineVariant = DarkNHAColors.border,
    surfaceTint = DarkNHAColors.surface,
    surfaceDim = Color(0xFF1A1A1A),
    surfaceBright = Color(0xFF444444),
    surfaceContainerLowest = Color(0xFF181818),
    surfaceContainerLow = DarkNHAColors.surface,
    surfaceContainer = Color(0xFF2C2C2C),
    surfaceContainerHigh = Color(0xFF2C2C2C),
    surfaceContainerHighest = Color(0xFF383838)
)

private val LightColorScheme = lightColorScheme(
    // Figma blue, darkened from #0D99FF so white text on it passes 4.5:1.
    primary = Color(0xFF0A6FD4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE5F4FF),
    onPrimaryContainer = Color(0xFF0757A8),
    secondary = Color(0xFF0A6FD4),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5F4FF),
    onSecondaryContainer = Color(0xFF0757A8),
    background = LightNHAColors.background,
    onBackground = LightNHAColors.textPrimary,
    surface = LightNHAColors.surface,
    onSurface = LightNHAColors.textPrimary,
    surfaceVariant = LightNHAColors.surfaceVariant,
    onSurfaceVariant = LightNHAColors.textSecondary,
    outline = Color(0xFF8C8C8C),
    outlineVariant = LightNHAColors.border,
    surfaceTint = LightNHAColors.surface,
    surfaceDim = Color(0xFFE6E6E6),
    surfaceBright = Color(0xFFFFFFFF),
    // Menus and dialogs are white panels on the grey canvas, as in Figma.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF0F0F0)
)

// Small radii: controls 4-6dp, cards 8dp, dialogs and sheets 12dp.
private val FigmaShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(12.dp),
    extraLarge = RoundedCornerShape(12.dp)
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
            shapes = FigmaShapes,
            content = content
        )
    }
}
