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

    @Test
    fun `progress is exactly one when position equals duration`() {
        // Boundary case distinct from "position exceeds duration": the ratio is exactly 1f and
        // must pass through coerceIn's inclusive upper bound without needing to clamp anything.
        val state = MusicWidgetState(positionMs = 180_000L, durationMs = 180_000L)

        assertEquals(1f, state.progress)
    }

    @Test
    fun `progress is exactly zero when position is zero and duration is positive`() {
        // Boundary case distinct from "position is negative": the ratio is exactly 0f and must
        // pass through coerceIn's inclusive lower bound without needing to clamp anything.
        val state = MusicWidgetState(positionMs = 0L, durationMs = 180_000L)

        assertEquals(0f, state.progress)
    }
}
