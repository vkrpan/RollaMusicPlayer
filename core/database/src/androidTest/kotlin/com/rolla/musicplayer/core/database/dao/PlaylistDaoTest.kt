package com.rolla.musicplayer.core.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.database.MusicDatabase
import com.rolla.musicplayer.core.database.entity.PlaylistEntity
import com.rolla.musicplayer.core.database.entity.PlaylistSongCrossRef
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
class PlaylistDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var songDao: SongDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        playlistDao = database.playlistDao()
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

    private fun buildPlaylistEntity(
        name: String = "My Playlist",
        createdAt: Long = 1_000L,
        updatedAt: Long = 1_000L,
    ): PlaylistEntity = PlaylistEntity(name = name, createdAt = createdAt, updatedAt = updatedAt)

    @Test
    fun insertPlaylist_thenObservePlaylistsWithCounts_returnsZeroCount() = runTest {
        playlistDao.insertPlaylist(buildPlaylistEntity(name = "Road Trip"))

        val playlists = playlistDao.observePlaylistsWithCounts().first()

        assertEquals(1, playlists.size)
        assertEquals("Road Trip", playlists.first().playlist.name)
        assertEquals(0, playlists.first().songCount)
    }

    @Test
    fun renamePlaylist_updatesNameAndTimestamp() = runTest {
        val id = playlistDao.insertPlaylist(buildPlaylistEntity(name = "Old Name", updatedAt = 1_000L))

        playlistDao.renamePlaylist(id, "New Name", 2_000L)

        val playlists = playlistDao.observePlaylistsWithCounts().first()
        assertEquals("New Name", playlists.first().playlist.name)
        assertEquals(2_000L, playlists.first().playlist.updatedAt)
    }

    @Test
    fun deletePlaylist_removesPlaylistAndCascadesCrossRefs() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1")))
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )

        playlistDao.deletePlaylist(playlistId)

        assertTrue(playlistDao.observePlaylistsWithCounts().first().isEmpty())
        assertNull(playlistDao.getPlaylistWithSongs(playlistId))
    }

    @Test
    fun addSongToPlaylist_thenObservePlaylistsWithCounts_reflectsCount() = runTest {
        songDao.upsertSongs(
            listOf(buildSongEntity(id = "1", mediaStoreId = 1L), buildSongEntity(id = "2", mediaStoreId = 2L)),
        )
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())

        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )
        assertEquals(1, playlistDao.observePlaylistsWithCounts().first().first().songCount)

        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "2", position = 1, addedAt = 0L),
        )
        assertEquals(2, playlistDao.observePlaylistsWithCounts().first().first().songCount)
    }

    @Test
    fun removeSongFromPlaylist_decreasesCountAndDropsSong() = runTest {
        songDao.upsertSongs(
            listOf(buildSongEntity(id = "1", mediaStoreId = 1L), buildSongEntity(id = "2", mediaStoreId = 2L)),
        )
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "2", position = 1, addedAt = 0L),
        )

        playlistDao.removeSongFromPlaylist(playlistId, "1")

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(1, withSongs?.songs?.size)
        assertEquals("2", withSongs?.songs?.first()?.id)
    }

    @Test
    fun getPlaylistWithSongs_returnsSongsInPositionOrder() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "First"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Second"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Third"),
            ),
        )
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "3", position = 2, addedAt = 0L),
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "2", position = 1, addedAt = 0L),
        )

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)

        assertEquals(listOf("1", "2", "3"), withSongs?.songs?.map { it.id })
    }

    @Test
    fun reorder_rewritesPositionsAndPersists() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L),
                buildSongEntity(id = "2", mediaStoreId = 2L),
                buildSongEntity(id = "3", mediaStoreId = 3L),
            ),
        )
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "2", position = 1, addedAt = 0L),
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "3", position = 2, addedAt = 0L),
        )

        playlistDao.reorder(playlistId, listOf("3", "1", "2"))

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf("3", "1", "2"), withSongs?.songs?.map { it.id })
    }

    @Test
    fun addSongToPlaylist_sameSongTwice_replacesInsteadOfDuplicating() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L)))
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())

        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 5, addedAt = 100L),
        )

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(1, withSongs?.songs?.size)
        assertEquals(1, playlistDao.observePlaylistsWithCounts().first().first().songCount)
    }
}
