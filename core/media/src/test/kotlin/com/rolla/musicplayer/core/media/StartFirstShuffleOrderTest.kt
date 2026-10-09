package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

private const val COUNT = 10
private const val SEED = 42L
private const val PLAYER_COUNT = 5
private const val CURRENT = 2
private const val OTHER_FIRST = 4

@androidx.annotation.OptIn(UnstableApi::class)
class StartFirstShuffleOrderTest {

    // --- startFirstShuffleOrder ---------------------------------------------------------------

    @Test
    fun `order is a permutation with the start item first, for every start index`() {
        repeat(COUNT) { start ->
            val order = startFirstShuffleOrder(COUNT, start, Random(SEED + start))

            assertEquals(start, order.first())
            assertEquals((0 until COUNT).toList(), order.sorted())
        }
    }

    @Test
    fun `order is deterministic for a given seed`() {
        val first = startFirstShuffleOrder(COUNT, CURRENT, Random(SEED))
        val second = startFirstShuffleOrder(COUNT, CURRENT, Random(SEED))

        assertArrayEquals(first, second)
    }

    @Test
    fun `empty and single-item lists`() {
        assertArrayEquals(IntArray(0), startFirstShuffleOrder(0, 0, Random(SEED)))
        assertArrayEquals(intArrayOf(0), startFirstShuffleOrder(1, 0, Random(SEED)))
    }

    @Test
    fun `out-of-range start is treated as no start item - still a full permutation`() {
        listOf(C.INDEX_UNSET, COUNT).forEach { start ->
            val order = startFirstShuffleOrder(COUNT, start, Random(SEED))

            assertEquals((0 until COUNT).toList(), order.sorted())
        }
    }

    // --- startFirstShuffleOrderToApply (the wiring decision) ---------------------------------

    @Test
    fun `shuffle off - nothing to apply`() {
        assertNull(startFirstShuffleOrderToApply(false, PLAYER_COUNT, CURRENT, OTHER_FIRST, Random(SEED)))
    }

    @Test
    fun `order already starts with the current item - nothing to apply`() {
        assertNull(startFirstShuffleOrderToApply(true, PLAYER_COUNT, CURRENT, CURRENT, Random(SEED)))
    }

    @Test
    fun `invalid current index or empty playlist - nothing to apply`() {
        assertNull(startFirstShuffleOrderToApply(true, 0, C.INDEX_UNSET, C.INDEX_UNSET, Random(SEED)))
        assertNull(startFirstShuffleOrderToApply(true, PLAYER_COUNT, PLAYER_COUNT, 0, Random(SEED)))
    }

    @Test
    fun `shuffle on and current item not first - a start-first permutation of every item`() {
        val order = startFirstShuffleOrderToApply(true, PLAYER_COUNT, CURRENT, OTHER_FIRST, Random(SEED))

        assertNotNull(order)
        assertEquals(CURRENT, order!!.first())
        assertEquals((0 until PLAYER_COUNT).toList(), order.sorted())
    }

    // --- StartFirstShuffleEnforcer (listener -> ExoPlayer.setShuffleOrder) -------------------

    private fun player(shuffleEnabled: Boolean = true, firstShuffled: Int = OTHER_FIRST): ExoPlayer {
        val timeline = mockk<Timeline> {
            every { windowCount } returns PLAYER_COUNT
            every { getFirstWindowIndex(true) } returns firstShuffled
        }
        return mockk(relaxed = true) {
            every { currentTimeline } returns timeline
            every { shuffleModeEnabled } returns shuffleEnabled
            every { currentMediaItemIndex } returns CURRENT
        }
    }

    private fun ShuffleOrder.traversal(): List<Int> =
        generateSequence(firstIndex.takeIf { it != C.INDEX_UNSET }) { index ->
            getNextIndex(index).takeIf { it != C.INDEX_UNSET }
        }.toList()

    @Test
    fun `shuffle turning on applies an order that starts at the current item and reaches every item`() {
        val player = player()
        val applied = slot<ShuffleOrder>()

        StartFirstShuffleEnforcer(player, Random(SEED)).onShuffleModeEnabledChanged(true)

        verify(exactly = 1) { player.setShuffleOrder(capture(applied)) }
        val traversal = applied.captured.traversal()
        assertEquals(CURRENT, traversal.first())
        assertEquals((0 until PLAYER_COUNT).toList(), traversal.sorted())
    }

    @Test
    fun `playlist replacement while shuffling applies a start-first order`() {
        val player = player()
        val applied = slot<ShuffleOrder>()

        StartFirstShuffleEnforcer(player, Random(SEED))
            .onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        verify(exactly = 1) { player.setShuffleOrder(capture(applied)) }
        assertEquals(CURRENT, applied.captured.firstIndex)
    }

    @Test
    fun `shuffle turning off, auto or seek transitions, and an already start-first order apply nothing`() {
        val player = player()
        val enforcer = StartFirstShuffleEnforcer(player, Random(SEED))
        enforcer.onShuffleModeEnabledChanged(false)
        enforcer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        enforcer.onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)

        val startFirst = player(firstShuffled = CURRENT)
        StartFirstShuffleEnforcer(startFirst, Random(SEED)).onShuffleModeEnabledChanged(true)

        val shuffleOff = player(shuffleEnabled = false)
        StartFirstShuffleEnforcer(shuffleOff, Random(SEED))
            .onMediaItemTransition(null, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)

        verify(exactly = 0) { player.setShuffleOrder(any()) }
        verify(exactly = 0) { startFirst.setShuffleOrder(any()) }
        verify(exactly = 0) { shuffleOff.setShuffleOrder(any()) }
    }
}
