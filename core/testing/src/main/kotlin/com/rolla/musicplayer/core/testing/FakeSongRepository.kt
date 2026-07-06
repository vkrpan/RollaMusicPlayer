package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/**
 * In-memory fake implementation of [SongRepository].
 *
 * Call [emit] from tests to push a new list of [Song] objects through [observeSongs] without
 * touching a database or the media scanner. This lets ViewModel tests control exactly what
 * data the ViewModel receives.
 */
class FakeSongRepository : SongRepository {

    private val songsFlow = MutableStateFlow<List<Song>>(emptyList())

    /** Replaces the current in-memory song list, triggering a new emission on the flow. */
    fun emit(songs: List<Song>) {
        songsFlow.value = songs
    }

    override fun observeSongs(): Flow<List<Song>> = songsFlow.asStateFlow()

    override fun observeSong(songId: String): Flow<Song?> =
        songsFlow.map { songs -> songs.firstOrNull { it.id == songId } }

    override suspend fun toggleFavorite(songId: String) {
        // Song has no isFavorite field of its own (see model-vocabulary / this phase's scope) —
        // fakes have nothing to flip, this is a no-op provided only to satisfy the interface.
    }

    override suspend fun recordPlaybackStarted(songId: String) {
        // Song has no playCount/lastPlayed fields of its own (see model-vocabulary / this phase's
        // scope) — fakes have nothing to update, this is a no-op provided only to satisfy the interface.
    }

    override fun observeRecentlyAdded(): Flow<List<Song>> = songsFlow.asStateFlow()

    override fun observeRecentlyPlayed(): Flow<List<Song>> = songsFlow.asStateFlow()

    override fun observeMostPlayed(): Flow<List<Song>> = songsFlow.asStateFlow()

    override fun observeFavourites(): Flow<List<Song>> = songsFlow.asStateFlow()
}
