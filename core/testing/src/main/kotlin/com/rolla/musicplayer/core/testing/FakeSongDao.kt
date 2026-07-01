package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.database.dao.SongDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory fake implementation of [SongDao] backed by a [MutableStateFlow].
 *
 * Call [emit] from tests to push a new list of entities through [observeAllSongs] without
 * touching a real Room database. [upsertSongs] and [deleteByMediaStoreIds] also mutate the
 * in-memory store so tests that exercise the scanner / indexer path stay self-consistent.
 */
class FakeSongDao : SongDao {

    private val songsFlow = MutableStateFlow<List<SongEntity>>(emptyList())

    /** Replaces the current in-memory song list, triggering a new emission on the flow. */
    fun emit(songs: List<SongEntity>) {
        songsFlow.value = songs
    }

    override fun observeAllSongs(): Flow<List<SongEntity>> = songsFlow.asStateFlow()

    override suspend fun upsertSongs(songs: List<SongEntity>) {
        songsFlow.update { current ->
            val updated = current.toMutableList()
            songs.forEach { incoming ->
                val idx = updated.indexOfFirst { it.id == incoming.id }
                if (idx >= 0) updated[idx] = incoming else updated.add(incoming)
            }
            updated
        }
    }

    override suspend fun deleteByMediaStoreIds(ids: List<Long>) {
        songsFlow.update { current -> current.filter { it.mediaStoreId !in ids } }
    }

    override suspend fun getAllSongs(): List<SongEntity> = songsFlow.value

    override suspend fun toggleFavorite(songId: String) {
        songsFlow.update { current ->
            current.map { if (it.id == songId) it.copy(isFavorite = !it.isFavorite) else it }
        }
    }

    override suspend fun recordPlaybackStarted(songId: String, timestamp: Long) {
        songsFlow.update { current ->
            current.map {
                if (it.id == songId) it.copy(playCount = it.playCount + 1, lastPlayed = timestamp) else it
            }
        }
    }

    override fun observeRecentlyAdded(): Flow<List<SongEntity>> =
        songsFlow.asStateFlow().map { list -> list.sortedByDescending { it.dateAdded } }

    override fun observeRecentlyPlayed(): Flow<List<SongEntity>> =
        songsFlow.asStateFlow().map { list ->
            list.filter { it.lastPlayed != null }.sortedByDescending { it.lastPlayed }
        }

    override fun observeMostPlayed(): Flow<List<SongEntity>> =
        songsFlow.asStateFlow().map { list ->
            list.filter { it.playCount > 0 }.sortedByDescending { it.playCount }
        }

    override fun observeFavourites(): Flow<List<SongEntity>> =
        songsFlow.asStateFlow().map { list ->
            list.filter { it.isFavorite }.sortedBy { it.title }
        }
}
