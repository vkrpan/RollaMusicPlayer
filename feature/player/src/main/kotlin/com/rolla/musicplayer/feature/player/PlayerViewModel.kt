package com.rolla.musicplayer.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playbackController: PlaybackController,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val currentSong: StateFlow<Song?> = playbackController.currentSong
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), null)

    val isPlaying: StateFlow<Boolean> = playbackController.isPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), false)

    val positionMs: StateFlow<Long> = playbackController.positionMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), 0L)

    val durationMs: StateFlow<Long> = playbackController.durationMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), 0L)

    val shuffleMode: StateFlow<ShuffleMode> = playbackController.shuffleMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), ShuffleMode.OFF)

    val repeatMode: StateFlow<RepeatMode> = playbackController.repeatMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), RepeatMode.OFF)

    val isCurrentSongFavorite: StateFlow<Boolean> = combine(
        currentSong,
        songRepository.observeFavourites(),
    ) { song, favourites ->
        song != null && favourites.any { it.id == song.id }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), false)

    /** User-created playlists, exposed for the shared "Add to playlist" bottom sheet. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L), emptyList())

    init {
        playbackController.connect()
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun next() = playbackController.next()
    fun previous() = playbackController.previous()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun setShuffle(mode: ShuffleMode) = playbackController.setShuffle(mode)
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    fun toggleFavorite() {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            songRepository.toggleFavorite(song.id)
        }
    }

    /** Adds the currently playing song to an existing playlist. Safe no-op if nothing is playing. */
    fun addCurrentSongToPlaylist(playlistId: Long) {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            playlistRepository.addSongs(playlistId, listOf(song.id))
        }
    }

    /** Creates a new playlist with [name] and adds the currently playing song to it. */
    fun createPlaylistAndAddCurrentSong(name: String) {
        val song = currentSong.value ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(trimmed)
            playlistRepository.addSongs(id, listOf(song.id))
        }
    }
}
