package com.rolla.musicplayer.feature.library

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * State and actions for the Home pager's Tracks tab. The library sync and its ScanState live in the app shell's
 * HomeViewModel (the permission gate wraps the whole pager), so this ViewModel only reads the indexed library.
 */
@HiltViewModel
class TracksViewModel @Inject constructor(
    songRepository: SongRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    val songs: StateFlow<List<Song>> = songRepository.observeSongs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    /** User-created playlists, exposed for the shared "Add to playlist" bottom sheet. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    /** Picks Shuffle's start track. Tests swap in a seeded [Random]; Hilt's constructor stays unchanged. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    init {
        playbackController.connect()
    }

    fun play(song: Song) {
        playbackController.play(song)
    }

    /**
     * Plays the whole library in order from the first track. A no-op for an empty library. [songs] is
     * WhileSubscribed and TracksTab collects it while visible, so its value is current when the button is tapped.
     */
    fun playAll() {
        val list = songs.value
        if (list.isEmpty()) return
        playbackController.setShuffle(ShuffleMode.OFF)
        playbackController.playAll(list, startIndex = 0)
    }

    /** Shuffles the whole library from a random start track (spec §12). A no-op for an empty library. */
    fun shuffleAll() {
        val list = songs.value
        if (list.isEmpty()) return
        playbackController.setShuffle(ShuffleMode.ON)
        playbackController.playAll(list, startIndex = random.nextInt(list.size))
    }

    /** Adds an already-tapped song to an existing playlist, via the shared "Add to playlist" sheet. */
    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongs(playlistId, listOf(songId))
        }
    }

    /** Creates a new playlist with [name] and immediately adds [songId] to it. */
    fun createPlaylistAndAddSong(name: String, songId: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val id = playlistRepository.createPlaylist(trimmed)
            playlistRepository.addSongs(id, listOf(songId))
        }
    }
}
