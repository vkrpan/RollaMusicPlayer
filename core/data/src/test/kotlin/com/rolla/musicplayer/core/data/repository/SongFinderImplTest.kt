package com.rolla.musicplayer.core.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.testing.FakeCustomTagDao
import com.rolla.musicplayer.core.testing.FakeSongDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [SongFinderImpl] over [FakeSongDao] and [FakeCustomTagDao]: the library filtered in memory, in library order, and
 * recomputed whenever the filter, the songs or the song-to-tag links change. The fake tag DAO's link writes check the
 * song rows (the foreign key), whose throw constructs `SQLiteConstraintException`, so this runs under Robolectric.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class SongFinderImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val songDao = FakeSongDao()
    private val customTagDao = FakeCustomTagDao()
    private val finder = SongFinderImpl(songDao, customTagDao, mainDispatcherRule.testDispatcher)

    // Seeded in Room's order (title COLLATE NOCASE: ASCII case folded, "Ä" after "Z"); the library order collates
    // "Ärger" between "apple" and "Zebra". Trivial stub (empty list) fails the assertion.
    // Mutation: skip the sort → ["1999", "apple", "Zebra", "Ärger"] → RED.
    @Test
    fun observeMatches_emptyFilter_wholeLibraryInLibraryOrder() = runTest {
        seedSongs(entity("1", "1999"), entity("2", "apple"), entity("3", "Zebra"), entity("4", "Ärger"))

        val result = finder.observeMatches(flowOf(SongFilter())).first()

        assertEquals(
            "whole library, library order",
            listOf("1999", "apple", "Ärger", "Zebra"),
            result.map { it.title },
        )
    }

    // Trivial stub (whole library, ignoring the filter) fails the first emission. Mutation: read the filter once
    // (`filter.first()`) → the second filter is never applied → RED.
    @Test
    fun observeMatches_reEmitsWhenFilterChanges() = runTest {
        seedSongs(entity("1", "Lovesong"), entity("2", "Hate"))
        val filter = MutableStateFlow(SongFilter(title = "love"))
        val emissions = collect(finder.observeMatches(filter))

        assertEquals("first filter", listOf("Lovesong"), emissions.last().map { it.title })

        filter.value = SongFilter(title = "hat")

        assertEquals("second filter", listOf("Hate"), emissions.last().map { it.title })
    }

    // Review Focus 4. The song is first shown present, then absent. Mutation: read the links once (`first()`) → the
    // dropped song stays → RED.
    @Test
    fun observeMatches_reEmitsWhenLinksChange() = runTest {
        seedSongs(entity("1", "Lovesong"), entity("2", "Hate"))
        customTagDao.applyFileTags("1", CustomTagIds(tagIds = setOf(2L), subTagIds = emptySet()), null, 0L)
        val emissions = collect(finder.observeMatches(flowOf(SongFilter(tagIds = setOf(2L)))))

        assertEquals("song 1 carries TAG 2", listOf("1"), emissions.last().map { it.id })

        customTagDao.applyFileTags("1", CustomTagIds.EMPTY, null, 0L)

        assertEquals("song 1 no longer carries TAG 2", emptyList<String>(), emissions.last().map { it.id })
    }

    private suspend fun seedSongs(vararg songs: SongEntity) {
        songDao.upsertSongs(songs.toList())
        customTagDao.seedSongs(songs.toList())
    }

    /** Collects [flow] for the rest of the test; the returned list grows with each emission. */
    private fun TestScope.collect(flow: Flow<List<Song>>): List<List<Song>> {
        val emissions = mutableListOf<List<Song>>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { emissions += it } }
        return emissions
    }

    private fun entity(id: String, title: String) = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 200_000L,
        trackNumber = 1,
        year = 2020,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "rolla-artwork://song/$id?v=1000",
        dateModified = 1_000L,
    )
}
