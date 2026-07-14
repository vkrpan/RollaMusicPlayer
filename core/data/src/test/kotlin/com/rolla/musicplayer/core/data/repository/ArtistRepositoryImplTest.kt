package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.ArtistDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.AlbumSearchRow
import com.rolla.musicplayer.core.database.relation.ArtistSearchRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistRepositoryImplTest {

    private val artistDao: ArtistDao = mockk()
    private val repository = ArtistRepositoryImpl(artistDao)

    private fun songEntity(id: String, title: String, trackNumber: Int? = 1) = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist A",
        album = "Album",
        albumId = 10L,
        durationMs = 180_000L,
        trackNumber = trackNumber,
        year = 2024,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "content://media/external/audio/albumart/10",
        dateModified = 1_000L,
    )

    @Test
    fun observeArtist_mapsRowToDomain() = runTest {
        every { artistDao.observeArtist("Artist A") } returns flowOf(
            ArtistSearchRow(
                name = "Artist A",
                albumCount = 2,
                songCount = 5,
            ),
        )

        val artist = repository.observeArtist("Artist A").first()

        assertEquals("Artist A", artist?.name)
        assertEquals(2, artist?.albumCount)
        assertEquals(5, artist?.songCount)
    }

    @Test
    fun observeArtist_nullRow_emitsNull() = runTest {
        every { artistDao.observeArtist("Unknown") } returns flowOf(null)

        val artist = repository.observeArtist("Unknown").first()

        assertNull(artist)
    }

    @Test
    fun observeArtistAlbums_mapsRowsToDomainInDaoOrder() = runTest {
        every { artistDao.observeArtistAlbums("Artist A") } returns flowOf(
            listOf(
                AlbumSearchRow(
                    albumId = 10L,
                    title = "Album One",
                    artist = "Artist A",
                    songCount = 3,
                    artworkUri = "art-10",
                ),
                AlbumSearchRow(
                    albumId = 20L,
                    title = "Album Two",
                    artist = "Artist A",
                    songCount = 2,
                    artworkUri = "art-20",
                ),
            ),
        )

        val albums = repository.observeArtistAlbums("Artist A").first()

        assertEquals(listOf("Album One", "Album Two"), albums.map { it.title })
    }

    @Test
    fun observeArtistAlbums_noAlbums_emitsEmptyList() = runTest {
        every { artistDao.observeArtistAlbums("Unknown") } returns flowOf(emptyList())

        val albums = repository.observeArtistAlbums("Unknown").first()

        assertTrue(albums.isEmpty())
    }

    @Test
    fun observeArtistSongs_mapsEntitiesToDomainInDaoOrder() = runTest {
        every { artistDao.observeArtistSongs("Artist A") } returns flowOf(
            listOf(
                songEntity(id = "1", title = "First", trackNumber = 1),
                songEntity(id = "2", title = "Second", trackNumber = 2),
            ),
        )

        val songs = repository.observeArtistSongs("Artist A").first()

        assertEquals(listOf("First", "Second"), songs.map { it.title })
    }

    @Test
    fun observeArtistSongs_missingArtist_emitsEmptyList() = runTest {
        every { artistDao.observeArtistSongs("Unknown") } returns flowOf(emptyList())

        val songs = repository.observeArtistSongs("Unknown").first()

        assertTrue(songs.isEmpty())
    }

    @Test
    fun observeArtistSongs_delegatesRequestedArtistNameToDao() = runTest {
        every { artistDao.observeArtistSongs("Artist B") } returns flowOf(emptyList())

        repository.observeArtistSongs("Artist B").first()

        verify { artistDao.observeArtistSongs("Artist B") }
    }
}
