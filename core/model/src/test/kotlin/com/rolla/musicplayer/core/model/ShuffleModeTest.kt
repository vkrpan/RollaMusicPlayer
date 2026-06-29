package com.rolla.musicplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShuffleModeTest {

    @Test
    fun declarationOrderIsOffThenOn() {
        assertEquals(
            listOf(ShuffleMode.OFF, ShuffleMode.ON),
            ShuffleMode.entries.toList(),
        )
    }

    @Test
    fun valueOfOffReturnsOff() {
        assertEquals(ShuffleMode.OFF, ShuffleMode.valueOf("OFF"))
    }

    @Test
    fun valueOfOnReturnsOn() {
        assertEquals(ShuffleMode.ON, ShuffleMode.valueOf("ON"))
    }
}
