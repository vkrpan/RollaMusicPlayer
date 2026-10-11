package com.rolla.musicplayer.feature.player

import androidx.lifecycle.ViewModel
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.media.QueueItem
import com.rolla.musicplayer.core.media.QueueState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Backs the queue sheet (spec §11): the controller's queue as-is, plus tap-to-jump. */
@HiltViewModel
class QueueViewModel @Inject constructor(private val playbackController: PlaybackController) : ViewModel() {
    val queue: StateFlow<QueueState> = playbackController.queue

    init {
        playbackController.connect()
    }

    /** Plays [item]. Seeks by its Media3 window index ([QueueItem.index]), never by its position in the list. */
    fun skipTo(item: QueueItem) = playbackController.skipToQueueIndex(item.index)
}
