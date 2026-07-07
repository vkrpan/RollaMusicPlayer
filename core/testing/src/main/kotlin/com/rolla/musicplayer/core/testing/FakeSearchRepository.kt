package com.rolla.musicplayer.core.testing

import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory fake implementation of [SearchRepository], mirroring [FakeSongRepository]'s shape.
 *
 * Call [emit] from tests to control what any non-blank query returns through [search]. Blank
 * queries short-circuit to a single [SearchResults.EMPTY] emission without recording, matching
 * the real repository's contract. Every non-blank query passed to [search] is recorded in
 * [searchedQueries] so ViewModel tests can assert debouncing/latest-query behavior.
 */
class FakeSearchRepository : SearchRepository {

    private val resultsFlow = MutableStateFlow(SearchResults.EMPTY)

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
}
