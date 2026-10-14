package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.PlaybackStateRepository
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory fake [PlaybackStateRepository]. [saved] is what [load] returns. [loadGate] (when set) holds every [load]
 * until completed, and [loadFailure] makes the next [load] throw. Writes are recorded in [queueWrites], [stateWrites]
 * and [clearCount]; [failNextWrite] makes the next save throw once.
 */
class FakePlaybackStateRepository : PlaybackStateRepository {

    data class QueueWrite(val songIds: List<String>, val shuffleOrder: List<Int>, val state: SavedPlaybackState)

    var saved: SavedPlayback? = null
    var loadGate: CompletableDeferred<Unit>? = null
    var loadFailure: Throwable? = null
    var failNextWrite: Throwable? = null

    var loadCount = 0
        private set
    var clearCount = 0
        private set
    val queueWrites = mutableListOf<QueueWrite>()
    val stateWrites = mutableListOf<SavedPlaybackState>()

    override suspend fun load(): SavedPlayback? {
        loadCount++
        loadGate?.await()
        loadFailure?.let {
            loadFailure = null
            throw it
        }
        return saved
    }

    override suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState) {
        throwIfFailing()
        queueWrites += QueueWrite(songIds, shuffleOrder, state)
    }

    override suspend fun saveState(state: SavedPlaybackState) {
        throwIfFailing()
        stateWrites += state
    }

    override suspend fun clear() {
        clearCount++
        saved = null
    }

    private fun throwIfFailing() {
        failNextWrite?.let {
            failNextWrite = null
            throw it
        }
    }
}
