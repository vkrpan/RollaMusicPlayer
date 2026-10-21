package com.rolla.musicplayer.feature.library

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.FolderRepository
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.createPlaylistWithSong
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.folderName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

/**
 * FolderDetail (spec §8.7), reached via `FolderDetail(folderPath: String)`. That route lives in `:app` and is never
 * imported here: [folderPath] is read off [SavedStateHandle] by its property name, as AlbumDetailViewModel reads
 * `albumId`. "" is the volume root. [songs] is null until Room answers; the actions treat that as an empty list. The
 * play and playlist actions are FavouritesTabViewModel's (Phase 4b ruling 8).
 */
@HiltViewModel
class FolderDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    folderRepository: FolderRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    private val folderPath: String = checkNotNull(savedStateHandle["folderPath"]) {
        "FolderDetailViewModel requires a folderPath SavedStateHandle argument"
    }

    /** The folder's display name, known from the route before Room answers. */
    val title: String = folderName(folderPath)

    val songs: StateFlow<List<Song>?> = folderRepository.observeFolderSongs(folderPath)
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

    /** Plays the folder as the queue, starting at the tapped [song] (Phase 3 ruling 5). */
    fun play(song: Song) {
        playbackController.playFrom(songs.value.orEmpty(), song)
    }

    /** Plays the folder in order from the first track. The helper no-ops on an empty list. */
    fun playAll() {
        playbackController.playAllInOrder(songs.value.orEmpty())
    }

    /** Shuffles the folder from a random start track (spec §12). The helper no-ops on an empty list. */
    fun shuffleAll() {
        playbackController.shuffleAll(songs.value.orEmpty(), random)
    }

    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, listOf(songId)) }
    }

    fun createPlaylistAndAddSong(name: String, songId: String) {
        viewModelScope.launch { playlistRepository.createPlaylistWithSong(name, songId) }
    }
}
