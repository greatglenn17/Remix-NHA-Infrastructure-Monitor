package com.example.ui.theme

import androidx.compose.runtime.State
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith

// Figma-style motion: short, ease-out, no overshoot. Compose scales every duration by the
// system animator scale, so "Remove animations" turns these off with no extra code.
object Motion {
    const val FAST = 150
    const val MEDIUM = 220
    val Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    fun <T> fast(): FiniteAnimationSpec<T> = tween(FAST, easing = Easing)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(MEDIUM, easing = Easing)

    /** Sections, tabs and chips: the old view fades out quickly and the new one fades in just after. */
    fun fadeThrough(): ContentTransform =
        fadeIn(tween(FAST, delayMillis = 60, easing = Easing)) togetherWith fadeOut(tween(90, easing = Easing))

    /** List to detail: the detail grows in from slightly smaller, like a Figma frame opening. */
    fun open(): ContentTransform =
        (fadeIn(medium()) + scaleIn(medium(), initialScale = 0.96f)) togetherWith fadeOut(tween(90, easing = Easing))

    /** Cards dip to 98% while pressed. Read the State inside graphicsLayer so only drawing updates. */
    @Composable
    fun pressScale(source: InteractionSource): State<Float> =
        animateFloatAsState(if (source.collectIsPressedAsState().value) 0.98f else 1f, fast(), label = "press")

    /** Detail back to list: the detail shrinks away while the list fades back in under it. */
    fun close(): ContentTransform =
        fadeIn(tween(FAST, easing = Easing)) togetherWith (fadeOut(fast()) + scaleOut(fast(), targetScale = 0.96f))
}
