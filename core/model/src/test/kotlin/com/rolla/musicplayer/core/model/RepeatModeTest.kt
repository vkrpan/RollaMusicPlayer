package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatModeTest {

    @Test
    fun declarationOrderIsOffOneThenAll() {
        assertEquals(
            listOf(RepeatMode.OFF, RepeatMode.ONE, RepeatMode.ALL),
            RepeatMode.entries.toList(),
        )
    }

    @Test
    fun valueOfOffReturnsOff() {
        assertEquals(RepeatMode.OFF, RepeatMode.valueOf("OFF"))
    }

    @Test
    fun valueOfOneReturnsOne() {
        assertEquals(RepeatMode.ONE, RepeatMode.valueOf("ONE"))
    }

    @Test
    fun valueOfAllReturnsAll() {
        assertEquals(RepeatMode.ALL, RepeatMode.valueOf("ALL"))
    }
}
