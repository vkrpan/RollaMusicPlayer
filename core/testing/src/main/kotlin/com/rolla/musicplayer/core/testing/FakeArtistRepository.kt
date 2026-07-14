package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.ArtistRepository
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory fake implementation of [ArtistRepository].
 *
 * Kept deliberately simple for a detail-screen ViewModel under test, which only ever observes a
 * single "current" artist: call [emitArtist]/[emitAlbums]/[emitSongs] from tests to push new
 * values through [observeArtist]/[observeArtistAlbums]/[observeArtistSongs] without touching a
 * database. All observe* functions return those same flows *regardless* of the `artistName`
 * passed in — every name received is recorded (in call order) in [requestedArtistNames] /
 * [requestedAlbumsArtistNames] / [requestedSongsArtistNames] so tests can assert the ViewModel
 * requested the expected name.
 */
class FakeArtistRepository : ArtistRepository {

    private val artistFlow = MutableStateFlow<Artist?>(null)
    private val albumsFlow = MutableStateFlow<List<Album>>(emptyList())
    private val songsFlow = MutableStateFlow<List<Song>>(emptyList())

    /** Every `artistName` passed to [observeArtist], in call order. */
    val requestedArtistNames = mutableListOf<String>()

    /** Every `artistName` passed to [observeArtistAlbums], in call order. */
    val requestedAlbumsArtistNames = mutableListOf<String>()

    /** Every `artistName` passed to [observeArtistSongs], in call order. */
    val requestedSongsArtistNames = mutableListOf<String>()

    /** Replaces the current in-memory artist, triggering a new emission on the flow. */
    fun emitArtist(artist: Artist?) {
        artistFlow.value = artist
    }

    /** Replaces the current in-memory album list, triggering a new emission on the flow. */
    fun emitAlbums(albums: List<Album>) {
        albumsFlow.value = albums
    }

    /** Replaces the current in-memory song list, triggering a new emission on the flow. */
    fun emitSongs(songs: List<Song>) {
        songsFlow.value = songs
    }

    override fun observeArtist(artistName: String): Flow<Artist?> {
        requestedArtistNames += artistName
        return artistFlow.asStateFlow()
    }

    override fun observeArtistAlbums(artistName: String): Flow<List<Album>> {
        requestedAlbumsArtistNames += artistName
        return albumsFlow.asStateFlow()
    }

    override fun observeArtistSongs(artistName: String): Flow<List<Song>> {
        requestedSongsArtistNames += artistName
        return songsFlow.asStateFlow()
    }
}
