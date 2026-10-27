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
    val onTrack: Color,
    val watch: Color,
    val behind: Color,
    val neutral: Color,
    val onTrackContainer: Color,
    val watchContainer: Color,
    val behindContainer: Color,
    val neutralContainer: Color,
    val isDark: Boolean
)

val DarkNHAColors = NHAColors(
    background = Color(0xFF090D16),
    surface = Color(0xFF151C28),
    surfaceVariant = Color(0xFF222B3B),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    border = Color(0xFF334155),
    cardHeader = Color(0xFF1E293B),
    onTrack = Color(0xFF4ADE80),
    watch = Color(0xFFFBBF24),
    behind = Color(0xFFFCA5A5),
    neutral = Color(0xFF94A3B8),
    onTrackContainer = Color(0xFF062C1E),
    watchContainer = Color(0xFF331D08),
    behindContainer = Color(0xFF371215),
    neutralContainer = Color(0xFF1E293B),
    isDark = true
)

val LightNHAColors = NHAColors(
    background = Color(0xFFF8FAFC),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE2E8F0),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    border = Color(0xFFE2E8F0),
    cardHeader = Color(0xFFE2E8F0),
    onTrack = Color(0xFF15803D),
    watch = Color(0xFFB45309),
    behind = Color(0xFFB91C1C),
    neutral = Color(0xFF64748B),
    onTrackContainer = Color(0xFFDCFCE7),
    watchContainer = Color(0xFFFEF3C7),
    behindContainer = Color(0xFFFEE2E2),
    neutralContainer = Color(0xFFF1F5F9),
    isDark = false
)

val LocalNHAColors = staticCompositionLocalOf { DarkNHAColors }

// Weather Condition Colors
val WeatherFairGreen = Color(0xFF22C55E)
val WeatherCloudyYellow = Color(0xFFF59E0B)
val WeatherRainShowersCyan = Color(0xFF38BDF8)
val WeatherRainyBlue = Color(0xFF3B82F6)
val WeatherStormyRed = Color(0xFFEF4444)
