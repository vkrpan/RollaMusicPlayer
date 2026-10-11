package com.rolla.musicplayer.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeArtistRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

// Seeds the VM's Random test seam; the shuffle test verifies that exact instance is forwarded to the controller.
private const val SHUFFLE_SEED = 7L

/**
 * Unit tests for [ArtistDetailViewModel].
 *
 * `:feature:library` must never depend on `:app`'s route classes, so [SavedStateHandle] is
 * constructed directly from a raw `Map<String, Any?>` here, exactly mirroring how Navigation
 * Compose's type-safe `ArtistDetail(artistName: String)` route populates it under the hood, the
 * same approach `AlbumDetailViewModelTest` uses for `albumId`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeArtistRepository = FakeArtistRepository()
    private val playbackController: PlaybackController = mockk(relaxed = true)

    private fun createTestArtist(name: String = "Test Artist"): Artist = Artist(
        id = 1L,
        name = name,
        albumCount = 2,
        songCount = 3,
    )

    private fun createTestAlbums(count: Int): List<Album> = (1..count).map { index ->
        Album(
            id = index.toLong(),
            title = "Album $index",
            artist = "Test Artist",
            songCount = 3,
            artworkUri = "content://media/external/audio/albumart/$index",
        )
    }

    private fun createTestSongs(count: Int, albumId: Long = 1L): List<Song> = (1..count).map { index ->
        Song(
            id = "song-$index",
            title = "Song $index",
            artist = "Test Artist",
            album = "Test Album",
            albumId = albumId,
            durationMs = 200_000L,
            trackNumber = index,
            year = 2000 + index,
            contentUri = "content://media/external/audio/media/$index",
            artworkUri = "content://media/external/audio/albumart/$index",
        )
    }

    private fun viewModel(artistName: String = "Queen"): ArtistDetailViewModel = ArtistDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("artistName" to artistName)),
        artistRepository = fakeArtistRepository,
        playbackController = playbackController,
    )

    // WhileSubscribed(5_000) means [ArtistDetailViewModel.uiState] only holds its declared
    // `initialValue = ArtistDetailUiState()` (isLoading = true) as long as nothing has subscribed
    // yet. Checking `.value` directly here (rather than going through Turbine) avoids racing the
    // MainDispatcherRule's UnconfinedTestDispatcher, which would otherwise run the upstream
    // combine() eagerly on first collection and could conflate straight past the loading state.
    @Test
    fun uiState_beforeAnySubscription_defaultsToLoading() = runTest {
        val vm = viewModel()

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.artist)
        assertEquals(emptyList<Album>(), vm.uiState.value.albums)
        assertEquals(emptyList<Song>(), vm.uiState.value.songs)
    }

    @Test
    fun uiState_givenArtistAlbumsAndSongsEmitted_reflectsAll() = runTest {
        val artist = createTestArtist()
        val albums = createTestAlbums(2)
        val songs = createTestSongs(3)
        fakeArtistRepository.emitArtist(artist)
        fakeArtistRepository.emitAlbums(albums)
        fakeArtistRepository.emitSongs(songs)

        val vm = viewModel()

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(artist, state.artist)
            assertEquals(albums, state.albums)
            assertEquals(songs, state.songs)
        }
    }

    // FakeArtistRepository's flows default to (null, emptyList(), emptyList()) until emit* is
    // called, which is exactly the not-found shape, so this exercises the ViewModel never having
    // received a matching artist for its name, distinct from the pre-subscription loading state
    // above.
    @Test
    fun uiState_givenArtistMissingFromRepository_isNotFoundState() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertNull(state.artist)
            assertEquals(emptyList<Album>(), state.albums)
            assertEquals(emptyList<Song>(), state.songs)
        }
    }

    @Test
    fun savedStateHandle_artistName_isPassedToRepository() = runTest {
        viewModel(artistName = "Queen")

        assertEquals(listOf("Queen"), fakeArtistRepository.requestedArtistNames)
        assertEquals(listOf("Queen"), fakeArtistRepository.requestedAlbumsArtistNames)
        assertEquals(listOf("Queen"), fakeArtistRepository.requestedSongsArtistNames)
    }

    // Mutation: keep the old inline body (indexOfFirst, then playAll(songs, startIndex = index)) → RED (ruling 5).
    @Test
    fun onSongClick_playsTheArtistsSongsFromTheTappedSong() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onSongClick(songs[2])

        verify(exactly = 1) { playbackController.playFrom(songs, songs[2]) }
        collectJob.cancel()
    }

    // Delegation only: the "song no longer in the list → play it alone" fallback is pinned by PlaybackControllerTest
    // (`playFrom starts the list at the tapped song or plays it alone when absent`).
    // Mutation: restore the VM-side `if (index == -1) playbackController.play(song)` branch → RED.
    @Test
    fun onSongClick_givenSongNotInCurrentList_stillDelegatesToPlayFrom() = runTest {
        val songs = createTestSongs(2)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        val songNotInList = createTestSongs(3, albumId = 2L).last()
        vm.onSongClick(songNotInList)

        verify(exactly = 1) { playbackController.playFrom(songs, songNotInList) }
        verify(exactly = 0) { playbackController.play(any()) }
        collectJob.cancel()
    }

    // Mutation: call playAll(songs, 0) directly (skipping setShuffle(OFF)) → RED.
    @Test
    fun onPlayClick_delegatesToPlayAllInOrder() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onPlayClick()

        verify(exactly = 1) { playbackController.playAllInOrder(songs) }
        collectJob.cancel()
    }

    // Delegation only: the empty-list no-op is pinned by PlaybackControllerTest
    // (`playAllInOrder and shuffleAll on an empty list do nothing`).
    // Mutation: restore the VM-side `if (songs.isEmpty()) return` guard in onPlayClick() → RED.
    @Test
    fun onPlayClick_givenEmptyArtist_delegatesTheEmptyListToPlayAllInOrder() = runTest {
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        advanceUntilIdle()

        vm.onPlayClick()

        verify(exactly = 1) { playbackController.playAllInOrder(emptyList()) }
        collectJob.cancel()
    }

    // Mutation: pass Random.Default instead of the injected random → RED.
    @Test
    fun onShuffleClick_delegatesToShuffleAllWithTheViewModelsRandom() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        vm.random = Random(SHUFFLE_SEED)
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onShuffleClick()

        verify(exactly = 1) { playbackController.shuffleAll(songs, vm.random) }
        collectJob.cancel()
    }

    // Delegation only: the empty-list no-op is pinned by PlaybackControllerTest
    // (`playAllInOrder and shuffleAll on an empty list do nothing`).
    // Mutation: restore the VM-side `if (songs.isEmpty()) return` guard in onShuffleClick() → RED.
    @Test
    fun onShuffleClick_givenEmptyArtist_delegatesTheEmptyListToShuffleAll() = runTest {
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        advanceUntilIdle()

        vm.onShuffleClick()

        verify(exactly = 1) { playbackController.shuffleAll(emptyList(), vm.random) }
        collectJob.cancel()
    }

    @Test
    fun init_connectsPlaybackController() = runTest {
        viewModel()

        verify { playbackController.connect() }
    }
}
