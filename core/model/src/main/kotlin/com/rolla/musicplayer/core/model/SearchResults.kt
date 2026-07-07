package com.rolla.musicplayer.core.model

/**
 * Grouped, local library search results — songs, albums, and artists whose metadata matched the
 * user's query. Produced by `SearchRepository` (`:core:data`) from an offline substring search
 * over the Room library; there is no network/online-suggestion source.
 */
data class SearchResults(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
) {
    /** `true` when none of [songs], [albums], or [artists] matched the query. */
    val isEmpty: Boolean
        get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty()

    companion object {
        /** No query typed yet, or nothing matched. */
        val EMPTY = SearchResults(songs = emptyList(), albums = emptyList(), artists = emptyList())
    }
}
