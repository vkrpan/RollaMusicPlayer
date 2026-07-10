package com.rolla.musicplayer.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicWidgetStateTest {

    @Test
    fun `progress is zero when duration is zero`() {
        val state = MusicWidgetState(positionMs = 5_000L, durationMs = 0L)

        assertEquals(0f, state.progress)
    }

    @Test
    fun `progress is zero when duration is negative`() {
        val state = MusicWidgetState(positionMs = 5_000L, durationMs = -1L)

        assertEquals(0f, state.progress)
    }

    @Test
    fun `progress is coerced to one when position exceeds duration`() {
        val state = MusicWidgetState(positionMs = 200_000L, durationMs = 100_000L)

        assertEquals(1f, state.progress)
    }

    @Test
    fun `progress is coerced to zero when position is negative`() {
        val state = MusicWidgetState(positionMs = -5_000L, durationMs = 100_000L)

        assertEquals(0f, state.progress)
    }

    @Test
    fun `progress reflects the position-to-duration ratio`() {
        val state = MusicWidgetState(positionMs = 25_000L, durationMs = 100_000L)

        assertEquals(0.25f, state.progress)
    }
}
