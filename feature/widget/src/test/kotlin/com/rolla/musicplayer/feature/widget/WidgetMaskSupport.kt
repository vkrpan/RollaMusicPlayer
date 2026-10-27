package com.rolla.musicplayer.feature.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat

/** A pixel belongs to a mask when its alpha (or its ink coverage) is at least half. */
private const val MASK_ALPHA = 128
private const val HALF_COVERAGE = 0.5

/** A square glyph mask of [size] × [size] pixels, row-major. */
internal class GlyphMask(val size: Int, private val bits: BooleanArray) {

    operator fun get(x: Int, y: Int): Boolean = bits[y * size + x]

    /** The mask mirrored about its vertical centre line. */
    fun mirrored(): GlyphMask = GlyphMask(size, BooleanArray(size * size) { i -> this[size - 1 - i % size, i / size] })
}

/**
 * Draws a widget drawable with the platform renderer the launcher uses (`Drawable.setBounds` + `draw` on a bitmap
 * canvas; run under `@GraphicsMode(NATIVE)`), at [width] × [height] px on a transparent bitmap.
 */
internal fun Context.drawableBitmap(@DrawableRes id: Int, width: Int, height: Int): Bitmap {
    val drawable = checkNotNull(ContextCompat.getDrawable(this, id)) { "drawable $id did not load" }
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(Canvas(bitmap))
    return bitmap
}

/** The alpha mask (alpha >= 128) of the [size]-px square of this bitmap whose top-left corner is ([left], [top]). */
internal fun Bitmap.alphaMask(size: Int, left: Int = 0, top: Int = 0): GlyphMask {
    check(left + size <= width && top + size <= height) { "a $size px crop at ($left, $top) leaves ${width}x$height" }
    return GlyphMask(
        size,
        BooleanArray(size * size) { i -> Color.alpha(getPixel(left + i % size, top + i / size)) >= MASK_ALPHA },
    )
}

/**
 * The ink mask of a glyph drawn in [ink] over the opaque [background] (both ARGB), for the [size]-px square at
 * ([left], [top]). On an opaque picture alpha is 255 everywhere, so a pixel's share of ink is read from its colour
 * instead: (pixel − background) / (ink − background), averaged over red, green and blue. A pixel with at least half
 * its area inked is in the mask, the same cut as alpha >= 128 on a transparent render.
 */
internal fun Bitmap.inkMask(size: Int, left: Int, top: Int, background: Int, ink: Int): GlyphMask {
    check(left + size <= width && top + size <= height) { "a $size px crop at ($left, $top) leaves ${width}x$height" }
    val channels = listOf(Color::red, Color::green, Color::blue)
    return GlyphMask(
        size,
        BooleanArray(size * size) { i ->
            val pixel = getPixel(left + i % size, top + i / size)
            val coverage = channels.sumOf { channel ->
                (channel(pixel) - channel(background)).toDouble() / (channel(ink) - channel(background))
            } / channels.size
            coverage >= HALF_COVERAGE
        },
    )
}

/** Intersection over union of two masks, counting only pixels where [inRegion] holds; 0 when both are empty there. */
internal fun iou(a: GlyphMask, b: GlyphMask, inRegion: (Int, Int) -> Boolean = { _, _ -> true }): Double {
    check(a.size == b.size) { "masks of ${a.size} px and ${b.size} px cannot be compared" }
    val pixels = (0 until a.size * a.size).map { i -> i % a.size to i / a.size }.filter { (x, y) -> inRegion(x, y) }
    val intersection = pixels.count { (x, y) -> a[x, y] && b[x, y] }
    val union = pixels.count { (x, y) -> a[x, y] || b[x, y] }
    return if (union == 0) 0.0 else intersection.toDouble() / union
}

internal fun Double.fmt(): String = "%.4f".format(this)
