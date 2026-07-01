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
}
