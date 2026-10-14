package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState

/** The saved playback queue (queue-restore spec §5.4). Every call is main-safe. */
interface PlaybackStateRepository {

    /** The saved queue, ready to restore, or null when nothing is saved or no saved song survives (then it clears). */
    suspend fun load(): SavedPlayback?

    /**
     * Replaces the saved queue: [songIds] in queue order, [shuffleOrder] a permutation of `songIds.indices`, and
     * [state] with `currentIndex` indexing [songIds].
     */
    suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState)

    /** Updates only the position, current index and modes of the saved queue. */
    suspend fun saveState(state: SavedPlaybackState)

    suspend fun clear()
}
