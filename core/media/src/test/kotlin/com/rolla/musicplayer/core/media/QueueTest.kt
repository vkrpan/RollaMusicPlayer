package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueTest {

    private fun item(index: Int) = QueueItem(index, "id$index", "T$index", "A$index", "")

    // Mutation: start the walk at 0 instead of firstIndex → [0, 1, 2] → RED.
    @Test
    fun walksFromFirstIndexFollowingNext() {
        val shuffled = intArrayOf(2, 0, 1)
        val next = { i: Int ->
            shuffled.indexOf(i).let { if (it == shuffled.lastIndex) C.INDEX_UNSET else shuffled[it + 1] }
        }
        assertEquals(listOf(2, 0, 1), playbackOrder(count = 3, firstIndex = 2, nextIndex = next))
    }

    // Mutation: delete the `order.size < count` guard → the walk never ends → RED by timeout.
    @Test(timeout = 2_000)
    fun aCyclicOrderStopsAfterCountSteps() {
        assertEquals(listOf(0, 1, 0), playbackOrder(count = 3, firstIndex = 0, nextIndex = { 1 - it }))
    }

    // Mutation: delete the `index in 0 until count` clause → 7 is taken until the size cap → [0, 7, 7] → RED.
    @Test
    fun aNextIndexOutsideTheTimelineEndsTheWalk() {
        assertEquals(listOf(0), playbackOrder(count = 3, firstIndex = 0, nextIndex = { 7 }))
    }

    @Test
    fun emptyTimelineGivesEmptyOrder() {
        assertEquals(emptyList<Int>(), playbackOrder(count = 0, firstIndex = C.INDEX_UNSET, nextIndex = { 0 }))
    }

    // Mutation: currentIndex = currentWindowIndex (a window index, not a list position) → 0 instead of 1 → RED.
    @Test
    fun currentIndexIsThePositionInPlaybackOrder() {
        val state = queueStateOf(order = listOf(2, 0, 1), currentWindowIndex = 0, item = ::item)
        assertEquals(listOf(2, 0, 1), state.items.map { it.index })
        assertEquals(1, state.currentIndex)
    }

    @Test
    fun aCurrentWindowOutsideTheOrderGivesMinusOne() {
        assertEquals(-1, queueStateOf(listOf(0, 1), currentWindowIndex = 5, item = ::item).currentIndex)
        assertEquals(-1, QueueState.EMPTY.currentIndex)
    }
}
