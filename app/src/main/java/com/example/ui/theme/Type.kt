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

// Inter, Figma's own UI face, bundled as one variable font (SIL OFL, licenses/Inter-OFL.txt).
// Below Android 8 variation settings are ignored and Compose fakes the bolder weights.
@OptIn(ExperimentalTextApi::class)
val Inter = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { w ->
        Font(R.font.inter, w, variationSettings = FontVariation.Settings(FontVariation.weight(w.weight)))
    }
)

// Material sizes kept (read outdoors on site), with tighter tracking and SemiBold titles.
// Material's 0.25-0.5sp tracking is dropped.
private val M3 = Typography().run {
    fun TextStyle.inter() = copy(fontFamily = Inter)
    Typography(
        displayLarge.inter(), displayMedium.inter(), displaySmall.inter(),
        headlineLarge.inter(), headlineMedium.inter(), headlineSmall.inter(),
        titleLarge.inter(), titleMedium.inter(), titleSmall.inter(),
        bodyLarge.inter(), bodyMedium.inter(), bodySmall.inter(),
        labelLarge.inter(), labelMedium.inter(), labelSmall.inter()
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
    labelLarge = M3.labelLarge.copy(letterSpacing = 0.sp),
    labelMedium = M3.labelMedium.copy(letterSpacing = 0.1.sp),
    labelSmall = M3.labelSmall.copy(letterSpacing = 0.2.sp)
)

// Tabular digits so money and percentages line up in columns.
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
