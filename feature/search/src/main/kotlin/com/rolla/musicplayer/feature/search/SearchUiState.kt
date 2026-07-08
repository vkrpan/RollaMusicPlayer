package com.rolla.musicplayer.feature.search

import androidx.compose.runtime.Immutable
import com.rolla.musicplayer.core.model.SearchResults

/**
 * Search screen UI state. Each variant is one distinct phase of the pipeline driven by
 * [SearchViewModel.uiState] -- exactly one is ever current at a time, so the screen can render a
 * plain `when` over this type instead of juggling separate loading/empty/error booleans.
 */
@Immutable
sealed interface SearchUiState {

    /** No query typed yet (or the query was cleared) -- the "type to search" placeholder state. */
    data object Idle : SearchUiState

    /** A non-blank query is in flight; results for it have not arrived yet. */
    data object Loading : SearchUiState

    /** Results arrived for the current query, but none of songs/albums/artists matched. */
    data object Empty : SearchUiState

    /** Results arrived for the current query and at least one of songs/albums/artists matched. */
    data class Results(val results: SearchResults) : SearchUiState
}

/** Maps a repository emission onto the corresponding non-loading [SearchUiState] variant. */
internal fun SearchResults.toUiState(): SearchUiState =
    if (isEmpty) SearchUiState.Empty else SearchUiState.Results(this)
