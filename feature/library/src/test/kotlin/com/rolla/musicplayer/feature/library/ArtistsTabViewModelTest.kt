package com.rolla.musicplayer.feature.library

import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.testing.FakeArtistRepository
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
class ArtistsTabViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Mutation: seed stateIn with emptyList() → value is [] before any subscriber → RED (ruling 6).
    @Test
    fun `artists are null before the first emission`() {
        assertNull(ArtistsTabViewModel(FakeArtistRepository()).artists.value)
    }

    // Mutation: observe artistRepository.observeArtist("")-derived data or emptyFlow() instead of
    // observeArtists() → RED.
    @Test
    fun `artists mirror the repository while collected`() = runTest {
        val repository = FakeArtistRepository()
        val viewModel = ArtistsTabViewModel(repository)
        val job = launch { viewModel.artists.collect {} }
        // Names differ only in case: both artists must come through, in the repository's order.
        val artists = listOf(Artist(1L, "Queen", 2, 5, "a"), Artist(2L, "queen", 1, 1, "b"))

        repository.emitArtists(artists)
        advanceUntilIdle()

        assertEquals(artists, viewModel.artists.value)
        job.cancel()
    }
}
