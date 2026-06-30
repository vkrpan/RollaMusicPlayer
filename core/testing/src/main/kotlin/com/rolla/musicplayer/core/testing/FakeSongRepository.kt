package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

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
}
