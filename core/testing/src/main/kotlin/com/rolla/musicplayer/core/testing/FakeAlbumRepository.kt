package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.AlbumRepository
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [AlbumRepository].
 *
 * Kept deliberately simple for a detail-screen ViewModel under test, which only ever observes a
 * single "current" album: call [emitAlbum]/[emitSongs] from tests to push new values through
 * [observeAlbum]/[observeAlbumSongs] without touching a database. Both observe* functions return
 * those same flows *regardless* of the `albumId` passed in — every id received is recorded (in
 * call order) in [requestedAlbumIds]/[requestedSongsAlbumIds] so tests can assert the ViewModel
 * requested the expected id.
 */
class FakeAlbumRepository : AlbumRepository {

    private val albumFlow = MutableStateFlow<Album?>(null)
    private val songsFlow = MutableStateFlow<List<Song>>(emptyList())

    /** Every `albumId` passed to [observeAlbum], in call order. */
    val requestedAlbumIds = mutableListOf<Long>()

    /** Every `albumId` passed to [observeAlbumSongs], in call order. */
    val requestedSongsAlbumIds = mutableListOf<Long>()

    /** Replaces the current in-memory album, triggering a new emission on the flow. */
    fun emitAlbum(album: Album?) {
        albumFlow.value = album
    }

    /** Replaces the current in-memory song list, triggering a new emission on the flow. */
    fun emitSongs(songs: List<Song>) {
        songsFlow.value = songs
    }

    override fun observeAlbum(albumId: Long): Flow<Album?> {
        requestedAlbumIds += albumId
        return albumFlow.asStateFlow()
    }

    override fun observeAlbumSongs(albumId: Long): Flow<List<Song>> {
        requestedSongsAlbumIds += albumId
        return songsFlow.asStateFlow()
    }
}
