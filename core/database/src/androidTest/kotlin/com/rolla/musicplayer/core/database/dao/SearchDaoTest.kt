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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    private lateinit var searchDao: SearchDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        searchDao = database.searchDao()
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
    fun searchSongs_matchesSubstringInTitle() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Stairway to Heaven"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Yesterday"),
            ),
        )

        val results = searchDao.searchSongs("air").first()

        assertEquals(listOf("1"), results.map { it.id })
    }

    @Test
    fun searchSongs_matchesSubstringInArtist() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Song A").copy(artist = "Fairground Attraction"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Song B").copy(artist = "The Beatles"),
            ),
        )

        val results = searchDao.searchSongs("air").first()

        assertEquals(listOf("1"), results.map { it.id })
    }

    @Test
    fun searchSongs_matchesSubstringInAlbum() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Song A").copy(album = "Airwaves"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Song B").copy(album = "Help!"),
            ),
        )

        val results = searchDao.searchSongs("air").first()

        assertEquals(listOf("1"), results.map { it.id })
    }

    @Test
    fun searchSongs_noMatchingQuery_returnsEmptyList() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L, title = "Alpha")))

        val results = searchDao.searchSongs("zzz-no-match").first()

        assertTrue(results.isEmpty())
    }

    @Test
    fun searchSongs_escapedPercentSign_matchesLiteralPercentNotWildcard() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "50% Off Sale"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "50 Percent Cotton"),
            ),
        )

        // The caller-side escaping contract (see SearchDao KDoc): raw "50%" arrives here as "50\%".
        val results = searchDao.searchSongs("50\\%").first()

        assertEquals(listOf("1"), results.map { it.id })
    }

    @Test
    fun searchSongs_escapedUnderscore_matchesLiteralUnderscoreNotSingleCharWildcard() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "track_01"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "trackA01"),
            ),
        )

        // The caller-side escaping contract (see SearchDao KDoc): raw "track_01" arrives as "track\_01".
        val results = searchDao.searchSongs("track\\_01").first()

        assertEquals(listOf("1"), results.map { it.id })
    }

    @Test
    fun searchSongs_ordersTitlePrefixMatchesBeforeSubstringMatches_thenAlphabetically() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Fair Weather"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Airplane Mode"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Air Supply"),
            ),
        )

        val results = searchDao.searchSongs("air").first()

        assertEquals(listOf("3", "2", "1"), results.map { it.id })
    }

    @Test
    fun searchAlbums_groupsByAlbumId_withCorrectCountsAndMinArtist() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "A1")
                    .copy(albumId = 100L, album = "Greatest Hits", artist = "Artist B", artworkUri = "art-100"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "A2")
                    .copy(albumId = 100L, album = "Greatest Hits", artist = "Artist A", artworkUri = "art-100"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "B1")
                    .copy(albumId = 200L, album = "Unrelated Album", artist = "Artist C", artworkUri = "art-200"),
            ),
        )

        val results = searchDao.searchAlbums("greatest").first()

        assertEquals(1, results.size)
        val album = results.first()
        assertEquals(100L, album.albumId)
        assertEquals("Greatest Hits", album.title)
        assertEquals("Artist A", album.artist)
        assertEquals(2, album.songCount)
        assertEquals("art-100", album.artworkUri)
    }

    @Test
    fun searchAlbums_noMatchingQuery_returnsEmptyList() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(album = "Alpha")))

        val results = searchDao.searchAlbums("zzz-no-match").first()

        assertTrue(results.isEmpty())
    }

    @Test
    fun searchArtists_groupsByArtistName_withAlbumAndSongCounts() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Air Supply", albumId = 10L),
                buildSongEntity(id = "2", mediaStoreId = 2L).copy(artist = "Air Supply", albumId = 20L),
                buildSongEntity(id = "3", mediaStoreId = 3L).copy(artist = "Air Supply", albumId = 20L),
                buildSongEntity(id = "4", mediaStoreId = 4L).copy(artist = "Other Artist", albumId = 30L),
            ),
        )

        val results = searchDao.searchArtists("air").first()

        assertEquals(1, results.size)
        val artist = results.first()
        assertEquals("Air Supply", artist.name)
        assertEquals(2, artist.albumCount)
        assertEquals(3, artist.songCount)
    }

    @Test
    fun searchArtists_noMatchingQuery_returnsEmptyList() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Alpha")))

        val results = searchDao.searchArtists("zzz-no-match").first()

        assertTrue(results.isEmpty())
    }
}
