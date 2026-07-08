package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.model.SearchResults
import kotlinx.coroutines.flow.Flow

/**
 * Local, offline library search. Matches the user's [query][search] as a substring of song
 * title/artist/album, and of album and artist names, entirely against the on-device Room library
 * — there is no online metadata or suggestion source anywhere in this app.
 */
interface SearchRepository {

    /**
     * Observes grouped results for [query]. A blank query emits [SearchResults.EMPTY] once,
     * without touching the database. Non-blank queries stay live: the flow re-emits when the
     * underlying library changes (re-scan, tag edit). LIKE-wildcard characters in [query]
     * (`%`, `_`, `\`) are treated as literal text, not wildcards — escaping is handled here,
     * callers pass raw user input.
     */
    fun search(query: String): Flow<SearchResults>

    /**
     * Observes the user's recent search history, most-recent-first. Entirely local (Preferences
     * DataStore) — see `:core:datastore`'s `RecentSearchesDataSource`. Emits `emptyList()` when
     * nothing has been recorded yet.
     */
    fun observeRecentSearches(): Flow<List<String>>

    /**
     * Records [query] as the newest recent search. Blank (post-trim) input is ignored; an
     * exact-match duplicate moves to the front instead of being duplicated; history is capped —
     * see `RecentSearchesDataSource.record` for the exact semantics.
     */
    suspend fun recordRecentSearch(query: String)

    /** Clears all recent search history. */
    suspend fun clearRecentSearches()
}
