package com.rolla.musicplayer.feature.library

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.AlbumRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    /** Picks Shuffle's start track. Tests swap in a seeded [Random]; Hilt's constructor stays unchanged. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    init {
        playbackController.connect()
    }

    /** Plays the whole album from the start, in track order. No-op for an empty album. */
    fun onPlayClick() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        playbackController.setShuffle(ShuffleMode.OFF)
        playbackController.playAll(songs, startIndex = 0)
    }

    /** Shuffles the whole album from a random start track (spec §12). No-op for an empty album. */
    fun onShuffleClick() {
        val songs = uiState.value.songs
        if (songs.isEmpty()) return
        playbackController.setShuffle(ShuffleMode.ON)
        playbackController.playAll(songs, startIndex = random.nextInt(songs.size))
    }

    /**
     * Plays the album as the queue, starting at [song]. If [song] is no longer part of the
     * current album (e.g. tapped just as it was removed), falls back to playing just that song
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
