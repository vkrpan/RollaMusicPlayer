package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * Maximum number of recent search queries kept by [FakeSearchRepository], mirroring the real
 * `RecentSearchesDataSource`'s cap.
 */
private const val MAX_RECENT_SEARCHES = 10

/**
 * In-memory fake implementation of [SearchRepository], mirroring [FakeSongRepository]'s shape.
 *
 * Call [emit] from tests to control what any non-blank query returns through [search]. Blank
 * queries short-circuit to a single [SearchResults.EMPTY] emission without recording, matching
 * the real repository's contract. Every non-blank query passed to [search] is recorded in
 * [searchedQueries] so ViewModel tests can assert debouncing/latest-query behavior.
 *
 * [observeRecentSearches]/[recordRecentSearch]/[clearRecentSearches] keep an in-memory,
 * most-recent-first list with the same trim/exact-match-dedupe/cap-at-[MAX_RECENT_SEARCHES]
 * semantics as the real `RecentSearchesDataSource`, so ViewModel tests can exercise recent-search
 * behavior without a real DataStore.
 */
class FakeSearchRepository : SearchRepository {

    private val resultsFlow = MutableStateFlow(SearchResults.EMPTY)
    private val recentSearchesFlow = MutableStateFlow<List<String>>(emptyList())

    /** Non-blank queries received by [search], in call order. */
    val searchedQueries = mutableListOf<String>()

    /** Replaces the results emitted for the current (and future) non-blank queries. */
    fun emit(results: SearchResults) {
        resultsFlow.value = results
    }

    override fun search(query: String): Flow<SearchResults> {
        if (query.isBlank()) return flowOf(SearchResults.EMPTY)
        searchedQueries += query
        return resultsFlow.asStateFlow()
    }

    override fun observeRecentSearches(): Flow<List<String>> = recentSearchesFlow.asStateFlow()

    override suspend fun recordRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        recentSearchesFlow.update { current ->
            (listOf(trimmed) + current.filterNot { it == trimmed }).take(MAX_RECENT_SEARCHES)
        }
    }

    override suspend fun clearRecentSearches() {
        recentSearchesFlow.value = emptyList()
    }
}
