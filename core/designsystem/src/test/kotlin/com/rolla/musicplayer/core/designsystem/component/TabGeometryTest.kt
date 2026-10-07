package com.rolla.musicplayer.core.designsystem.component

import org.junit.Assert.assertEquals
import org.junit.Test

class TabGeometryTest {

    @Test
    fun emphasisIsFullOnTheCurrentPageAndFadesLinearly() {
        assertEquals(1f, tabEmphasis(position = 2f, index = 2), 0.0001f)
        assertEquals(0f, tabEmphasis(position = 2f, index = 3), 0.0001f)
        assertEquals(0.5f, tabEmphasis(position = 2.5f, index = 3), 0.0001f)
        assertEquals(0f, tabEmphasis(position = 0f, index = 5), 0.0001f)
    }

    @Test
    fun scaleInterpolatesBetweenMinAndOne() {
        assertEquals(0.5f, tabScale(minScale = 0.5f, emphasis = 0f), 0.0001f)
        assertEquals(1f, tabScale(minScale = 0.5f, emphasis = 1f), 0.0001f)
        assertEquals(0.75f, tabScale(minScale = 0.5f, emphasis = 0.5f), 0.0001f)
    }

    @Test
    fun anchorInterpolatesBetweenNeighbourCentersAndClamps() {
        val centers = listOf(10f, 30f, 70f)
        assertEquals(30f, interpolateAnchor(centers, 1f), 0.0001f)
        assertEquals(50f, interpolateAnchor(centers, 1.5f), 0.0001f)
        assertEquals(70f, interpolateAnchor(centers, 9f), 0.0001f)
        assertEquals(10f, interpolateAnchor(centers, -2f), 0.0001f)
        assertEquals(0f, interpolateAnchor(emptyList(), 0f), 0.0001f)
    }

    @Test
    fun selectedTabIsCenteredInTheRow() {
        // widths 100 each, half-size neighbours: scaled widths 50 / 100 / 50, spacing 10.
        val widths = listOf(100, 100, 100)
        val edges = tabSlotEdges(
            widths = widths,
            spacing = 10f,
            minScale = 0.5f,
            position = 1f,
            rowWidth = 400,
        )
        assertEquals(200, (edges[1] + edges[2]) / 2) // selected slot centered on 400 / 2
        assertEquals(listOf(65, 150, 235), labelLefts(widths, edges))
    }

    @Test
    fun slotsAreContiguousAndHoldEachScaledLabelPlusHalfTheSpacingEachSide() {
        // Same row as above: scaled widths 50 / 100 / 50, spacing 10, selected label centered on 200.
        val edges = tabSlotEdges(
            widths = listOf(100, 100, 100),
            spacing = 10f,
            minScale = 0.5f,
            position = 1f,
            rowWidth = 400,
        )
        assertEquals(listOf(85, 145, 255, 315), edges)
        assertEquals(emptyList<Int>(), tabSlotEdges(emptyList(), 10f, 0.5f, 0f, 400))
    }

    @Test
    fun labelInsetCentersTheUnscaledLabelInItsSlot() {
        assertEquals(5, labelInset(slotWidth = 110, labelWidth = 100))
        assertEquals(-20, labelInset(slotWidth = 60, labelWidth = 100))
    }

    @Test
    fun singleTabIsCenteredAndEmptyRowYieldsNoEdges() {
        // One full-size tab: a 110 px slot (100 + 5 + 5) centered on 200.
        val edges = tabSlotEdges(listOf(100), 10f, 0.5f, 0f, 400)
        assertEquals(listOf(145, 255), edges)
        assertEquals(listOf(150), labelLefts(listOf(100), edges))
        assertEquals(emptyList<Int>(), tabSlotEdges(emptyList(), 10f, 0.5f, 0f, 400))
    }

    /**
     * Left edge (px, LTR) of each unscaled label as the row renders it: the slot start plus [labelInset], the same
     * composition `Modifier.tabSlot` places.
     */
    private fun labelLefts(widths: List<Int>, edges: List<Int>): List<Int> =
        widths.mapIndexed { index, width -> edges[index] + labelInset(edges[index + 1] - edges[index], width) }
}
