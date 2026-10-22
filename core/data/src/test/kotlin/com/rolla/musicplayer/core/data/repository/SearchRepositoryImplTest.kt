package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.SearchDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.AlbumSearchRow
import com.rolla.musicplayer.core.database.relation.ArtistSearchRow
import com.rolla.musicplayer.core.datastore.RecentSearchesDataSource
import io.mockk.Called
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchRepositoryImplTest {

    private val searchDao: SearchDao = mockk()
    private val recentSearchesDataSource: RecentSearchesDataSource = mockk()
    private val repository = SearchRepositoryImpl(searchDao, recentSearchesDataSource)

    private fun stubDao(
        songs: List<SongEntity> = emptyList(),
        albums: List<AlbumSearchRow> = emptyList(),
        artists: List<ArtistSearchRow> = emptyList(),
    ) {
        every { searchDao.searchSongs(any()) } returns flowOf(songs)
        every { searchDao.searchAlbums(any()) } returns flowOf(albums)
        every { searchDao.searchArtists(any()) } returns flowOf(artists)
    }

    private fun songEntity(id: String, title: String) = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "content://media/external/audio/albumart/10",
        dateModified = 1_000L,
    )

    @Test
    fun search_blankQuery_emitsEmptyWithoutTouchingDao() = runTest {
        val results = repository.search("   ").first()

        assertTrue(results.isEmpty)
        verify { searchDao wasNot Called }
    }

    @Test
    fun search_mapsAllThreeGroupsToDomain() = runTest {
        stubDao(
            songs = listOf(songEntity(id = "1", title = "Airplane Mode")),
            albums = listOf(
                AlbumSearchRow(albumId = 10L, title = "Airwaves", artist = "A", songCount = 2, artworkUri = "art"),
            ),
            artists = listOf(
                ArtistSearchRow(name = "Air Supply", albumCount = 2, songCount = 5, artworkUri = "art-air"),
            ),
        )

        val results = repository.search("air").first()

        assertEquals(listOf("Airplane Mode"), results.songs.map { it.title })
        assertEquals(listOf("Airwaves"), results.albums.map { it.title })
        assertEquals(listOf("Air Supply"), results.artists.map { it.name })
    }

    @Test
    fun search_sameArtistName_alwaysGetsSameSyntheticId() = runTest {
        stubDao(
            artists = listOf(
                ArtistSearchRow(name = "Air Supply", albumCount = 1, songCount = 1, artworkUri = "art-air"),
            ),
        )

        val first = repository.search("air").first().artists.single().id
        val second = repository.search("air").first().artists.single().id

        assertEquals(first, second)
    }

    @Test
    fun search_escapesLikeWildcardsBeforeQueryingDao() = runTest {
        stubDao()

        repository.search("""50%_\ off""").first()

        val expected = """50\%\_\\ off"""
        verify { searchDao.searchSongs(expected) }
        verify { searchDao.searchAlbums(expected) }
        verify { searchDao.searchArtists(expected) }
    }

    @Test
    fun search_noMatches_emitsEmptyResults() = runTest {
        stubDao()

        val results = repository.search("zzz").first()

        assertTrue(results.isEmpty)
    }

    // ── recent searches (delegation to RecentSearchesDataSource) ────────────────

    @Test
    fun observeRecentSearches_delegatesToDataSource() = runTest {
        every { recentSearchesDataSource.recentSearches } returns flowOf(listOf("rock", "jazz"))

        val result = repository.observeRecentSearches().first()

        assertEquals(listOf("rock", "jazz"), result)
    }

    @Test
    fun recordRecentSearch_delegatesToDataSource() = runTest {
        coEvery { recentSearchesDataSource.record(any()) } just Runs

        repository.recordRecentSearch("rock")

        coVerify { recentSearchesDataSource.record("rock") }
    }

    @Test
    fun clearRecentSearches_delegatesToDataSource() = runTest {
        coEvery { recentSearchesDataSource.clear() } just Runs

        repository.clearRecentSearches()

        coVerify { recentSearchesDataSource.clear() }
    }

    // Phase 6b ruling 5. Trivial stub: a no-op body → "q" is still stored → RED. Mutation: the same no-op body.
    // Control: both queries are stored before the call.
    @Test
    fun removeRecentSearchDelegatesToTheDataSource() = runTest {
        val dataSource = InMemoryRecentSearches(listOf("q", "r"))
        val repository = SearchRepositoryImpl(searchDao, dataSource)
        assertEquals("control: both queries are stored first", listOf("q", "r"), dataSource.recentSearches.first())

        repository.removeRecentSearch("q")

        val stored = dataSource.recentSearches.first()
        assertFalse("q is removed from the data source", "q" in stored)
        assertTrue("r stays in the data source", "r" in stored)
    }

    /** A list-backed data source, so the test reads what the repository left stored rather than which calls it made. */
    private class InMemoryRecentSearches(initial: List<String>) : RecentSearchesDataSource {
        private val queries = MutableStateFlow(initial)
        override val recentSearches: Flow<List<String>> = queries
        override suspend fun record(query: String) {
            queries.value = listOf(query) + queries.value.filterNot { it == query }
        }
        override suspend fun remove(query: String) {
            queries.value = queries.value.filterNot { it == query }
        }
        override suspend fun clear() {
            queries.value = emptyList()
        }
    }
}
