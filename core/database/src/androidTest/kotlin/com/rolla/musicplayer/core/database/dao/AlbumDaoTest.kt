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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlbumDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    private lateinit var albumDao: AlbumDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        albumDao = database.albumDao()
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
    fun observeAlbum_groupsByAlbumId_withCorrectCountsAndMinArtist() = runTest {
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

        val album = albumDao.observeAlbum(100L).first()

        assertEquals(100L, album?.albumId)
        assertEquals("Greatest Hits", album?.title)
        assertEquals("Artist A", album?.artist)
        assertEquals(2, album?.songCount)
        assertEquals("art-100", album?.artworkUri)
    }

    @Test
    fun observeAlbum_missingAlbumId_emitsNull() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(albumId = 100L)))

        val album = albumDao.observeAlbum(999L).first()

        assertNull(album)
    }

    @Test
    fun observeAlbumSongs_missingAlbumId_emitsEmptyList() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(albumId = 100L)))

        val songs = albumDao.observeAlbumSongs(999L).first()

        assertTrue(songs.isEmpty())
    }

    @Test
    fun observeAlbumSongs_ordersByTrackNumberAscending() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Track Three")
                    .copy(albumId = 100L, trackNumber = 3),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Track One")
                    .copy(albumId = 100L, trackNumber = 1),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Track Two")
                    .copy(albumId = 100L, trackNumber = 2),
            ),
        )

        val songs = albumDao.observeAlbumSongs(100L).first()

        assertEquals(listOf("2", "3", "1"), songs.map { it.id })
    }

    @Test
    fun observeAlbumSongs_sortsUntaggedTrackNumbersLast() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Untagged A")
                    .copy(albumId = 100L, trackNumber = null),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Tagged")
                    .copy(albumId = 100L, trackNumber = 1),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Untagged B")
                    .copy(albumId = 100L, trackNumber = null),
            ),
        )

        val songs = albumDao.observeAlbumSongs(100L).first()

        // Tagged track first, then untagged tracks ordered alphabetically by title as the tiebreak.
        assertEquals(listOf("2", "1", "3"), songs.map { it.id })
    }

    @Test
    fun observeAlbumSongs_tiesOnTrackNumber_breakByTitleAscending() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Zebra")
                    .copy(albumId = 100L, trackNumber = 1),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Apple")
                    .copy(albumId = 100L, trackNumber = 1),
            ),
        )

        val songs = albumDao.observeAlbumSongs(100L).first()

        assertEquals(listOf("2", "1"), songs.map { it.id })
    }

    @Test
    fun observeAlbumSongs_onlyReturnsSongsForRequestedAlbumId() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L).copy(albumId = 100L),
                buildSongEntity(id = "2", mediaStoreId = 2L).copy(albumId = 200L),
            ),
        )

        val songs = albumDao.observeAlbumSongs(100L).first()

        assertEquals(listOf("1"), songs.map { it.id })
    }
}
