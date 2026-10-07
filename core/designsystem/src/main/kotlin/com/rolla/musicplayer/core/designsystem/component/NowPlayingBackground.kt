package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import com.rolla.musicplayer.core.designsystem.theme.NowPlayingGradient

// Fraction of the height where the base gradient settles and the bottom wash begins to fade in (measured: ~55 %).
private const val WASH_START_FRACTION = 0.55f

/**
 * The Now Playing background (spec §5.1). A vertical base runs from `top` to `middle`. A left-to-right wash
 * (`washStart` purple to `washEnd` teal) fades in toward the bottom edge. Drawing only; never recomposes.
 */
fun Modifier.nowPlayingBackground(gradient: NowPlayingGradient): Modifier = drawWithCache {
    val washTop = size.height * WASH_START_FRACTION
    val washSize = Size(size.width, size.height - washTop)
    val base = Brush.verticalGradient(
        0f to gradient.top,
        WASH_START_FRACTION to gradient.middle,
        1f to gradient.middle,
    )
    val wash = Brush.horizontalGradient(listOf(gradient.washStart, gradient.washEnd))
    val fade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = washTop, endY = size.height)
    val layerPaint = Paint()
    onDrawBehind {
        drawRect(base)
        drawContext.canvas.saveLayer(Rect(0f, washTop, size.width, size.height), layerPaint)
        drawRect(wash, topLeft = Offset(0f, washTop), size = washSize)
        drawRect(fade, topLeft = Offset(0f, washTop), size = washSize, blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
    }
}
