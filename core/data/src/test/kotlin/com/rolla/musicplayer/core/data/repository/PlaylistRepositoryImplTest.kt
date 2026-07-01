package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.testing.FakePlaylistDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakePlaylistDao: FakePlaylistDao
    private lateinit var repository: PlaylistRepositoryImpl

    @Before
    fun setup() {
        fakePlaylistDao = FakePlaylistDao()
        repository = PlaylistRepositoryImpl(fakePlaylistDao)
    }

    // ── observePlaylists ──────────────────────────────────────────────────────

    @Test
    fun `observePlaylists_reflectsSongCounts`() = runTest {
        val playlistId = repository.createPlaylist("Road Trip")
        repository.addSongs(playlistId, listOf("song-1", "song-2"))

        val playlists: List<Playlist> = repository.observePlaylists().first()

        assertEquals(1, playlists.size)
        assertEquals("Road Trip", playlists[0].name)
        assertEquals("songCount must reflect the two added songs", 2, playlists[0].songCount)
    }

    // ── observePlaylistSongs ──────────────────────────────────────────────────

    @Test
    fun `observePlaylistSongs_returnsDomainSongsInPositionOrder`() = runTest {
        fakePlaylistDao.emitSongs(
            listOf(
                testSongEntity(id = "song-1", title = "Alpha"),
                testSongEntity(id = "song-2", title = "Beta"),
                testSongEntity(id = "song-3", title = "Gamma"),
            ),
        )
        val playlistId = repository.createPlaylist("Mix")
        repository.addSongs(playlistId, listOf("song-3", "song-1", "song-2"))

        val songs = repository.observePlaylistSongs(playlistId).first()

        assertEquals(listOf("song-3", "song-1", "song-2"), songs.map { it.id })
    }

    // ── createPlaylist / renamePlaylist / deletePlaylist ─────────────────────

    @Test
    fun `createPlaylist_thenRenamePlaylist_updatesName`() = runTest {
        val playlistId = repository.createPlaylist("Old Name")

        repository.renamePlaylist(playlistId, "New Name")

        val playlists = repository.observePlaylists().first()
        assertEquals("New Name", playlists.first { it.id == playlistId }.name)
    }

    @Test
    fun `deletePlaylist_removesPlaylistFromObservePlaylists`() = runTest {
        val playlistId = repository.createPlaylist("Temp")

        repository.deletePlaylist(playlistId)

        val playlists = repository.observePlaylists().first()
        assertTrue("Deleted playlist must no longer be present", playlists.none { it.id == playlistId })
    }

    // ── addSongs ──────────────────────────────────────────────────────────────

    @Test
    fun `addSongs_assignsSequentialPositionsAfterExisting`() = runTest {
        fakePlaylistDao.emitSongs(
            listOf(
                testSongEntity(id = "song-1"),
                testSongEntity(id = "song-2"),
                testSongEntity(id = "song-3"),
            ),
        )
        val playlistId = repository.createPlaylist("Mix")
        repository.addSongs(playlistId, listOf("song-1"))

        repository.addSongs(playlistId, listOf("song-2", "song-3"))

        val songs = repository.observePlaylistSongs(playlistId).first()
        assertEquals(listOf("song-1", "song-2", "song-3"), songs.map { it.id })
    }

    @Test
    fun `addSongs_reAddingExistingSongDoesNotDuplicate`() = runTest {
        fakePlaylistDao.emitSongs(listOf(testSongEntity(id = "song-1")))
        val playlistId = repository.createPlaylist("Mix")
        repository.addSongs(playlistId, listOf("song-1"))

        repository.addSongs(playlistId, listOf("song-1"))

        val songs = repository.observePlaylistSongs(playlistId).first()
        assertEquals("Re-adding the same song must not duplicate it", 1, songs.size)
    }

    // ── removeSong ────────────────────────────────────────────────────────────

    @Test
    fun `removeSong_removesOnlyTheGivenSong`() = runTest {
        fakePlaylistDao.emitSongs(listOf(testSongEntity(id = "song-1"), testSongEntity(id = "song-2")))
        val playlistId = repository.createPlaylist("Mix")
        repository.addSongs(playlistId, listOf("song-1", "song-2"))

        repository.removeSong(playlistId, "song-1")

        val songs = repository.observePlaylistSongs(playlistId).first()
        assertEquals(listOf("song-2"), songs.map { it.id })
    }

    // ── reorder ───────────────────────────────────────────────────────────────

    @Test
    fun `reorder_rewritesOrderAndIsReflectedInSubsequentEmission`() = runTest {
        fakePlaylistDao.emitSongs(
            listOf(
                testSongEntity(id = "song-1"),
                testSongEntity(id = "song-2"),
                testSongEntity(id = "song-3"),
            ),
        )
        val playlistId = repository.createPlaylist("Mix")
        repository.addSongs(playlistId, listOf("song-1", "song-2", "song-3"))

        repository.reorder(playlistId, listOf("song-3", "song-1", "song-2"))

        val songs = repository.observePlaylistSongs(playlistId).first()
        assertEquals(listOf("song-3", "song-1", "song-2"), songs.map { it.id })
    }

    // ── Test factories ────────────────────────────────────────────────────────

    @Suppress("LongParameterList")
    private fun testSongEntity(
        id: String = "test-id",
        mediaStoreId: Long = 1L,
        title: String = "Test Song",
        artist: String = "Test Artist",
        album: String = "Test Album",
        albumId: Long = 1L,
        durationMs: Long = 180_000L,
        trackNumber: Int? = 1,
        year: Int? = 2024,
        contentUri: String = "content://media/external/audio/media/1",
        artworkUri: String = "content://media/external/audio/albumart/1",
        dateModified: Long = 1_700_000_000L,
    ) = SongEntity(
        id = id,
        mediaStoreId = mediaStoreId,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        contentUri = contentUri,
        artworkUri = artworkUri,
        dateModified = dateModified,
    )
}
