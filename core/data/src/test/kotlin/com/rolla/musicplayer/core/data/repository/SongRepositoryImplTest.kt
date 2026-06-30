package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSongDao
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SongRepositoryImplTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeSongDao: FakeSongDao
    private lateinit var repository: SongRepositoryImpl

    @Before
    fun setup() {
        fakeSongDao = FakeSongDao()
        repository = SongRepositoryImpl(fakeSongDao)
    }

    // ── observeSongs ──────────────────────────────────────────────────────────

    @Test
    fun `observeSongs_mapsEntitiesToDomain`() = runTest {
        val entity = testSongEntity(
            id = "song-1",
            mediaStoreId = 1001L,
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California",
            albumId = 200L,
            durationMs = 391_000L,
            trackNumber = 1,
            year = 1977,
            contentUri = "content://media/external/audio/media/1001",
            artworkUri = "content://media/external/audio/albumart/200",
        )
        fakeSongDao.emit(listOf(entity))

        // FakeSongDao._songs is a StateFlow — .first() receives the current value immediately.
        val songs: List<Song> = repository.observeSongs().first()

        assertEquals("Exactly one song expected after emitting one entity", 1, songs.size)
        val song = songs[0]
        assertEquals("id mapping", entity.id, song.id)
        assertEquals("title mapping", entity.title, song.title)
        assertEquals("artist mapping", entity.artist, song.artist)
        assertEquals("album mapping", entity.album, song.album)
        assertEquals("albumId mapping", entity.albumId, song.albumId)
        assertEquals("durationMs mapping", entity.durationMs, song.durationMs)
        assertEquals("trackNumber mapping", entity.trackNumber, song.trackNumber)
        assertEquals("year mapping", entity.year, song.year)
        assertEquals("contentUri mapping", entity.contentUri, song.contentUri)
        assertEquals("artworkUri mapping", entity.artworkUri, song.artworkUri)
    }

    @Test
    fun `observeSongs_emitsOnUpdate`() = runTest {
        val received = mutableListOf<List<Song>>()

        // Start collecting. With UnconfinedTestDispatcher the coroutine starts immediately,
        // subscribes to the DAO's StateFlow, and receives the initial empty-list emission.
        val collectJob = launch { repository.observeSongs().collect { received.add(it) } }
        advanceUntilIdle()

        // Push one entity into the DAO to trigger a second emission.
        fakeSongDao.emit(listOf(testSongEntity()))
        advanceUntilIdle()

        assertEquals(
            "Last emission must contain exactly the one song that was emitted",
            1,
            received.last().size,
        )
        collectJob.cancel()
    }

    @Test
    fun `observeSongs_emptyDaoEmitsEmptyList`() = runTest {
        // FakeSongDao starts empty; .first() should resolve immediately with [].
        val songs: List<Song> = repository.observeSongs().first()

        assertTrue("observeSongs must emit an empty list when the DAO holds no songs", songs.isEmpty())
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
