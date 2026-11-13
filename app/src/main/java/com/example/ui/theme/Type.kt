package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

// IBM Plex Sans, drawn from IBM's drafting and typewriter heritage, bundled as one variable font
// (SIL OFL, licenses/IBM-Plex-OFL.txt). Its figures are tabular as drawn.
// Below Android 8 variation settings are ignored and Compose fakes the bolder weights.
@OptIn(ExperimentalTextApi::class)
val PlexSans = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.SemiBold).map { w ->
        Font(R.font.ibm_plex_sans, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

// IBM Plex Mono for measured figures (money, percentages, counts): two weights, as the text.
val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold)
)

// Material sizes kept (read outdoors on site), with tighter tracking and SemiBold titles.
// Material's 0.25-0.5sp tracking is dropped.
private val M3 = Typography().run {
    fun TextStyle.plex() = copy(fontFamily = PlexSans)
    Typography(
        displayLarge.plex(), displayMedium.plex(), displaySmall.plex(),
        headlineLarge.plex(), headlineMedium.plex(), headlineSmall.plex(),
        titleLarge.plex(), titleMedium.plex(), titleSmall.plex(),
        bodyLarge.plex(), bodyMedium.plex(), bodySmall.plex(),
        labelLarge.plex(), labelMedium.plex(), labelSmall.plex()
    )
}

val Typography = Typography(
    displayLarge = M3.displayLarge.copy(letterSpacing = (-0.5).sp),
    displayMedium = M3.displayMedium.copy(letterSpacing = (-0.5).sp),
    displaySmall = M3.displaySmall.copy(letterSpacing = (-0.25).sp),
    headlineLarge = M3.headlineLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = M3.headlineMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = M3.headlineSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = M3.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = M3.titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = M3.titleSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    bodyLarge = M3.bodyLarge.copy(letterSpacing = 0.sp),
    bodyMedium = M3.bodyMedium.copy(letterSpacing = 0.sp),
    bodySmall = M3.bodySmall.copy(letterSpacing = 0.1.sp),
    // Two weights only (Normal and SemiBold), and nothing under 12sp: read outdoors on site.
    labelLarge = M3.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    labelMedium = M3.labelMedium.copy(fontWeight = FontWeight.Normal, letterSpacing = 0.1.sp),
    labelSmall = M3.labelSmall.copy(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
)

// Money, percentages and counts that line up in columns are set in the mono, as instrument readouts.
val TabularNumbers = TextStyle(fontFamily = PlexMono)

private val figure = Regex("-?₱?\\d[\\d,.]*%?")

/** Whether a shown value is only a measured figure: an amount, a percentage or a number (the website's `isFigure`). */
fun isFigure(value: String) = figure.matches(value.trim())

/** The mono for a value that is only a figure; words, and figures mixed with words, stay in the text face. */
fun TextStyle.figures(value: String): TextStyle = if (isFigure(value)) merge(TabularNumbers) else this
