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

    // ── edge cases: empty / single-song playlists ────────────────────────────

    @Test
    fun getPlaylistWithSongs_forPlaylistWithNoSongs_returnsEmptyList() = runTest {
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)

        assertEquals(emptyList<String>(), withSongs?.songs?.map { it.id })
        assertEquals(0, playlistDao.observePlaylistsWithCounts().first().first().songCount)
    }

    @Test
    fun addSongToPlaylist_toEmptyPlaylist_assignsPositionZero() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L)))
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())

        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf(0), withSongs?.crossRefs?.map { it.position })
    }

    // ── remove song: position compaction ─────────────────────────────────────

    @Test
    fun removeSongFromPlaylist_middleSong_compactsRemainingPositions() = runTest {
        // removeSongFromPlaylist shifts every later row's position down by one (see its docstring
        // in PlaylistDao), so removing a middle song leaves positions contiguous — 0 and 1, not a
        // 0-and-2 gap. This matters because appendSongs derives the next position from
        // MAX(position) + 1: a stale gap would otherwise let a newly appended song collide with
        // an existing song's position (see the corresponding PlaylistRepositoryImplTest coverage).
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

        playlistDao.removeSongFromPlaylist(playlistId, "2")

        val remaining = playlistDao.getPlaylistWithSongsUnordered(playlistId)?.crossRefs.orEmpty()
        assertEquals(2, remaining.size)
        assertEquals(
            "positions must be compacted to a contiguous 0..n-1 range after removal",
            setOf(0, 1),
            remaining.map { it.position }.toSet(),
        )
    }

    @Test
    fun appendSongs_afterRemovingMiddleSong_assignsNextPositionWithoutCollision() = runTest {
        // Regression coverage for the position-collision risk: appendSongs must derive the next
        // position from MAX(position) + 1 (not the row count), so it never collides with an
        // existing row even when a prior removal briefly created — and then compacted — a gap.
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L),
                buildSongEntity(id = "2", mediaStoreId = 2L),
                buildSongEntity(id = "3", mediaStoreId = 3L),
                buildSongEntity(id = "4", mediaStoreId = 4L),
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

        playlistDao.removeSongFromPlaylist(playlistId, "2")
        playlistDao.appendSongs(playlistId, listOf("4"), addedAt = 0L)

        val crossRefs = playlistDao.getPlaylistWithSongsUnordered(playlistId)?.crossRefs.orEmpty()
        assertEquals(
            "every remaining row must have a unique position",
            crossRefs.size,
            crossRefs.map { it.position }.toSet().size,
        )
        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf("1", "3", "4"), withSongs?.songs?.map { it.id })
    }

    @Test
    fun removeSongFromPlaylist_nonExistentSong_isNoOpAndDoesNotThrow() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L)))
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = "1", position = 0, addedAt = 0L),
        )

        playlistDao.removeSongFromPlaylist(playlistId, "does-not-exist")

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf("1"), withSongs?.songs?.map { it.id })
    }

    // ── reorder: move up / move down / atomicity ─────────────────────────────

    @Test
    fun reorder_moveLastSongToFront_movesUpAndShiftsOthersDown() = runTest {
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

        // "Move up": song 3 (last) becomes first.
        playlistDao.reorder(playlistId, listOf("3", "1", "2"))

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf("3", "1", "2"), withSongs?.songs?.map { it.id })
        assertEquals(listOf(0, 1, 2), withSongs?.crossRefs?.sortedBy { it.position }?.map { it.position })
    }

    @Test
    fun reorder_moveFirstSongToBack_movesDownAndShiftsOthersUp() = runTest {
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

        // "Move down": song 1 (first) becomes last.
        playlistDao.reorder(playlistId, listOf("2", "3", "1"))

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf("2", "3", "1"), withSongs?.songs?.map { it.id })
        assertEquals(listOf(0, 1, 2), withSongs?.crossRefs?.sortedBy { it.position }?.map { it.position })
    }

    @Test
    fun reorder_toIdenticalOrder_leavesPositionsUnchanged() = runTest {
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

        playlistDao.reorder(playlistId, listOf("1", "2"))

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(listOf("1", "2"), withSongs?.songs?.map { it.id })
    }

    @Test
    fun reorder_resultingPositionsAreContiguousFromZero() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L),
                buildSongEntity(id = "2", mediaStoreId = 2L),
                buildSongEntity(id = "3", mediaStoreId = 3L),
                buildSongEntity(id = "4", mediaStoreId = 4L),
            ),
        )
        val playlistId = playlistDao.insertPlaylist(buildPlaylistEntity())
        listOf("1", "2", "3", "4").forEachIndexed { index, songId ->
            playlistDao.addSongToPlaylist(
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = index, addedAt = 0L),
            )
        }

        playlistDao.reorder(playlistId, listOf("4", "2", "1", "3"))

        val positions = playlistDao.getPlaylistWithSongsUnordered(playlistId)?.crossRefs
            ?.sortedBy { it.position }
            ?.map { it.position }
        assertEquals("reorder must leave positions contiguous, starting at 0", listOf(0, 1, 2, 3), positions)
    }

    // ── cascade: deleting a song removes it from every playlist ──────────────

    @Test
    fun deletingSong_cascadesRemovalFromPlaylistSongs() = runTest {
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

        songDao.deleteByMediaStoreIds(listOf(1L))

        val withSongs = playlistDao.getPlaylistWithSongs(playlistId)
        assertEquals(
            "deleting a song must cascade-remove its playlist_songs cross-ref rows",
            listOf("2"),
            withSongs?.songs?.map { it.id },
        )
        assertEquals(1, playlistDao.observePlaylistsWithCounts().first().first().songCount)
    }

    // ── observePlaylistsWithCounts: multiple playlists ───────────────────────

    @Test
    fun observePlaylistsWithCounts_multiplePlaylists_areOrderedAlphabeticallyByName() = runTest {
        playlistDao.insertPlaylist(buildPlaylistEntity(name = "Zeta"))
        playlistDao.insertPlaylist(buildPlaylistEntity(name = "Alpha"))
        playlistDao.insertPlaylist(buildPlaylistEntity(name = "Mango"))

        val names = playlistDao.observePlaylistsWithCounts().first().map { it.playlist.name }

        assertEquals(listOf("Alpha", "Mango", "Zeta"), names)
    }
}
