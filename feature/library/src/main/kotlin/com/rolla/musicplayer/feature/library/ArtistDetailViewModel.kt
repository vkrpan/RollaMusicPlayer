package com.rolla.musicplayer.feature.library

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.ArtistRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Full-screen UI state for the artist detail screen.
 *
 * `artist == null && !isLoading` is the not-found state — the artist's last song was removed (or
 * the artist otherwise disappeared) between the user navigating here and the first emission
 * landing, since [ArtistRepository.observeArtist] derives the artist purely from its songs. The
 * screen should show an empty/not-found treatment in that case rather than a permanent spinner.
 */
@Immutable
data class ArtistDetailUiState(
    val isLoading: Boolean = true,
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val songs: List<Song> = emptyList(),
)

/**
 * ViewModel backing the artist detail screen, reached via `ArtistDetail(artistName: String)`.
 * That route lives in `:app` and is intentionally never imported here (`:feature:*` may only
 * depend on `:core:*`); instead [artistName] is read directly off [SavedStateHandle] by its raw
 * property-name key, exactly as `AlbumDetailViewModel` reads `albumId`. Like `albumId`,
 * `artistName` is non-nullable on the route, so it is required here rather than defaulted.
 */
@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val artistRepository: ArtistRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val artistName: String = checkNotNull(savedStateHandle["artistName"]) {
        "ArtistDetailViewModel requires a non-null artistName SavedStateHandle argument"
    }

    val uiState: StateFlow<ArtistDetailUiState> = combine(
        artistRepository.observeArtist(artistName),
        artistRepository.observeArtistAlbums(artistName),
        artistRepository.observeArtistSongs(artistName),
    ) { artist, albums, songs ->
        ArtistDetailUiState(isLoading = false, artist = artist, albums = albums, songs = songs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = ArtistDetailUiState(),
    )

    init {
        playbackController.connect()
    }

    /** Plays all of the artist's songs from the start, in list order. No-op if there are none. */
    fun onPlayClick() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        playbackController.setShuffle(ShuffleMode.OFF)
        playbackController.playAll(songs, startIndex = 0)
    }

    /** Shuffles all of the artist's songs. No-op if there are none. */
    fun onShuffleClick() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        playbackController.setShuffle(ShuffleMode.ON)
        playbackController.playAll(songs, startIndex = 0)
    }

    /**
     * Plays the artist's songs as the queue, starting at [song]. If [song] is no longer part of
     * the current list (e.g. tapped just as it was removed), falls back to playing just that song
     * on its own rather than guessing a queue position.
     */
    fun onSongClick(song: Song) {
        val songs = uiState.value.songs
        val index = songs.indexOfFirst { it.id == song.id }
        if (index == -1) {
            playbackController.play(song)
        } else {
            playbackController.playAll(songs, startIndex = index)
        }
    }
}
