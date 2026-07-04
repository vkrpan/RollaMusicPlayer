package com.rolla.musicplayer.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    songRepository: SongRepository,
    private val libraryIndexer: LibraryIndexer,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

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

    init {
        playbackController.connect()
    }

    fun onPermissionGranted() {
        val current = _scanState.value
        if (current is ScanState.Scanning || current is ScanState.Done) return
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            _scanState.value = try {
                val result = libraryIndexer.sync()
                ScanState.Done(added = result.added, removed = result.removed)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                ScanState.Error(e.message ?: "Scan failed")
            }
        }
    }

    fun play(song: Song) {
        playbackController.play(song)
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
