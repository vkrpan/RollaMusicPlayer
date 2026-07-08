package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.SearchDao
import com.rolla.musicplayer.core.database.entity.toDomain
import com.rolla.musicplayer.core.database.relation.toDomain
import com.rolla.musicplayer.core.datastore.RecentSearchesDataSource
import com.rolla.musicplayer.core.model.SearchResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject

class SearchRepositoryImpl @Inject constructor(
    private val searchDao: SearchDao,
    private val recentSearchesDataSource: RecentSearchesDataSource,
) : SearchRepository {

    override fun search(query: String): Flow<SearchResults> {
        if (query.isBlank()) return flowOf(SearchResults.EMPTY)
        val escaped = query.escapeForLike()
        return combine(
            searchDao.searchSongs(escaped),
            searchDao.searchAlbums(escaped),
            searchDao.searchArtists(escaped),
        ) { songs, albums, artists ->
            SearchResults(
                songs = songs.map { it.toDomain() },
                albums = albums.map { it.toDomain() },
                artists = artists.map { it.toDomain() },
            )
        }
            // Room runs the queries on its own executor regardless; flowOn additionally keeps the
            // three-way combine + domain mapping off the collector's (main) dispatcher.
            .flowOn(Dispatchers.IO)
            .distinctUntilChanged()
    }

    override fun observeRecentSearches(): Flow<List<String>> = recentSearchesDataSource.recentSearches

    override suspend fun recordRecentSearch(query: String) = recentSearchesDataSource.record(query)

    override suspend fun clearRecentSearches() = recentSearchesDataSource.clear()
}

/**
 * Makes raw user input safe for the `LIKE ... ESCAPE '\'` queries in [SearchDao]: `%`, `_`, and
 * `\` become literal characters instead of wildcards/escapes. Backslash must be escaped FIRST,
 * or it would double-escape the markers added for `%`/`_`.
 */
private fun String.escapeForLike(): String =
    replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
