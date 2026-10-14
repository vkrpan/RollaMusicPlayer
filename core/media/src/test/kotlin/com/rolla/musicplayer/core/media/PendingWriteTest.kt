package com.rolla.musicplayer.core.media

import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.SavedPlaybackState
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingWriteTest {

    private val older = SavedPlaybackState(0, 1_000L, shuffleEnabled = false, RepeatMode.OFF)
    private val newer = SavedPlaybackState(2, 9_000L, shuffleEnabled = true, RepeatMode.ALL)
    private val queue = PendingWrite.Queue(listOf("a", "b", "c"), listOf(2, 0, 1), older)

    // A newer state folds into a pending queue write. Mutation: State replaces the Queue → RED.
    @Test
    fun aStateFoldsIntoAPendingQueue() {
        assertEquals(queue.copy(state = newer), queue.mergedWith(PendingWrite.State(newer)))
    }

    // Mutation: drop the Clear branch, so a State replaces a pending Clear → RED.
    @Test
    fun aStateNeverReplacesAPendingClear() {
        assertEquals(PendingWrite.Clear, PendingWrite.Clear.mergedWith(PendingWrite.State(newer)))
    }

    // Otherwise the latest write wins. Mutation: keep `this` whenever it is non-null → RED.
    @Test
    fun otherwiseTheLatestWriteWins() {
        assertEquals(queue, PendingWrite.State(newer).mergedWith(queue))
        assertEquals(PendingWrite.Clear, queue.mergedWith(PendingWrite.Clear))
        assertEquals(PendingWrite.State(newer), null.mergedWith(PendingWrite.State(newer)))
    }
}
