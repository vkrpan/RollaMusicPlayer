package com.rolla.musicplayer.feature.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DP_TOLERANCE = 0.01f
private const val PADDING_TOLERANCE = 0.05f

// Float rounding between production's and the test's arithmetic; far below any real overlap.
private const val LOOP_EPSILON = 0.001f

/**
 * The widget's pure layout math (Phase 6c rulings 2, 3, P12 and P14): when the artwork shows, how wide each of the
 * five control cells is, when the content is centred in the card (P14), and, when it is not, the vertical padding and
 * the bar's corner inset (P12).
 */
class WidgetLayoutTest {

    // Stub: showArtwork = true. Mutation: threshold 330 dp → 338.9 dp shows the artwork → RED.
    @Test
    fun artworkShowsFromThreeHundredThirtyNineDp() {
        val atThreshold = widgetLayoutFor(DpSize(339.dp, 110.dp), 1f)
        assertTrue("339 dp shows the artwork", atThreshold.showArtwork)
        assertEquals("339 dp with artwork leaves 48 dp cells", 48f, atThreshold.controlCellWidth.value, DP_TOLERANCE)

        val belowThreshold = widgetLayoutFor(DpSize(338.9.dp, 110.dp), 1f)
        assertFalse("338.9 dp hides the artwork", belowThreshold.showArtwork)
        assertEquals(
            "338.9 dp without artwork spreads the five cells across the full column",
            63.78f,
            belowThreshold.controlCellWidth.value,
            DP_TOLERANCE,
        )
    }

    // Stub: constant 48 dp. Mutation: threshold 330 dp → at 330 dp the cell is (330 − 99) / 5 = 46.2 dp → RED.
    @Test
    fun everyWidthFromTheMinimumKeepsFortyEightDpCells() {
        for (tenths in 2_600..6_000) {
            val width = (tenths / 10f).dp
            val cell = widgetLayoutFor(DpSize(width, 110.dp), 1f).controlCellWidth
            assertTrue("$width gives a $cell cell, under 48 dp", cell >= 47.999.dp)
        }
        assertEquals(
            "400 dp with artwork gives (400 − 99) / 5 dp cells",
            60.2f,
            widgetLayoutFor(DpSize(400.dp, 110.dp), 1f).controlCellWidth.value,
            DP_TOLERANCE,
        )
    }

    // Stub: return 0.dp. Mutation: drop widgetArtGap from the artwork sum → 329 dp → RED.
    @Test
    fun derivedWidthsComeFromTokens() {
        assertEquals("artwork threshold is 10 + 69 + 10 + 5 × 48 + 10", 339.dp, widgetArtworkMinWidth())
        assertEquals("minimum width is 10 + 5 × 48 + 10", 260.dp, widgetMinWidth())
    }

    // Stub: padding 0. Mutation: line-height factor 1.0 instead of 1.33 → nominal 80 dp, so 92 dp is centred with
    // padding 0 → RED. Heights under the centred threshold (98.58 dp at fontScale 1.0, ruling P14): above it the
    // padding is 0 (centredModeOnlyWhenTheReportedHeightHoldsTheContent).
    @Test
    fun paddingCentresNominalContent() {
        assertEquals(
            "a 92 dp card at fontScale 1.0 pads 89.57 dp of content equally",
            1.215f,
            widgetLayoutFor(DpSize(400.dp, 92.dp), 1f).verticalPadding.value,
            PADDING_TOLERANCE,
        )
        assertEquals(
            "a 97 dp card at fontScale 1.0 pads 89.57 dp of content equally",
            3.715f,
            widgetLayoutFor(DpSize(400.dp, 97.dp), 1f).verticalPadding.value,
            PADDING_TOLERANCE,
        )
    }

    // Stub: centreContent = false. Mutation: the threshold without the 2 × clearance term → 95 dp at fontScale 1.0 is
    // centred → RED.
    @Test
    fun centredModeOnlyWhenTheReportedHeightHoldsTheContent() {
        // Threshold at fontScale 1.0: 89.57 + 2 × 4.51 = 98.58 dp.
        val atThreshold = widgetLayoutFor(DpSize(400.dp, 98.6.dp), 1f)
        assertTrue("98.6 dp is centred", atThreshold.centreContent)
        assertEquals("98.6 dp centred: no vertical padding", 0f, atThreshold.verticalPadding.value, DP_TOLERANCE)
        assertEquals("98.6 dp centred: no bar inset", 0f, atThreshold.barBottomInset.value, DP_TOLERANCE)

        // Control: just under the threshold the content fills the card, padded, with the bar inset.
        val underThreshold = widgetLayoutFor(DpSize(400.dp, 98.5.dp), 1f)
        assertFalse("98.5 dp is not centred", underThreshold.centreContent)
        assertEquals("98.5 dp: vertical padding", 4.465f, underThreshold.verticalPadding.value, PADDING_TOLERANCE)

        assertFalse("95 dp is not centred", widgetLayoutFor(DpSize(400.dp, 95.dp), 1f).centreContent)
        assertTrue(
            "167 dp (the Motorola's reported two-row height) is centred",
            widgetLayoutFor(DpSize(400.dp, 167.dp), 1f).centreContent,
        )
        assertFalse(
            "89.6 dp (the Motorola's one row, at most) is not centred",
            widgetLayoutFor(DpSize(400.dp, 89.6.dp), 1f).centreContent,
        )
        assertFalse(
            "110 dp at fontScale 2.0 is not centred (128.14 dp of nominal content)",
            widgetLayoutFor(DpSize(400.dp, 110.dp), 2f).centreContent,
        )
    }

