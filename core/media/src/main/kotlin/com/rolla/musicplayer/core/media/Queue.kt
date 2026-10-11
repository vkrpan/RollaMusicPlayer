package com.rolla.musicplayer.core.media

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline

/** One queue entry (spec §11). [index] is the Media3 window index: pass it to PlaybackController.skipToQueueIndex. */
data class QueueItem(
    val index: Int,
    val mediaId: String,
    val title: String,
    val artist: String,
    val artworkUri: String,
)

/**
 * The queue in playback order: shuffle order when shuffle is on (spec §11). [currentIndex] is the current item's
 * position in [items], not a window index, or -1 when nothing is current.
 */
data class QueueState(val items: List<QueueItem> = emptyList(), val currentIndex: Int = -1) {
    companion object {
        val EMPTY = QueueState()
    }
}

/**
 * Window indices in playback order, from [firstIndex] following [nextIndex] until it leaves `0 until count` (which
 * also covers [C.INDEX_UNSET], -1). Stops after [count] entries, so a malformed order can never loop forever.
 */
internal fun playbackOrder(count: Int, firstIndex: Int, nextIndex: (Int) -> Int): List<Int> {
    val order = ArrayList<Int>(count.coerceAtLeast(0))
    var index = firstIndex
    while (index in 0 until count && order.size < count) {
        order += index
        index = nextIndex(index)
    }
    return order
}

internal fun queueStateOf(order: List<Int>, currentWindowIndex: Int, item: (Int) -> QueueItem): QueueState =
    QueueState(items = order.map(item), currentIndex = order.indexOf(currentWindowIndex))

/** Reads this player's timeline as a [QueueState]. Call it on the player's application thread. */
internal fun Player.queueState(): QueueState {
    val timeline = currentTimeline
    if (timeline.isEmpty) return QueueState.EMPTY
    val shuffle = shuffleModeEnabled
    val order = playbackOrder(
        count = timeline.windowCount,
        firstIndex = timeline.getFirstWindowIndex(shuffle),
        nextIndex = { timeline.getNextWindowIndex(it, Player.REPEAT_MODE_OFF, shuffle) },
    )
    val window = Timeline.Window()
    return queueStateOf(order, currentMediaItemIndex) { index ->
        val mediaItem = timeline.getWindow(index, window).mediaItem
        val metadata = mediaItem.mediaMetadata
        QueueItem(
            index = index,
            mediaId = mediaItem.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            artworkUri = metadata.artworkUri?.toString().orEmpty(),
        )
    }
}
