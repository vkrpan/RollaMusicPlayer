package com.rolla.musicplayer.feature.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Identifies one of the app's built-in, auto-populated smart playlists. */
enum class SmartPlaylistKind { RECENTLY_PLAYED, FAVOURITES, MOST_PLAYED, RECENTLY_ADDED }

/**
 * Display summary for a smart playlist card: a [kind] + label pulled from a [SongRepository]
 * flow, plus a small artwork preview (at most 4 songs) for a collage-style thumbnail.
 */
data class SmartPlaylistSummary(
    val kind: SmartPlaylistKind,
    val label: String,
    val count: Int,
    val previewArtworkUris: List<String>,
)

/**
 * ViewModel for the Playlists tab. This step only *displays* smart-playlist summaries and
 * user-created playlists — creation/rename/delete and playlist-detail navigation are handled in
 * later steps.
 */
@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    playlistRepository: PlaylistRepository,
    songRepository: SongRepository,
) : ViewModel() {

    val smartPlaylists: StateFlow<List<SmartPlaylistSummary>> = combine(
        songRepository.observeRecentlyPlayed(),
        songRepository.observeFavourites(),
        songRepository.observeMostPlayed(),
        songRepository.observeRecentlyAdded(),
    ) { recentlyPlayed, favourites, mostPlayed, recentlyAdded ->
        listOf(
            SmartPlaylistSummary(
                kind = SmartPlaylistKind.RECENTLY_PLAYED,
                label = "Recently played",
                count = recentlyPlayed.size,
                previewArtworkUris = recentlyPlayed.take(PREVIEW_ARTWORK_LIMIT).map { it.artworkUri },
            ),
            SmartPlaylistSummary(
                kind = SmartPlaylistKind.FAVOURITES,
                label = "Favourites",
                count = favourites.size,
                previewArtworkUris = favourites.take(PREVIEW_ARTWORK_LIMIT).map { it.artworkUri },
            ),
            SmartPlaylistSummary(
                kind = SmartPlaylistKind.MOST_PLAYED,
                label = "Most played",
                count = mostPlayed.size,
                previewArtworkUris = mostPlayed.take(PREVIEW_ARTWORK_LIMIT).map { it.artworkUri },
            ),
            SmartPlaylistSummary(
                kind = SmartPlaylistKind.RECENTLY_ADDED,
                label = "Recently added",
                count = recentlyAdded.size,
                previewArtworkUris = recentlyAdded.take(PREVIEW_ARTWORK_LIMIT).map { it.artworkUri },
            ),
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

    private companion object {
        const val PREVIEW_ARTWORK_LIMIT = 4
    }
}
