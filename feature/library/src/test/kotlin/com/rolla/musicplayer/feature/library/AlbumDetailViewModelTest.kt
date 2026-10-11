package com.rolla.musicplayer.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeAlbumRepository
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
 * Unit tests for [AlbumDetailViewModel].
 *
 * `:feature:library` must never depend on `:app`'s route classes, so [SavedStateHandle] is
 * constructed directly from a raw `Map<String, Any?>` here, exactly mirroring how Navigation
 * Compose's type-safe `AlbumDetail(albumId: Long)` route populates it under the hood — the same
 * approach `PlaylistDetailViewModelTest` uses for `playlistId`/`kind`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeAlbumRepository = FakeAlbumRepository()
    private val playbackController: PlaybackController = mockk(relaxed = true)

    private fun createTestAlbum(id: Long = 1L): Album = Album(
        id = id,
        title = "Test Album",
        artist = "Test Artist",
        songCount = 3,
        artworkUri = "content://media/external/audio/albumart/$id",
    )

    private fun createTestSongs(count: Int, albumId: Long = 1L): List<Song> = (1..count).map { index ->
        Song(
            id = "song-$index",
            title = "Song $index",
            artist = "Artist $index",
            album = "Test Album",
            albumId = albumId,
            durationMs = 200_000L,
            trackNumber = index,
            year = 2000 + index,
            contentUri = "content://media/external/audio/media/$index",
            artworkUri = "content://media/external/audio/albumart/$index",
        )
    }

    private fun viewModel(albumId: Long = 1L): AlbumDetailViewModel = AlbumDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("albumId" to albumId)),
        albumRepository = fakeAlbumRepository,
        playbackController = playbackController,
    )

    // WhileSubscribed(5_000) means [AlbumDetailViewModel.uiState] only holds its declared
    // `initialValue = AlbumDetailUiState()` (isLoading = true) as long as nothing has subscribed
    // yet. Checking `.value` directly here (rather than going through Turbine) avoids racing the
    // MainDispatcherRule's UnconfinedTestDispatcher, which would otherwise run the upstream
    // combine() eagerly on first collection and could conflate straight past the loading state.
    @Test
    fun uiState_beforeAnySubscription_defaultsToLoading() = runTest {
        val vm = viewModel()

        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.album)
        assertEquals(emptyList<Song>(), vm.uiState.value.songs)
    }

    @Test
    fun uiState_givenAlbumAndSongsEmitted_reflectsBoth() = runTest {
        val album = createTestAlbum()
        val songs = createTestSongs(3)
        fakeAlbumRepository.emitAlbum(album)
        fakeAlbumRepository.emitSongs(songs)

        val vm = viewModel()

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertEquals(album, state.album)
            assertEquals(songs, state.songs)
        }
    }

    // FakeAlbumRepository's flows default to (null, emptyList()) until emit* is called, which is
    // exactly the not-found shape -- so this exercises the ViewModel never having received a
    // matching album for its id, distinct from the pre-subscription loading state above.
    @Test
    fun uiState_givenAlbumMissingFromRepository_isNotFoundState() = runTest {
        val vm = viewModel()

        vm.uiState.test {
            val state = expectMostRecentItem()
            assertFalse(state.isLoading)
            assertNull(state.album)
            assertEquals(emptyList<Song>(), state.songs)
        }
    }

    @Test
    fun savedStateHandle_albumId_isPassedToRepository() = runTest {
        viewModel(albumId = 42L)

        assertEquals(listOf(42L), fakeAlbumRepository.requestedAlbumIds)
        assertEquals(listOf(42L), fakeAlbumRepository.requestedSongsAlbumIds)
    }

    // Mutation: keep the old inline body (indexOfFirst, then playAll(songs, startIndex = index)) → RED (ruling 5).
    @Test
    fun onSongClick_playsTheAlbumFromTheTappedSong() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeAlbumRepository.emitAlbum(createTestAlbum())
        fakeAlbumRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onSongClick(songs[2])

        verify(exactly = 1) { playbackController.playFrom(songs, songs[2]) }
        collectJob.cancel()
    }

    // Delegation only: the "song no longer in the list → play it alone" fallback is pinned by PlaybackControllerTest
    // (`playFrom starts the list at the tapped song or plays it alone when absent`).
    // Mutation: restore the VM-side `if (index == -1) playbackController.play(song)` branch → RED.
    @Test
    fun onSongClick_givenSongNotInCurrentAlbum_stillDelegatesToPlayFrom() = runTest {
        val songs = createTestSongs(2)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeAlbumRepository.emitAlbum(createTestAlbum())
        fakeAlbumRepository.emitSongs(songs)
        advanceUntilIdle()

        val songNotInAlbum = createTestSongs(3, albumId = 2L).last()
        vm.onSongClick(songNotInAlbum)

        verify(exactly = 1) { playbackController.playFrom(songs, songNotInAlbum) }
        verify(exactly = 0) { playbackController.play(any()) }
        collectJob.cancel()
    }

    // Mutation: call playAll(songs, 0) directly (skipping setShuffle(OFF)) → RED.
    @Test
    fun onPlayClick_delegatesToPlayAllInOrder() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeAlbumRepository.emitAlbum(createTestAlbum())
        fakeAlbumRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onPlayClick()

        verify(exactly = 1) { playbackController.playAllInOrder(songs) }
        collectJob.cancel()
    }

    // Delegation only: the empty-list no-op is pinned by PlaybackControllerTest
    // (`playAllInOrder and shuffleAll on an empty list do nothing`).
    // Mutation: restore the VM-side `if (songs.isEmpty()) return` guard in onPlayClick() → RED.
    @Test
    fun onPlayClick_givenEmptyAlbum_delegatesTheEmptyListToPlayAllInOrder() = runTest {
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeAlbumRepository.emitAlbum(createTestAlbum())
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
        fakeAlbumRepository.emitAlbum(createTestAlbum())
        fakeAlbumRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onShuffleClick()

        verify(exactly = 1) { playbackController.shuffleAll(songs, vm.random) }
        collectJob.cancel()
    }

    // Delegation only: the empty-list no-op is pinned by PlaybackControllerTest
    // (`playAllInOrder and shuffleAll on an empty list do nothing`).
    // Mutation: restore the VM-side `if (songs.isEmpty()) return` guard in onShuffleClick() → RED.
    @Test
    fun onShuffleClick_givenEmptyAlbum_delegatesTheEmptyListToShuffleAll() = runTest {
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeAlbumRepository.emitAlbum(createTestAlbum())
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
