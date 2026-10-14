package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.PlaybackQueueDao
import com.rolla.musicplayer.core.database.entity.PlaybackQueueEntity
import com.rolla.musicplayer.core.database.entity.PlaybackStateEntity
import com.rolla.musicplayer.core.model.SavedPlayback
import com.rolla.musicplayer.core.model.SavedPlaybackState
import javax.inject.Inject

class PlaybackStateRepositoryImpl @Inject constructor(
    private val dao: PlaybackQueueDao,
) : PlaybackStateRepository {

    override suspend fun load(): SavedPlayback? {
        val snapshot = dao.loadSnapshot()
        val state = snapshot.state ?: return null
        val saved = restoredPlayback(snapshot.queue, state)
        if (saved == null) dao.clear()
        return saved
    }

    override suspend fun saveQueue(songIds: List<String>, shuffleOrder: List<Int>, state: SavedPlaybackState) {
        require(shuffleOrder.sorted() == songIds.indices.toList()) { "shuffleOrder must be a permutation of the slots" }
        val ranks = IntArray(songIds.size)
        shuffleOrder.forEachIndexed { rank, slot -> ranks[slot] = rank }
        val rows = songIds.mapIndexed { slot, songId -> PlaybackQueueEntity(slot, songId, ranks[slot]) }
        dao.replaceQueue(rows, state.toEntity())
    }

    override suspend fun saveState(state: SavedPlaybackState) = dao.upsertState(state.toEntity())

    override suspend fun clear() = dao.clear()

    private fun SavedPlaybackState.toEntity() = PlaybackStateEntity(
        currentSlot = currentIndex,
        positionMs = positionMs,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode.name,
        savedAt = System.currentTimeMillis(),
    )
}
