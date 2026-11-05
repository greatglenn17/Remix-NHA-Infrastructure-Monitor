package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// NHA Dynamic Theme Color Palette Data Structure
//
// What the status colours mean, so red keeps its weight:
// - behind (red): critical only. 15% or more behind (varianceColor), Critical issues, overdue,
//   errors, and the Delete item in a menu or the confirm button that deletes.
// - watch (amber): warnings. 10-15% behind, billing ahead of work, waiting approval, advisories.
// - neutral (grey): small slips under 10%, history (old values, deleted entries), and
//   always-visible trash icons that only start a delete.
// - onTrack (green): on or ahead of target.
// - Plain actions (Clear filters, Remove attachment) use the primary colour, never red.
// Progress against target always goes through varianceColor, not a red/green split.
// Exception: the subdivision lot map keeps its progress scale (0-25% red ... 100% green).
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
    // Lot progress bands between behind (0-25%) and onTrack (100%).
    val progressQuarter2: Color,
    val progressQuarter3: Color,
    val progressQuarter4: Color,
    // Login brand panel gradient; white text sits on it in both themes.
    val heroStart: Color,
    val heroEnd: Color,
    val isDark: Boolean
)

val DarkNHAColors = NHAColors(
    // Figma-style dark: neutral greys (no blue cast), hairline borders, one blue accent.
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF2C2C2C),
    surfaceVariant = Color(0xFF383838),
    textPrimary = Color(0xFFF5F5F5),
    textSecondary = Color(0xFFA3A3A3),
    border = Color(0xFF444444),
    cardHeader = Color(0xFF383838),
    onTrack = Color(0xFF4ADE80),
    watch = Color(0xFFFBBF24),
    behind = Color(0xFFFCA5A5),
    neutral = Color(0xFFA3A3A3),
    onTrackContainer = Color(0xFF173322),
    watchContainer = Color(0xFF3A2D10),
    behindContainer = Color(0xFF3F1E1E),
    neutralContainer = Color(0xFF383838),
    progressQuarter2 = Color(0xFFFB923C),
    progressQuarter3 = Color(0xFFFACC15),
    progressQuarter4 = Color(0xFF60A5FA),
    heroStart = Color(0xFF15508F),
    heroEnd = Color(0xFF0A2440),
    isDark = true
)

val LightNHAColors = NHAColors(
    // Figma-style light: grey canvas, white panels, 1px #E6E6E6 hairlines, one blue accent.
    background = Color(0xFFF5F5F5),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F0),
    textPrimary = Color(0xFF1E1E1E),
    textSecondary = Color(0xFF666666),
    border = Color(0xFFE6E6E6),
    cardHeader = Color(0xFFF5F5F5),
    onTrack = Color(0xFF15803D),
    watch = Color(0xFFB45309),
    behind = Color(0xFFB91C1C),
    neutral = Color(0xFF6B6B6B),
    onTrackContainer = Color(0xFFDCFCE7),
    watchContainer = Color(0xFFFEF3C7),
    behindContainer = Color(0xFFFEE2E2),
    neutralContainer = Color(0xFFF0F0F0),
    progressQuarter2 = Color(0xFFEA580C),
    progressQuarter3 = Color(0xFFCA8A04),
    progressQuarter4 = Color(0xFF2563EB),
    heroStart = Color(0xFF0A6FD4),
    heroEnd = Color(0xFF06396E),
    isDark = false
)

val LocalNHAColors = staticCompositionLocalOf { DarkNHAColors }

// Weather Condition Colors
val WeatherFairGreen = Color(0xFF22C55E)
val WeatherCloudyYellow = Color(0xFFF59E0B)
val WeatherRainShowersCyan = Color(0xFF38BDF8)
val WeatherRainyBlue = Color(0xFF3B82F6)
val WeatherStormyRed = Color(0xFFEF4444)
