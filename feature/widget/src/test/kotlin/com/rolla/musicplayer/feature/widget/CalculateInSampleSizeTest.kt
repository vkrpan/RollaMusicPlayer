package com.rolla.musicplayer.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateInSampleSizeTest {

    @Test
    fun `small image below target returns one`() {
        assertEquals(1, calculateInSampleSize(width = 1, height = 1, targetMaxEdge = TARGET))
    }

    @Test
    fun `image exactly at target returns one`() {
        assertEquals(1, calculateInSampleSize(width = TARGET, height = TARGET, targetMaxEdge = TARGET))
    }

    @Test
    fun `image at twice the target returns two`() {
        assertEquals(2, calculateInSampleSize(width = TARGET * 2, height = TARGET * 2, targetMaxEdge = TARGET))
    }

    @Test
    fun `image at four times the target returns four`() {
        assertEquals(4, calculateInSampleSize(width = TARGET * 4, height = TARGET * 4, targetMaxEdge = TARGET))
    }

    @Test
    fun `non-square image is governed by the longer edge`() {
        assertEquals(4, calculateInSampleSize(width = 1024, height = 256, targetMaxEdge = TARGET))
    }

    @Test
    fun `huge image returns the correct power of two`() {
        assertEquals(32, calculateInSampleSize(width = 8192, height = 8192, targetMaxEdge = TARGET))
    }

    @Test
    fun `zero width defensively returns one`() {
        assertEquals(1, calculateInSampleSize(width = 0, height = 512, targetMaxEdge = TARGET))
    }

    @Test
    fun `negative height defensively returns one`() {
        assertEquals(1, calculateInSampleSize(width = 512, height = -10, targetMaxEdge = TARGET))
    }

    private companion object {
        const val TARGET = 256
    }
}
