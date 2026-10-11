package com.rolla.musicplayer.feature.player

import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.media.QueueItem
import com.rolla.musicplayer.core.media.QueueState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueViewModelTest {
    private val queueFlow = MutableStateFlow(QueueState.EMPTY)
    private val controller: PlaybackController = mockk(relaxed = true) { every { queue } returns queueFlow }

    // Mutation: pass item.index + 1 (or the list position) → RED.
    @Test
    fun skipToJumpsToTheItemsWindowIndex() {
        val vm = QueueViewModel(controller)
        vm.skipTo(QueueItem(index = 4, mediaId = "x", title = "", artist = "", artworkUri = ""))
        verify { controller.skipToQueueIndex(4) }
    }

    // Mutation: expose a stateIn copy that starts EMPTY instead of the controller's flow → RED.
    @Test
    fun queueIsTheControllersFlow() {
        val state = QueueState(listOf(QueueItem(0, "a", "A", "B", "")), 0)
        queueFlow.value = state
        assertEquals(state, QueueViewModel(controller).queue.value)
    }
}
