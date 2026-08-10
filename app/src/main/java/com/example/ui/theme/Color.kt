package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// NHA Dynamic Theme Color Palette Data Structure
data class NHAColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color,
    val cardHeader: Color,
    val isDark: Boolean
)

// Executive Dark Theme Palette
val StaticDarkBackground = Color(0xFF090D16)      // Deep black-slate canvas background
val StaticDarkSurface = Color(0xFF151C28)         // Sleek dark card container
val StaticDarkSurfaceVariant = Color(0xFF222B3B)  // Dark input field / chip background
val StaticDarkTextPrimary = Color(0xFFF8FAFC)     // Crisp white primary text
val StaticDarkTextSecondary = Color(0xFF94A3B8)   // Clean muted text
val StaticDarkBorder = Color(0xFF334155)          // Crisp dark outline border
val StaticDarkCardHeader = Color(0xFF1E293B)

// Executive Light Theme Palette
val StaticLightBackground = Color(0xFFF1F5F9)     // Crisp light slate background
val StaticLightSurface = Color(0xFFFFFFFF)        // Clean white card background
val StaticLightSurfaceVariant = Color(0xFFE2E8F0) // Light input field / chip background
val StaticLightTextPrimary = Color(0xFF0F172A)    // Crisp dark slate primary text
val StaticLightTextSecondary = Color(0xFF64748B)  // Clean muted slate text
val StaticLightBorder = Color(0xFFCBD5E1)         // Clean light outline border
val StaticLightCardHeader = Color(0xFFE2E8F0)

val DarkNHAColors = NHAColors(
    background = StaticDarkBackground,
    surface = StaticDarkSurface,
    surfaceVariant = StaticDarkSurfaceVariant,
    textPrimary = StaticDarkTextPrimary,
    textSecondary = StaticDarkTextSecondary,
    border = StaticDarkBorder,
    cardHeader = StaticDarkCardHeader,
    isDark = true
)

val LightNHAColors = NHAColors(
    background = StaticLightBackground,
    surface = StaticLightSurface,
    surfaceVariant = StaticLightSurfaceVariant,
    textPrimary = StaticLightTextPrimary,
    textSecondary = StaticLightTextSecondary,
    border = StaticLightBorder,
    cardHeader = StaticLightCardHeader,
    isDark = false
)

val LocalNHAColors = staticCompositionLocalOf { DarkNHAColors }

// Dynamic Theme Getters for Seamless Composable UI Adaptation
val DarkBackground: Color
    @Composable get() = LocalNHAColors.current.background

val DarkSurface: Color
    @Composable get() = LocalNHAColors.current.surface

val DarkSurfaceVariant: Color
    @Composable get() = LocalNHAColors.current.surfaceVariant

val DarkTextPrimary: Color
    @Composable get() = LocalNHAColors.current.textPrimary

val DarkTextSecondary: Color
    @Composable get() = LocalNHAColors.current.textSecondary

val DarkBorder: Color
    @Composable get() = LocalNHAColors.current.border

val DarkCardHeader: Color
    @Composable get() = LocalNHAColors.current.cardHeader

// Accent Colors
val NavyPrimary = Color(0xFF38BDF8)
val NavySecondary = Color(0xFF1E293B)
val GoldAccent = Color(0xFFF59E0B)
val ErrorRed = Color(0xFFEF4444)
val NhaBlue = Color(0xFF0284C7)
val GreenAccent = Color(0xFF22C55E)

// Dynamic Color Tokens
val GeoBackground: Color @Composable get() = LocalNHAColors.current.background
val GeoTextPrimary: Color @Composable get() = LocalNHAColors.current.textPrimary
val GeoTextSecondary: Color @Composable get() = LocalNHAColors.current.textSecondary
val GeoNeutralBg: Color @Composable get() = LocalNHAColors.current.surfaceVariant
val GeoNeutralBorder: Color @Composable get() = LocalNHAColors.current.border

val BackgroundLight: Color @Composable get() = LocalNHAColors.current.background
val SurfaceLight: Color @Composable get() = LocalNHAColors.current.surface
val SurfaceVariantLight: Color @Composable get() = LocalNHAColors.current.surfaceVariant

// Status Indicators (Theme Adaptive)
// Green (On-Track / Positive)
val StatusGreenBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF062C1E) else Color(0xFFDCFCE7)
val StatusGreenBorder: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF166534) else Color(0xFF86EFAC)
val StatusGreenText: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
val StatusGreenSubtext: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF86EFAC) else Color(0xFF166534)
val StatusGreenBarBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF064E3B) else Color(0xFFBBF7D0)
val StatusGreenBarFill = Color(0xFF22C55E)

// Red (Critical / Slippage)
val StatusRedBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF371215) else Color(0xFFFEE2E2)
val StatusRedBorder: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF991B1B) else Color(0xFFFCA5A5)
val StatusRedText: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C)
val StatusRedSubtext: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFFFECACA) else Color(0xFF991B1B)
val StatusRedBarBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF7F1D1D) else Color(0xFFFECACA)
val StatusRedBarFill = Color(0xFFEF4444)

// Orange (Behind Schedule)
val StatusOrangeBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF331D08) else Color(0xFFFFEDD5)
val StatusOrangeBorder: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF9A3412) else Color(0xFFFDBA74)
val StatusOrangeText: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFFFDBA74) else Color(0xFFC2410C)
val StatusOrangeSubtext: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFFFED7AA) else Color(0xFF9A3412)
val StatusOrangeBarBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF7C2D12) else Color(0xFFFED7AA)
val StatusOrangeBarFill = Color(0xFFF97316)

// Gray (Suspended / Neutral)
val StatusGrayBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF18181B) else Color(0xFFF4F4F5)
val StatusGrayBorder: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF3F3F46) else Color(0xFFD4D4D8)
val StatusGrayText: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFFE4E4E7) else Color(0xFF3F3F46)
val StatusGraySubtext: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFFA1A1AA) else Color(0xFF71717A)
val StatusGrayBarBg: Color @Composable get() = if (LocalNHAColors.current.isDark) Color(0xFF27272A) else Color(0xFFE4E4E7)
val StatusGrayBarFill = Color(0xFF71717A)

// Weather Condition Colors
val WeatherFairGreen = Color(0xFF22C55E)
val WeatherCloudyYellow = Color(0xFFF59E0B)
val WeatherRainShowersCyan = Color(0xFF38BDF8)
val WeatherRainyBlue = Color(0xFF3B82F6)
val WeatherStormyRed = Color(0xFFEF4444)
