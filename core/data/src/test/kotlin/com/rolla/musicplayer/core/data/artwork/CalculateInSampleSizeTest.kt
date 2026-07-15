package com.rolla.musicplayer.core.data.artwork

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateInSampleSizeTest {

    @Test
    fun smallImageBelowTarget_returnsOne() {
        assertEquals(1, calculateInSampleSize(width = 1, height = 1, targetMaxEdge = TARGET))
    }

    @Test
    fun imageExactlyAtTarget_returnsOne() {
        assertEquals(1, calculateInSampleSize(width = TARGET, height = TARGET, targetMaxEdge = TARGET))
    }

    @Test
    fun imageAtTwiceTheTarget_returnsTwo() {
        assertEquals(2, calculateInSampleSize(width = TARGET * 2, height = TARGET * 2, targetMaxEdge = TARGET))
    }

    @Test
    fun imageAtFourTimesTheTarget_returnsFour() {
        assertEquals(4, calculateInSampleSize(width = TARGET * 4, height = TARGET * 4, targetMaxEdge = TARGET))
    }

    @Test
    fun nonSquareImage_isGovernedByTheLongerEdge() {
        assertEquals(4, calculateInSampleSize(width = 2048, height = 512, targetMaxEdge = TARGET))
    }

    @Test
    fun hugeImage_returnsTheCorrectPowerOfTwo() {
        assertEquals(32, calculateInSampleSize(width = 16384, height = 16384, targetMaxEdge = TARGET))
    }

    @Test
    fun zeroWidth_defensivelyReturnsOne() {
        assertEquals(1, calculateInSampleSize(width = 0, height = 512, targetMaxEdge = TARGET))
    }

    @Test
    fun negativeHeight_defensivelyReturnsOne() {
        assertEquals(1, calculateInSampleSize(width = 512, height = -10, targetMaxEdge = TARGET))
    }

    private companion object {
        const val TARGET = 512
    }
}
