package com.rolla.musicplayer.feature.playlists

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Identifies one of the app's built-in, auto-populated smart playlists. */
enum class SmartPlaylistKind { RECENTLY_PLAYED, FAVOURITES, MOST_PLAYED, RECENTLY_ADDED }

/** The smart playlist's display name, shared by its Playlists-tab card and its detail screen title (UI copy). */
internal fun smartPlaylistLabel(kind: SmartPlaylistKind): String = when (kind) {
    SmartPlaylistKind.RECENTLY_ADDED -> "Recently added"
    SmartPlaylistKind.MOST_PLAYED -> "Most played"
    SmartPlaylistKind.RECENTLY_PLAYED -> "Recently played"
    SmartPlaylistKind.FAVOURITES -> "Favourite tracks"
}

/**
 * Display summary for a smart playlist card: a [kind] + label pulled from a [SongRepository]
 * flow, plus a small artwork preview (the first 4 distinct non-blank artwork URIs) for a collage-style thumbnail.
 */
@Immutable
data class SmartPlaylistSummary(
    val kind: SmartPlaylistKind,
    val label: String,
    val count: Int,
    val previewArtworkUris: List<String>,
)

/**
 * ViewModel for the Home pager's Playlists tab: the four smart-playlist summaries (spec §8.2 order) and the
 * user-created playlists, plus creating a playlist. Rename, delete and the detail screen belong to
 * PlaylistDetailViewModel.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
) : ViewModel() {

    val smartPlaylists: StateFlow<List<SmartPlaylistSummary>> = combine(
        songRepository.observeRecentlyPlayed(),
        songRepository.observeFavourites(),
        songRepository.observeMostPlayed(),
        songRepository.observeRecentlyAdded(),
    ) { recentlyPlayed, favourites, mostPlayed, recentlyAdded ->
        // Spec §8.2 card order.
        listOf(
            summary(SmartPlaylistKind.RECENTLY_ADDED, recentlyAdded),
            summary(SmartPlaylistKind.MOST_PLAYED, mostPlayed),
            summary(SmartPlaylistKind.RECENTLY_PLAYED, recentlyPlayed),
            summary(SmartPlaylistKind.FAVOURITES, favourites),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
        initialValue = emptyList(),
    )

    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    fun createPlaylist(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch { playlistRepository.createPlaylist(trimmed) }
    }

    private fun summary(kind: SmartPlaylistKind, songs: List<Song>) = SmartPlaylistSummary(
        kind = kind,
        label = smartPlaylistLabel(kind),
        count = songs.size,
        // Songs without art are skipped before capping, so later art still fills the card. artworkUri is per album,
        // so duplicates are dropped too: four tracks of one album would otherwise fill the collage with one cover.
        previewArtworkUris = songs.asSequence()
            .map { it.artworkUri }
            .filter { it.isNotBlank() }
            .distinct()
            .take(PREVIEW_ARTWORK_LIMIT)
            .toList(),
    )

    private companion object {
        const val PREVIEW_ARTWORK_LIMIT = 4
    }
}
