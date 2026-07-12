package com.rolla.musicplayer.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class ClampSeekPositionTest {

    @Test
    fun `skip back clamps at zero when delta would go negative`() {
        val result = clampSeekPosition(currentPositionMs = 5_000L, deltaMs = -15_000L, durationMs = 180_000L)

        assertEquals(0L, result)
    }

    @Test
    fun `skip forward clamps at duration when delta would exceed it`() {
        val result = clampSeekPosition(currentPositionMs = 170_000L, deltaMs = 15_000L, durationMs = 180_000L)

        assertEquals(180_000L, result)
    }

    @Test
    fun `skip back within range subtracts the delta`() {
        val result = clampSeekPosition(currentPositionMs = 45_000L, deltaMs = -15_000L, durationMs = 180_000L)

        assertEquals(30_000L, result)
    }

    @Test
    fun `skip forward within range adds the delta`() {
        val result = clampSeekPosition(currentPositionMs = 45_000L, deltaMs = 15_000L, durationMs = 180_000L)

        assertEquals(60_000L, result)
    }

    @Test
    fun `exactly at zero skipping back stays at zero`() {
        val result = clampSeekPosition(currentPositionMs = 0L, deltaMs = -15_000L, durationMs = 180_000L)

        assertEquals(0L, result)
    }

    @Test
    fun `exactly at duration skipping forward stays at duration`() {
        val result = clampSeekPosition(currentPositionMs = 180_000L, deltaMs = 15_000L, durationMs = 180_000L)

        assertEquals(180_000L, result)
    }

    @Test
    fun `zero duration returns zero regardless of position or delta`() {
        val result = clampSeekPosition(currentPositionMs = 5_000L, deltaMs = 15_000L, durationMs = 0L)

        assertEquals(0L, result)
    }

    @Test
    fun `negative duration defensively returns zero`() {
        val result = clampSeekPosition(currentPositionMs = 5_000L, deltaMs = -15_000L, durationMs = -1L)

        assertEquals(0L, result)
    }

    @Test
    fun `negative current position is coerced up into the valid range`() {
        // Defensive bound: PlaybackStateHolder.positionMs is always coerced non-negative upstream,
        // so this input shouldn't occur in practice, but clampSeekPosition's own coerceIn must not
        // propagate a negative snapshot into a negative seek target.
        val result = clampSeekPosition(currentPositionMs = -5_000L, deltaMs = 15_000L, durationMs = 180_000L)

        assertEquals(10_000L, result)
    }

    @Test
    fun `negative current position combined with a further negative delta clamps at zero`() {
        val result = clampSeekPosition(currentPositionMs = -5_000L, deltaMs = -15_000L, durationMs = 180_000L)

        assertEquals(0L, result)
    }
}
