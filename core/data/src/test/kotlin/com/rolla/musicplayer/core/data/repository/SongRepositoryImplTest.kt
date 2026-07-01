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

    // ── toggleFavorite ────────────────────────────────────────────────────────

    @Test
    fun `toggleFavorite_flipsIsFavoriteOnMatchingSong`() = runTest {
        fakeSongDao.emit(listOf(testSongEntity(id = "song-1"), testSongEntity(id = "song-2")))

        repository.toggleFavorite("song-1")

        val entities = fakeSongDao.getAllSongs()
        assertTrue("song-1 must now be favorited", entities.first { it.id == "song-1" }.isFavorite)
        assertTrue("song-2 must be unaffected", !entities.first { it.id == "song-2" }.isFavorite)
    }

    // ── observeRecentlyAdded ──────────────────────────────────────────────────

    @Test
    fun `observeRecentlyAdded_ordersMostRecentFirst`() = runTest {
        fakeSongDao.emit(
            listOf(
                testSongEntity(id = "song-old", dateAdded = 1_000L),
                testSongEntity(id = "song-new", dateAdded = 3_000L),
                testSongEntity(id = "song-mid", dateAdded = 2_000L),
            ),
        )

        val songs = repository.observeRecentlyAdded().first()

        assertEquals(listOf("song-new", "song-mid", "song-old"), songs.map { it.id })
    }

    // ── observeRecentlyPlayed ─────────────────────────────────────────────────

    @Test
    fun `observeRecentlyPlayed_excludesNeverPlayedSongs`() = runTest {
        fakeSongDao.emit(
            listOf(
                testSongEntity(id = "song-played", lastPlayed = 2_000L),
                testSongEntity(id = "song-never-played", lastPlayed = null),
            ),
        )

        val songs = repository.observeRecentlyPlayed().first()

        assertEquals(listOf("song-played"), songs.map { it.id })
    }

    // ── observeMostPlayed ─────────────────────────────────────────────────────

    @Test
    fun `observeMostPlayed_excludesZeroPlayCountSongsAndOrdersDescending`() = runTest {
        fakeSongDao.emit(
            listOf(
                testSongEntity(id = "song-popular", playCount = 10),
                testSongEntity(id = "song-unplayed", playCount = 0),
                testSongEntity(id = "song-occasional", playCount = 2),
            ),
        )

        val songs = repository.observeMostPlayed().first()

        assertEquals(listOf("song-popular", "song-occasional"), songs.map { it.id })
    }

    // ── observeFavourites ─────────────────────────────────────────────────────

    @Test
    fun `observeFavourites_filtersToFavoritedSongsOnly`() = runTest {
        fakeSongDao.emit(
            listOf(
                testSongEntity(id = "song-fav", title = "Zebra", isFavorite = true),
                testSongEntity(id = "song-not-fav", title = "Apple", isFavorite = false),
            ),
        )

        val songs = repository.observeFavourites().first()

        assertEquals(listOf("song-fav"), songs.map { it.id })
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
        dateAdded: Long = 0L,
        isFavorite: Boolean = false,
        playCount: Int = 0,
        lastPlayed: Long? = null,
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
        dateAdded = dateAdded,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayed = lastPlayed,
    )
}
