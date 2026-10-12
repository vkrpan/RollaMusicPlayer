package com.rolla.musicplayer.feature.library

import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.testing.FakeAlbumRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumsTabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mutation: seed stateIn with emptyList() → value is [] before any subscriber → RED (ruling 6).
    @Test
    fun `albums are null before the first emission`() {
        assertNull(AlbumsTabViewModel(FakeAlbumRepository()).albums.value)
    }

    // Mutation: observe albumRepository.observeAlbum(0L)-derived data or emptyFlow() instead of observeAlbums() → RED.
    @Test
    fun `albums mirror the repository while collected`() = runTest {
        val repository = FakeAlbumRepository()
        val viewModel = AlbumsTabViewModel(repository)
        val job = launch { viewModel.albums.collect {} }
        // Same title, different ids and artists: the list must carry both, in the repository's order.
        val albums = listOf(
            album(id = 30L, title = "Greatest Hits", artist = "Queen"),
            album(id = 40L, title = "Greatest Hits", artist = "ABBA"),
        )

        repository.emitAlbums(albums)
        advanceUntilIdle()

        assertEquals(albums, viewModel.albums.value)
        job.cancel()
    }

    private fun album(id: Long, title: String, artist: String) = Album(
        id = id,
        title = title,
        artist = artist,
        songCount = 1,
        artworkUri = "content://media/external/audio/albumart/$id",
    )
}
