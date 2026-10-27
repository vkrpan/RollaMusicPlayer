package com.rolla.musicplayer.feature.widget

import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

private const val RATIO_TOLERANCE = 0.05
private const val TEXT_FLOOR = 4.5
private const val NON_TEXT_FLOOR = 3.0

// The spec §13 track colour that ruling 1 replaces.
private const val SPEC_TRACK = 0xFF333333.toInt()

/**
 * WCAG 2.x contrast of the widget palette on its black card (Phase 6c ruling 4 pins the ratios; ruling 1 explains the
 * track). The helper is local so the test does not share a bug with production code.
 *
 * The arithmetic is plain JVM, but reading the palette constants initialises MusicWidgetTheme.kt's file class, whose
 * [RollaWidgetColors] calls into `android.graphics.Color` (Glance's widget-background tone adjustment); Robolectric
 * supplies that.
 */
@RunWith(AndroidJUnit4::class)
class WidgetContrastTest {

    // Stub: ratio 21 always. Mutation: luminance without the sRGB curve → title ≈ 20.8 → RED.
    @Test
    fun textPairsClearFourPointFive() {
        assertRatio("title on card", WidgetTitle.toArgb(), WidgetCard.toArgb(), 20.49, TEXT_FLOOR)
        assertRatio("artist on card", WidgetArtist.toArgb(), WidgetCard.toArgb(), 9.70, TEXT_FLOOR)
    }

    // Stub: ratio 21 always. Mutation: luminance without the sRGB curve → fill ≈ 9.4 → RED.
    @Test
    fun nonTextPairsClearThree() {
        assertRatio("glyph tint on card", WidgetTitle.toArgb(), WidgetCard.toArgb(), 20.49, NON_TEXT_FLOOR)
        assertRatio("progress fill on card", WidgetAccent.toArgb(), WidgetCard.toArgb(), 4.65, NON_TEXT_FLOOR)
        assertRatio("progress track on card", WidgetTrack.toArgb(), WidgetCard.toArgb(), 3.56, NON_TEXT_FLOOR)
        assertRatio(
            "note on placeholder",
            WidgetTitle.toArgb(),
            WidgetArtPlaceholder.toArgb(),
            11.79,
            NON_TEXT_FLOOR,
        )
    }

    // The control for the helper itself: it can return a failing ratio. Stub: ratio 21 always → RED.
    // Mutation: luminance without the sRGB curve (raw channel / 255) → #333333 gives 5.0 → RED.
    @Test
    fun specTrackWouldFail() {
        val ratio = contrastRatio(SPEC_TRACK, WidgetCard.toArgb())
        assertEquals("spec track #333333 on card", 1.66, ratio, RATIO_TOLERANCE)
        assertTrue("spec track #333333 on card is $ratio, expected under 3:1", ratio < NON_TEXT_FLOOR)
    }

    private fun assertRatio(pair: String, foreground: Int, background: Int, expected: Double, floor: Double) {
        val ratio = contrastRatio(foreground, background)
        assertEquals("$pair contrast", expected, ratio, RATIO_TOLERANCE)
        assertTrue("$pair contrast $ratio is under $floor:1", ratio >= floor)
    }

    private fun contrastRatio(foreground: Int, background: Int): Double {
        val a = relativeLuminance(foreground)
        val b = relativeLuminance(background)
        return (max(a, b) + 0.05) / (min(a, b) + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        val r = linearChannel((argb shr 16) and 0xFF)
        val g = linearChannel((argb shr 8) and 0xFF)
        val b = linearChannel(argb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearChannel(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
