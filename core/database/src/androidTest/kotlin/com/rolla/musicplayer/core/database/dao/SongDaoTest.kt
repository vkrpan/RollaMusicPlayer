package com.rolla.musicplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SongDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun buildSongEntity(
        id: String = "1",
        mediaStoreId: Long = 1L,
        title: String = "Test Song",
    ): SongEntity = SongEntity(
        id = id,
        mediaStoreId = mediaStoreId,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = 1,
        year = 2024,
        contentUri = "content://media/external/audio/media/$mediaStoreId",
        artworkUri = "content://media/external/audio/albumart/10",
        dateModified = 1_000_000L,
        dateAdded = 1_000_000L,
        isFavorite = false,
        playCount = 0,
        lastPlayed = null,
    )

    @Test
    fun upsertSongs_insertsAndGetAllSongs_returnsCorrectData() = runTest {
        val songs = listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L, title = "Alpha"),
            buildSongEntity(id = "2", mediaStoreId = 2L, title = "Beta"),
        )

        songDao.upsertSongs(songs)

        val result = songDao.getAllSongs()
        assertEquals(2, result.size)
        assertTrue(result.any { it.id == "1" && it.title == "Alpha" })
        assertTrue(result.any { it.id == "2" && it.title == "Beta" })
    }

    @Test
    fun upsertSongs_withSameId_replacesExistingRow() = runTest {
        val original = buildSongEntity(id = "1", title = "Original Title")
        songDao.upsertSongs(listOf(original))

        val updated = buildSongEntity(id = "1", title = "Updated Title")
        songDao.upsertSongs(listOf(updated))

        val result = songDao.getAllSongs()
        assertEquals(1, result.size)
        assertEquals("Updated Title", result.first().title)
    }

    @Test
    fun observeAllSongs_emitsUpdatedListAfterUpsert() = runTest {
        val initial = buildSongEntity(id = "1", title = "First Song")
        songDao.upsertSongs(listOf(initial))

        val firstEmission = songDao.observeAllSongs().first()
        assertEquals(1, firstEmission.size)
        assertEquals("First Song", firstEmission.first().title)

        val additional = buildSongEntity(id = "2", mediaStoreId = 2L, title = "Second Song")
        songDao.upsertSongs(listOf(additional))

        val secondEmission = songDao.observeAllSongs().first()
        assertEquals(2, secondEmission.size)
    }

    @Test
    fun deleteByMediaStoreIds_removesOnlySpecifiedSongs() = runTest {
        val songs = listOf(
            buildSongEntity(id = "1", mediaStoreId = 1L, title = "Keep Me"),
            buildSongEntity(id = "2", mediaStoreId = 2L, title = "Delete Me"),
            buildSongEntity(id = "3", mediaStoreId = 3L, title = "Also Keep"),
        )
        songDao.upsertSongs(songs)

        songDao.deleteByMediaStoreIds(listOf(2L))

        val result = songDao.getAllSongs()
        assertEquals(2, result.size)
        assertFalse(result.any { it.mediaStoreId == 2L })
        assertTrue(result.any { it.id == "1" })
        assertTrue(result.any { it.id == "3" })
    }

    // ── favourites (smart playlist) ───────────────────────────────────────────

    @Test
    fun toggleFavorite_flipsIsFavoriteFlagOnOnlyTheGivenSong() = runTest {
        songDao.upsertSongs(
            listOf(buildSongEntity(id = "1", mediaStoreId = 1L), buildSongEntity(id = "2", mediaStoreId = 2L)),
        )

        songDao.toggleFavorite("1")

        val result = songDao.getAllSongs()
        assertTrue(result.first { it.id == "1" }.isFavorite)
        assertFalse(result.first { it.id == "2" }.isFavorite)
    }

    @Test
    fun toggleFavorite_calledTwice_returnsToOriginalValue() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L)))

        songDao.toggleFavorite("1")
        songDao.toggleFavorite("1")

        assertFalse(songDao.getAllSongs().first().isFavorite)
    }

    @Test
    fun observeFavourites_returnsOnlyFavoritedSongsOrderedByTitle() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Zebra"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Apple"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Not Favourited"),
            ),
        )

        songDao.toggleFavorite("1")
        songDao.toggleFavorite("2")

        val favourites = songDao.observeFavourites().first()
        assertEquals(listOf("Apple", "Zebra"), favourites.map { it.title })
    }

    // ── play-count tracking (smart playlists) ─────────────────────────────────

    @Test
    fun recordPlaybackStarted_incrementsPlayCountAndSetsLastPlayed() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L)))

        songDao.recordPlaybackStarted("1", timestamp = 5_000L)

        val song = songDao.getAllSongs().first()
        assertEquals(1, song.playCount)
        assertEquals(5_000L, song.lastPlayed)
    }

    @Test
    fun recordPlaybackStarted_calledMultipleTimes_accumulatesPlayCount() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L)))

        songDao.recordPlaybackStarted("1", timestamp = 1_000L)
        songDao.recordPlaybackStarted("1", timestamp = 2_000L)
        songDao.recordPlaybackStarted("1", timestamp = 3_000L)

        val song = songDao.getAllSongs().first()
        assertEquals(3, song.playCount)
        assertEquals(3_000L, song.lastPlayed)
    }

    @Test
    fun observeRecentlyPlayed_excludesNeverPlayedSongs_orderedMostRecentFirst() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Played Earlier"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Played Later"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Never Played"),
            ),
        )

        songDao.recordPlaybackStarted("1", timestamp = 1_000L)
        songDao.recordPlaybackStarted("2", timestamp = 2_000L)

        val recentlyPlayed = songDao.observeRecentlyPlayed().first()
        assertEquals(listOf("2", "1"), recentlyPlayed.map { it.id })
    }

    @Test
    fun observeMostPlayed_excludesZeroPlayCountSongs_orderedHighestFirst() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L),
                buildSongEntity(id = "2", mediaStoreId = 2L),
                buildSongEntity(id = "3", mediaStoreId = 3L),
            ),
        )

        songDao.recordPlaybackStarted("1", timestamp = 1_000L)
        repeat(3) { songDao.recordPlaybackStarted("2", timestamp = 1_000L) }
        // song 3 is never played, must be excluded.

        val mostPlayed = songDao.observeMostPlayed().first()
        assertEquals(listOf("2", "1"), mostPlayed.map { it.id })
    }

    @Test
    fun observeRecentlyAdded_ordersByDateAddedDescending() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L).copy(dateAdded = 1_000L),
                buildSongEntity(id = "2", mediaStoreId = 2L).copy(dateAdded = 3_000L),
                buildSongEntity(id = "3", mediaStoreId = 3L).copy(dateAdded = 2_000L),
            ),
        )

        val recentlyAdded = songDao.observeRecentlyAdded().first()
        assertEquals(listOf("2", "3", "1"), recentlyAdded.map { it.id })
    }
}
