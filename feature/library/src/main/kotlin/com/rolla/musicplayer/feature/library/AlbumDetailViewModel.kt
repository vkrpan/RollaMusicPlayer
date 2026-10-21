package com.rolla.musicplayer.feature.library

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.AlbumRepository
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.createPlaylistWithSong
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * Full-screen UI state for the album detail screen.
 *
 * `album == null && !isLoading` is the not-found state — the album's last song was removed (or
 * the whole album deleted) between the user navigating here and the first emission landing, since
 * [AlbumRepository.observeAlbum] derives the album purely from its songs. The screen should show
 * an empty/not-found treatment in that case rather than a permanent spinner.
 */
@Immutable
data class AlbumDetailUiState(
    val isLoading: Boolean = true,
    val album: Album? = null,
    val songs: List<Song> = emptyList(),
)

/**
 * ViewModel backing the album detail screen, reached via `AlbumDetail(albumId: Long)`. That route
 * lives in `:app` and is intentionally never imported here (`:feature:*` may only depend on
 * `:core:*`); instead [albumId] is read directly off [SavedStateHandle] by its raw property-name
 * key, exactly as `PlaylistDetailViewModel` reads `playlistId`/`kind`. Unlike the playlist route,
 * `albumId` is non-nullable on the route, so it is required here rather than defaulted.
 */
@HiltViewModel
class AlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val albumRepository: AlbumRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val albumId: Long = checkNotNull(savedStateHandle["albumId"]) {
        "AlbumDetailViewModel requires a non-null albumId SavedStateHandle argument"
    }

    val uiState: StateFlow<AlbumDetailUiState> = combine(
        albumRepository.observeAlbum(albumId),
        albumRepository.observeAlbumSongs(albumId),
    ) { album, songs ->
        AlbumDetailUiState(isLoading = false, album = album, songs = songs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = AlbumDetailUiState(),
    )

    /** User-created playlists, for the shared "Add to playlist" sheet. */
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

    /** Plays the whole album from the start, in track order. No-op for an empty album. */
    fun onPlayClick() {
        playbackController.playAllInOrder(uiState.value.songs)
    }

    /** Shuffles the whole album from a random start track (spec §12). No-op for an empty album. */
    fun onShuffleClick() {
        playbackController.shuffleAll(uiState.value.songs, random)
    }

    /**
     * Plays the album as the queue, starting at [song]. If [song] is no longer part of the
     * current album (e.g. tapped just as it was removed), [PlaybackController.playFrom] plays
     * just that song on its own rather than guessing a queue position.
     */
    fun onSongClick(song: Song) {
        playbackController.playFrom(uiState.value.songs, song)
    }

    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, listOf(songId)) }
    }

    /** "New playlist" from the options sheet; a blank name writes nothing (Phase 6a ruling 7). */
    fun createPlaylistAndAddSong(name: String, songId: String) {
        viewModelScope.launch { playlistRepository.createPlaylistWithSong(name, songId) }
    }
}
