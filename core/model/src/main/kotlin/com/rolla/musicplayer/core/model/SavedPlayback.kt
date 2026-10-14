package com.rolla.musicplayer.core.model

/** Where playback stood when it was saved. [currentIndex] indexes the owning [SavedPlayback.songs]. */
data class SavedPlaybackState(
    val currentIndex: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
)

/**
 * A restorable queue (queue-restore spec §5.4): [songs] in queue order, and [shuffleOrder], a permutation of
 * `songs.indices` giving the shuffled playback order. It is kept even while shuffle is off, so turning shuffle on
 * after a restore gives back the same order.
 */
data class SavedPlayback(
    val songs: List<Song>,
    val shuffleOrder: List<Int>,
    val state: SavedPlaybackState,
)
