package com.rolla.musicplayer.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [transportStarts] (NowPlayingScreen.kt), in px at 1 px/dp: five boxes 48, 48, 64, 48, 48 wide, the edge boxes
 * npControlEdgeInset (19) from the edges and previous/next centred npTransportInnerCentre (111) from the nearer edge.
 */
class TransportStartsTest {

    // The 360 dp reference: shuffle at 19, previous centred at 111 (87), play centred (148), next centred 111 from the
    // end (225), repeat 19 from the end (293). Trivial stub (all zeros, or an even spread) → RED.
    @Test
    fun wideRowTakesTheReferencePositions() {
        assertEquals("starts on a 360 px row", listOf(19, 87, 148, 225, 293), transportStarts(360, WIDTHS, EDGE, INNER))
    }

    // On 300 px the reference positions overlap (previous ends at 135, play starts at 118), so the five spread evenly,
    // as SpaceEvenly: gap (300 − 256) / 6 = 7. Mutation: drop the fallback (always the reference positions) → RED.
    @Test
    fun narrowRowSpreadsEvenly() {
        assertEquals("starts on a 300 px row", listOf(7, 62, 117, 188, 243), transportStarts(300, WIDTHS, EDGE, INNER))
    }

    private companion object {
        val WIDTHS = listOf(48, 48, 64, 48, 48)
        const val EDGE = 19
        const val INNER = 111
    }
}
