package com.rolla.musicplayer.core.data.repository

import com.rolla.musicplayer.core.database.dao.AlbumDao
import com.rolla.musicplayer.core.database.entity.SongEntity
import com.rolla.musicplayer.core.database.relation.AlbumSearchRow
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

class AlbumRepositoryImplTest {

    private val albumDao: AlbumDao = mockk()
    private val repository = AlbumRepositoryImpl(albumDao)

    private fun songEntity(id: String, title: String, trackNumber: Int? = 1) = SongEntity(
        id = id,
        mediaStoreId = id.toLong(),
        title = title,
        artist = "Artist",
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
    fun observeAlbum_mapsRowToDomain() = runTest {
        every { albumDao.observeAlbum(10L) } returns flowOf(
            AlbumSearchRow(
                albumId = 10L,
                title = "Greatest Hits",
                artist = "Artist A",
                songCount = 2,
                artworkUri = "art",
            ),
        )

        val album = repository.observeAlbum(10L).first()

        assertEquals(10L, album?.id)
        assertEquals("Greatest Hits", album?.title)
        assertEquals("Artist A", album?.artist)
        assertEquals(2, album?.songCount)
        assertEquals("art", album?.artworkUri)
    }

    @Test
    fun observeAlbum_nullRow_emitsNull() = runTest {
        every { albumDao.observeAlbum(999L) } returns flowOf(null)

        val album = repository.observeAlbum(999L).first()

        assertNull(album)
    }

    @Test
    fun observeAlbumSongs_mapsEntitiesToDomainInDaoOrder() = runTest {
        every { albumDao.observeAlbumSongs(10L) } returns flowOf(
            listOf(
                songEntity(id = "1", title = "First", trackNumber = 1),
                songEntity(id = "2", title = "Second", trackNumber = 2),
            ),
        )

        val songs = repository.observeAlbumSongs(10L).first()

        assertEquals(listOf("First", "Second"), songs.map { it.title })
    }

    @Test
    fun observeAlbumSongs_missingAlbum_emitsEmptyList() = runTest {
        every { albumDao.observeAlbumSongs(999L) } returns flowOf(emptyList())

        val songs = repository.observeAlbumSongs(999L).first()

        assertTrue(songs.isEmpty())
    }

    @Test
    fun observeAlbumSongs_delegatesRequestedAlbumIdToDao() = runTest {
        every { albumDao.observeAlbumSongs(42L) } returns flowOf(emptyList())

        repository.observeAlbumSongs(42L).first()

        verify { albumDao.observeAlbumSongs(42L) }
    }
}