    // Stub: constant 10 dp. Mutations: ignore fontScale, or drop the controls and bar from the nominal content → the
    // padding eats into the text, controls and bar at a large font, or a centred card is too short for its content
    // → RED.
    @Test
    fun largeFontKeepsControlsAndBarInsideTheCard() {
        val controlsAndBar = RollaDimens.minTouchTarget + RollaDimens.widgetProgressHeight
        // Centred (P14): the content wraps its nominal height, so the card must hold all of it.
        // Not centred: the space between the top and bottom padding holds the nominal text, the controls and the bar,
        // or the whole card. From 70 dp, the minResizeHeight (ruling T0c). The text column's bar inset comes out of
        // the text block (P13): the text gives way by up to the full clearance (clearance − verticalPadding, up to
        // 4.51 dp).
        for (height in 70..300) {
            for (step in 0..23) {
                val fontScale = 0.85f + step * 0.05f
                val layout = widgetLayoutFor(DpSize(400.dp, height.dp), fontScale)
                val nominal = textNominal(fontScale) + controlsAndBar
                if (layout.centreContent) {
                    assertTrue(
                        "height $height dp, fontScale $fontScale: centred, but the $nominal nominal content does " +
                            "not fit",
                        height.dp + LOOP_EPSILON.dp >= nominal,
                    )
                } else {
                    val betweenPadding = height.dp - layout.verticalPadding * 2
                    val needed = minOf(height.dp, nominal)
                    assertTrue(
                        "height $height dp, fontScale $fontScale: $betweenPadding between the top and bottom padding " +
                            "(the text column's ${layout.barBottomInset} bar inset included), under $needed",
                        betweenPadding + LOOP_EPSILON.dp >= needed,
                    )
                }
            }
        }
        assertLargeFontPadding(fontScale = 1.3f, expected = 4.43f)
        assertLargeFontPadding(fontScale = 1.5f, expected = 0.57f)
        assertLargeFontPadding(fontScale = 1.6f, expected = 0f)
        assertLargeFontPadding(fontScale = 2f, expected = 0f)
    }

    // Stub: barBottomInset = 0. Mutation: barBottomInset = 0 → a 74 dp card puts the bar flush with the corner → RED.
    @Test
    fun barClearsTheCardCorner() {
        val clearance = widgetBarCornerClearance()
        assertEquals("corner clearance = 24 − √(24² − 14²)", 4.51f, clearance.value, DP_TOLERANCE)

        val oneRow = widgetLayoutFor(DpSize(400.dp, 74.dp), 1f)
        assertEquals("74 dp card: no vertical padding", 0f, oneRow.verticalPadding.value, DP_TOLERANCE)
        assertEquals("74 dp card: the bar is lifted by the clearance", 4.51f, oneRow.barBottomInset.value, DP_TOLERANCE)

        // Control: a 110 dp card is centred (P14); the gap around the content clears the corner, so no inset.
        val roomy = widgetLayoutFor(DpSize(400.dp, 110.dp), 1f)
        assertTrue("110 dp card: centred", roomy.centreContent)
        assertEquals("110 dp card: no bar inset", 0f, roomy.barBottomInset.value, DP_TOLERANCE)

        for (height in 70..300) {
            for (step in 0..23) {
                val fontScale = 0.85f + step * 0.05f
                val layout = widgetLayoutFor(DpSize(400.dp, height.dp), fontScale)
                // Centred, the content wraps its nominal height and the gap below the bar is half the spare height.
                val bottom = if (layout.centreContent) {
                    (height.dp - contentNominal(fontScale)) / 2
                } else {
                    layout.verticalPadding + layout.barBottomInset
                }
                assertTrue(
                    "height $height dp, fontScale $fontScale (centred ${layout.centreContent}): the bar sits " +
                        "$bottom above the card's bottom",
                    bottom + LOOP_EPSILON.dp >= clearance,
                )
            }
        }
    }

    private fun assertLargeFontPadding(fontScale: Float, expected: Float) {
        assertEquals(
            "fontScale $fontScale in a 110 dp card",
            expected,
            widgetLayoutFor(DpSize(400.dp, 110.dp), fontScale).verticalPadding.value,
            PADDING_TOLERANCE,
        )
    }

    // Ruling 3's nominal text height, recomputed here so the loop does not trust production's formula.
    private fun textNominal(fontScale: Float): Dp =
        ((RollaDimens.widgetTitleSize.value + RollaDimens.widgetArtistSize.value) * 1.33f * fontScale).dp

    // Ruling 3's nominal content height: the artwork box, or the text, the controls and the bar if taller.
    private fun contentNominal(fontScale: Float): Dp = maxOf(
        RollaDimens.widgetArt,
        textNominal(fontScale) + RollaDimens.minTouchTarget + RollaDimens.widgetProgressHeight,
    )
}
