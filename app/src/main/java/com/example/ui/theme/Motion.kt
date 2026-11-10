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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

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

    /** A small icon changing state pops in, so "Synced" lands like a check mark after an upload. */
    fun pop(): ContentTransform =
        (fadeIn(fast()) + scaleIn(medium(), initialScale = 0.4f)) togetherWith fadeOut(tween(90, easing = Easing))

    /** Detail back to list: the detail shrinks away while the list fades back in under it. */
    fun close(): ContentTransform =
        fadeIn(tween(FAST, easing = Easing)) togetherWith (fadeOut(fast()) + scaleOut(fast(), targetScale = 0.96f))
}

/** A soft light sweeping across a placeholder while a photo or plan loads. */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = MaterialTheme.colorScheme.surfaceVariant
    val light = MaterialTheme.colorScheme.surface
    val sweep by rememberInfiniteTransition(label = "shimmer")
        .animateFloat(-0.5f, 1.5f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "sweep")
    return drawBehind {
        val x = sweep * size.width
        drawRect(Brush.linearGradient(listOf(base, light, base), Offset(x - size.width / 2, 0f), Offset(x + size.width / 2, size.height)))
    }
}

/** AsyncImage that shimmers until the photo arrives (and stops if it can't load). */
@Composable
fun LoadingImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    var loading by remember(model) { mutableStateOf(true) }
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        modifier = if (loading) modifier.shimmer() else modifier,
        contentScale = contentScale,
        onState = { loading = it is AsyncImagePainter.State.Loading || it is AsyncImagePainter.State.Empty }
    )
}
