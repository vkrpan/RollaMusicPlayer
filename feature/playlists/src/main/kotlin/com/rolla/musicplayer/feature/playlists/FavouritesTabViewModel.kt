package com.rolla.musicplayer.feature.playlists

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * The Home pager's Favourites tab (spec §8.2): the favourite tracks, Play / Shuffle / play-from-row through the shared
 * PlaybackController helpers, and the "Add to playlist" actions of the per-track options sheet. [songs] is null until
 * Room answers (ruling 6); the actions treat that as an empty list.
 */
@HiltViewModel
class FavouritesTabViewModel @Inject constructor(
    songRepository: SongRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val songs: StateFlow<List<Song>?> = songRepository.observeFavourites()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = null,
        )

    /** User-created playlists, for the shared "Add to playlist" sheet. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    /** Picks Shuffle's start track. Tests swap in a seeded [Random]. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    init {
        playbackController.connect()
    }

    /** Plays the favourites as the queue, starting at the tapped [song] (Phase 3 ruling 5). */
    fun play(song: Song) {
        playbackController.playFrom(songs.value.orEmpty(), song)
    }

    /** Plays the favourites in order from the first. The helper no-ops on an empty list. */
    fun playAll() {
        playbackController.playAllInOrder(songs.value.orEmpty())
    }

    /** Shuffles the favourites from a random start track (spec §12). The helper no-ops on an empty list. */
    fun shuffleAll() {
        playbackController.shuffleAll(songs.value.orEmpty(), random)
    }

    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, listOf(songId)) }
    }

    fun createPlaylistAndAddSong(name: String, songId: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(trimmed)
            playlistRepository.addSongs(id, listOf(songId))
        }
    }
}
