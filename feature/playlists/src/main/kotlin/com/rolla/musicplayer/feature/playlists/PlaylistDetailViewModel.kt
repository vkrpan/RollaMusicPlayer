package com.rolla.musicplayer.feature.playlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Full-screen UI state for either a user playlist or a smart playlist. */
@Immutable
data class PlaylistDetailUiState(
    val title: String = "",
    val songs: List<Song> = emptyList(),
    // true = user playlist (removable, backed by a real playlistId); false = smart/read-only.
    val isUserPlaylist: Boolean = false,
    val isLoading: Boolean = true,
)

/**
 * ViewModel backing `PlaylistDetailScreen`, which is shared by two distinct nav destinations —
 * a user playlist (`PlaylistDetail(playlistId: Long)`) and a smart playlist
 * (`SmartPlaylist(kind: String)`). Both routes live in `:app` and are intentionally never
 * imported here (`:feature:*` may only depend on `:core:*`); instead the relevant argument is
 * read directly off [SavedStateHandle] by its raw property-name key. Navigation Compose's
 * type-safe routes store each `@Serializable` property under its own key regardless of which
 * route type triggered creation, so exactly one of [playlistId] / [smartKind] will be non-null
 * for any given instance of this ViewModel.
 */
@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val playlistId: Long? = savedStateHandle.get<Long>("playlistId")
    private val smartKind: SmartPlaylistKind? =
        savedStateHandle.get<String>("kind")?.let { SmartPlaylistKind.valueOf(it) }

    val uiState: StateFlow<PlaylistDetailUiState> = buildUiStateFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = PlaylistDetailUiState(),
        )

    /** User-created playlists, exposed for the shared "Add to playlist" bottom sheet. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    private fun buildUiStateFlow(): Flow<PlaylistDetailUiState> {
        val id = playlistId
        val kind = smartKind
        return when {
            id != null -> combine(
                playlistRepository.observePlaylists(),
                playlistRepository.observePlaylistSongs(id),
            ) { playlists, songs ->
                PlaylistDetailUiState(
                    title = playlists.firstOrNull { it.id == id }?.name ?: "",
                    songs = songs,
                    isUserPlaylist = true,
                    isLoading = false,
                )
            }
            kind != null -> smartPlaylistSongs(kind).map { songs ->
                PlaylistDetailUiState(
                    title = smartPlaylistLabel(kind),
                    songs = songs,
                    isUserPlaylist = false,
                    isLoading = false,
                )
            }
            // Neither argument present — shouldn't happen given the two nav destinations, but
            // fail safe rather than crash on a force-unwrap.
            else -> flowOf(PlaylistDetailUiState(isLoading = false))
        }
    }

    private fun smartPlaylistSongs(kind: SmartPlaylistKind): Flow<List<Song>> = when (kind) {
        SmartPlaylistKind.RECENTLY_PLAYED -> songRepository.observeRecentlyPlayed()
        SmartPlaylistKind.FAVOURITES -> songRepository.observeFavourites()
        SmartPlaylistKind.MOST_PLAYED -> songRepository.observeMostPlayed()
        SmartPlaylistKind.RECENTLY_ADDED -> songRepository.observeRecentlyAdded()
    }

    // Mirrors the labels used in PlaylistsViewModel.smartPlaylists — kept as a small duplicated
    // literal set rather than a shared constant (see viewmodel-architect task notes).
    private fun smartPlaylistLabel(kind: SmartPlaylistKind): String = when (kind) {
        SmartPlaylistKind.RECENTLY_PLAYED -> "Recently played"
        SmartPlaylistKind.FAVOURITES -> "Favourites"
        SmartPlaylistKind.MOST_PLAYED -> "Most played"
        SmartPlaylistKind.RECENTLY_ADDED -> "Recently added"
    }

    fun playAll() {
        playbackController.playAll(uiState.value.songs, startIndex = 0)
    }

    fun shuffleAll() {
        playbackController.setShuffle(ShuffleMode.ON)
        playbackController.playAll(uiState.value.songs, startIndex = 0)
    }

    fun playSong(song: Song) {
        val songs = uiState.value.songs
        playbackController.playAll(songs, startIndex = songs.indexOf(song).coerceAtLeast(0))
    }

    fun removeSong(song: Song) {
        val id = playlistId ?: return
        viewModelScope.launch {
            playlistRepository.removeSong(id, song.id)
        }
    }

    fun reorder(orderedSongIds: List<String>) {
        val id = playlistId ?: return
        viewModelScope.launch {
            playlistRepository.reorder(id, orderedSongIds)
        }
    }

    fun renamePlaylist(name: String) {
        val id = playlistId ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, trimmed)
        }
    }

    fun deletePlaylist() {
        val id = playlistId ?: return
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
        }
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
