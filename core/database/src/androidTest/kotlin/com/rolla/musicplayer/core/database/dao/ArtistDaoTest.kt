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
class ArtistDaoTest {

    private lateinit var database: MusicDatabase
    private lateinit var songDao: SongDao
    private lateinit var artistDao: ArtistDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, MusicDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        songDao = database.songDao()
        artistDao = database.artistDao()
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
    fun observeArtist_groupsByArtist_withCorrectCounts() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "A1")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album One"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "A2")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album One"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "A3")
                    .copy(artist = "Artist A", albumId = 200L, album = "Album Two"),
                buildSongEntity(id = "4", mediaStoreId = 4L, title = "B1")
                    .copy(artist = "Artist B", albumId = 300L, album = "Unrelated Album"),
            ),
        )

        val artist = artistDao.observeArtist("Artist A").first()

        assertEquals("Artist A", artist?.name)
        assertEquals(2, artist?.albumCount)
        assertEquals(3, artist?.songCount)
    }

    @Test
    fun observeArtist_missingArtist_emitsNull() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Artist A")))

        val artist = artistDao.observeArtist("Unknown Artist").first()

        assertNull(artist)
    }

    @Test
    fun observeArtistAlbums_groupsByAlbumId_orderedByTitle() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Z1")
                    .copy(artist = "Artist A", albumId = 100L, album = "Zebra Album", artworkUri = "art-100"),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "A1")
                    .copy(artist = "Artist A", albumId = 200L, album = "Alpha Album", artworkUri = "art-200"),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "A2")
                    .copy(artist = "Artist A", albumId = 200L, album = "Alpha Album", artworkUri = "art-200"),
                buildSongEntity(id = "4", mediaStoreId = 4L, title = "B1")
                    .copy(artist = "Artist B", albumId = 300L, album = "Unrelated Album"),
            ),
        )

        val albums = artistDao.observeArtistAlbums("Artist A").first()

        assertEquals(listOf("Alpha Album", "Zebra Album"), albums.map { it.title })
        assertEquals(200L, albums.first().albumId)
        assertEquals(2, albums.first().songCount)
        assertEquals("art-200", albums.first().artworkUri)
    }

    @Test
    fun observeArtistAlbums_missingArtist_emitsEmptyList() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Artist A")))

        val albums = artistDao.observeArtistAlbums("Unknown Artist").first()

        assertTrue(albums.isEmpty())
    }

    @Test
    fun observeArtistSongs_missingArtist_emitsEmptyList() = runTest {
        songDao.upsertSongs(listOf(buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Artist A")))

        val songs = artistDao.observeArtistSongs("Unknown Artist").first()

        assertTrue(songs.isEmpty())
    }

    @Test
    fun observeArtistSongs_ordersByAlbumThenTrackNumberAscending() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Zebra Track 1")
                    .copy(artist = "Artist A", albumId = 200L, album = "Zebra Album", trackNumber = 1),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Alpha Track 2")
                    .copy(artist = "Artist A", albumId = 100L, album = "Alpha Album", trackNumber = 2),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Alpha Track 1")
                    .copy(artist = "Artist A", albumId = 100L, album = "Alpha Album", trackNumber = 1),
            ),
        )

        val songs = artistDao.observeArtistSongs("Artist A").first()

        // Alpha Album (album_id 100) sorts before Zebra Album (album_id 200) by title;
        // within Alpha Album, track_number ascending places track 1 before track 2.
        assertEquals(listOf("3", "2", "1"), songs.map { it.id })
    }

    @Test
    fun observeArtistSongs_sortsUntaggedTrackNumbersLastWithinAlbum() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Untagged A")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album", trackNumber = null),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Tagged")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album", trackNumber = 1),
                buildSongEntity(id = "3", mediaStoreId = 3L, title = "Untagged B")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album", trackNumber = null),
            ),
        )

        val songs = artistDao.observeArtistSongs("Artist A").first()

        // Tagged track first, then untagged tracks ordered alphabetically by title as the tiebreak.
        assertEquals(listOf("2", "1", "3"), songs.map { it.id })
    }

    @Test
    fun observeArtistSongs_tiesOnTrackNumber_breakByTitleAscending() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L, title = "Zebra")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album", trackNumber = 1),
                buildSongEntity(id = "2", mediaStoreId = 2L, title = "Apple")
                    .copy(artist = "Artist A", albumId = 100L, album = "Album", trackNumber = 1),
            ),
        )

        val songs = artistDao.observeArtistSongs("Artist A").first()

        assertEquals(listOf("2", "1"), songs.map { it.id })
    }

    @Test
    fun observeArtistSongs_onlyReturnsSongsForRequestedArtist() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Artist A"),
                buildSongEntity(id = "2", mediaStoreId = 2L).copy(artist = "Artist B"),
            ),
        )

        val songs = artistDao.observeArtistSongs("Artist A").first()

        assertEquals(listOf("1"), songs.map { it.id })
    }

    // Mutation: drop COLLATE NOCASE from the outer ORDER BY → "queen" sorts after "Zappa" → RED (Review Focus 1).
    @Test
    fun observeArtists_groupsByExactName_ordersCaseInsensitivelyThenBinary() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L).copy(artist = "Zappa", albumId = 1L, album = "Z1"),
                buildSongEntity(id = "2", mediaStoreId = 2L).copy(artist = "queen", albumId = 2L, album = "Q1"),
                buildSongEntity(id = "3", mediaStoreId = 3L).copy(artist = "Queen", albumId = 3L, album = "Q2"),
                buildSongEntity(id = "4", mediaStoreId = 4L).copy(artist = "Queen", albumId = 4L, album = "Q3"),
                buildSongEntity(id = "5", mediaStoreId = 5L).copy(artist = "ABBA", albumId = 5L, album = "A1"),
                // A second "Queen" song on album 3: the album count must stay 2 while the song count reaches 3.
                buildSongEntity(id = "6", mediaStoreId = 6L).copy(artist = "Queen", albumId = 3L, album = "Q2"),
            ),
        )

        val artists = artistDao.observeArtists().first()

        assertEquals(listOf("ABBA", "Queen", "queen", "Zappa"), artists.map { it.name })
        assertEquals(2, artists.single { it.name == "Queen" }.albumCount)
        assertEquals(3, artists.single { it.name == "Queen" }.songCount)
    }

    // The expected album ("alpha") is the MIDDLE row in scan order, so no position-based pick can pass by accident.
    // Mutations: (a) MIN(album) without COLLATE NOCASE → binary min is "Mid" ('M' < 'Z' < 'a') → art/mid → RED;
    // (b) delete the MIN(...) AS firstAlbum line → the bare column comes from an unspecified row of the group (the
    // first or the last in practice: art/zeta or art/mid), never the middle one → RED.
    @Test
    fun observeArtists_artworkComesFromTheAlphabeticallyFirstAlbum() = runTest {
        songDao.upsertSongs(
            listOf(
                buildSongEntity(id = "1", mediaStoreId = 1L)
                    .copy(artist = "X", albumId = 1L, album = "Zeta", artworkUri = "art/zeta"),
                buildSongEntity(id = "2", mediaStoreId = 2L)
                    .copy(artist = "X", albumId = 2L, album = "alpha", artworkUri = "art/alpha"),
                buildSongEntity(id = "3", mediaStoreId = 3L)
                    .copy(artist = "X", albumId = 3L, album = "Mid", artworkUri = "art/mid"),
            ),
        )

        assertEquals("art/alpha", artistDao.observeArtists().first().single().artworkUri)
        assertEquals("art/alpha", artistDao.observeArtist("X").first()?.artworkUri)
    }
}
