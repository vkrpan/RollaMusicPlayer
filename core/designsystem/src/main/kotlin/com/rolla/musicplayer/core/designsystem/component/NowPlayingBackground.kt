package com.rolla.musicplayer.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import com.rolla.musicplayer.core.designsystem.theme.NowPlayingGradient

// Fraction of the height where the base gradient settles and the bottom wash begins to fade in (measured: ~55 %).
private const val WASH_START_FRACTION = 0.55f

/**
 * The Now Playing background (spec §5.1). A vertical base runs from `top` to `middle`. A left-to-right wash
 * (`washStart` purple to `washEnd` teal) fades in toward the bottom edge. Drawing only; never recomposes.
 *
 * Equal gradients give equal modifiers, so a caller recomposing with the same gradient keeps the node and its brushes.
 * The brushes are rebuilt only when the size or the gradient changes.
 */
fun Modifier.nowPlayingBackground(gradient: NowPlayingGradient): Modifier =
    this then NowPlayingBackgroundElement(gradient)

private data class NowPlayingBackgroundElement(val gradient: NowPlayingGradient) :
    ModifierNodeElement<NowPlayingBackgroundNode>() {
    override fun create() = NowPlayingBackgroundNode(gradient)

    override fun update(node: NowPlayingBackgroundNode) {
        node.update(gradient)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "nowPlayingBackground"
        properties["gradient"] = gradient
    }
}

private class NowPlayingBackgroundNode(private var gradient: NowPlayingGradient) : Modifier.Node(), DrawModifierNode {
    private var cachedSize = Size.Unspecified
    private lateinit var base: Brush
    private lateinit var wash: Brush
    private lateinit var fade: Brush
    private val layerPaint = Paint()

    fun update(newGradient: NowPlayingGradient) {
        if (newGradient == gradient) return
        gradient = newGradient
        cachedSize = Size.Unspecified
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        if (size != cachedSize) rebuild(size)
        val washTop = size.height * WASH_START_FRACTION
        val washSize = Size(size.width, size.height - washTop)
        drawRect(base)
        drawContext.canvas.saveLayer(Rect(0f, washTop, size.width, size.height), layerPaint)
        drawRect(wash, topLeft = Offset(0f, washTop), size = washSize)
        drawRect(fade, topLeft = Offset(0f, washTop), size = washSize, blendMode = BlendMode.DstIn)
        drawContext.canvas.restore()
        drawContent()
    }

    private fun rebuild(size: Size) {
        val washTop = size.height * WASH_START_FRACTION
        base = Brush.verticalGradient(0f to gradient.top, WASH_START_FRACTION to gradient.middle, 1f to gradient.middle)
        wash = Brush.horizontalGradient(listOf(gradient.washStart, gradient.washEnd))
        fade = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = washTop, endY = size.height)
        cachedSize = size
    }
}
