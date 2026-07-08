package com.rolla.musicplayer.core.datastore

import kotlinx.coroutines.flow.Flow

/**
 * Contract for the local search screen's recent-query history.
 *
 * Entirely on-device (Preferences DataStore) -- there is no query-suggestion service to sync
 * with, per this app's offline-first architecture. History is most-recent-first, exact-match
 * deduplicated (recording a query already present moves it to the front instead of creating a
 * duplicate entry), and capped at [MAX_RECENT_SEARCHES] entries.
 */
interface RecentSearchesDataSource {

    /** Recent queries, most-recent-first. Defaults to `emptyList()` when nothing is stored. */
    val recentSearches: Flow<List<String>>

    /**
     * Records [query] as the newest recent search.
     *
     * [query] is trimmed before storage; a blank (post-trim) value is ignored entirely -- it is
     * neither stored nor does it disturb existing history. An exact match already present in the
     * list is removed from its old position and re-inserted at the front rather than duplicated.
     * The list is capped at [MAX_RECENT_SEARCHES] entries, dropping the oldest entries beyond it.
     */
    suspend fun record(query: String)

    /** Clears all recent search history. */
    suspend fun clear()
}

/** Maximum number of recent search queries retained by [RecentSearchesDataSource]. */
internal const val MAX_RECENT_SEARCHES = 10
