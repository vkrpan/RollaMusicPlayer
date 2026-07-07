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
}
