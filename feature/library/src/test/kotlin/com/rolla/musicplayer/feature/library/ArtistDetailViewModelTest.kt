package com.rolla.musicplayer.feature.library

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeArtistRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

// Its first nextInt(3) is non-zero (asserted in the test), so a hardcoded start index of 0 fails.
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

    @Test
    fun onSongClick_playsQueueStartingAtTappedSongIndex() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onSongClick(songs[2])

        verify { playbackController.playAll(songs, startIndex = 2) }
        collectJob.cancel()
    }

    @Test
    fun onSongClick_givenSongNotInCurrentList_fallsBackToPlaySong() = runTest {
        val songs = createTestSongs(2)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        val songNotInList = createTestSongs(3, albumId = 2L).last()
        vm.onSongClick(songNotInList)

        verify { playbackController.play(songNotInList) }
        verify(exactly = 0) { playbackController.playAll(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun onPlayClick_disablesShuffleThenPlaysAllFromZero() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onPlayClick()

        verify { playbackController.setShuffle(ShuffleMode.OFF) }
        verify { playbackController.playAll(songs, startIndex = 0) }
        collectJob.cancel()
    }

    @Test
    fun onPlayClick_givenEmptyArtist_leavesControllerUntouched() = runTest {
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        advanceUntilIdle()

        vm.onPlayClick()

        verify(exactly = 0) { playbackController.setShuffle(any()) }
        verify(exactly = 0) { playbackController.playAll(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun onShuffleClick_enablesShuffleThenPlaysAllFromARandomStart() = runTest {
        val songs = createTestSongs(3)
        val vm = viewModel()
        vm.random = Random(SHUFFLE_SEED)
        val expectedStart = Random(SHUFFLE_SEED).nextInt(songs.size)
        assertNotEquals("Seed must pick a non-zero start, or a hardcoded 0 would pass", 0, expectedStart)
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        fakeArtistRepository.emitSongs(songs)
        advanceUntilIdle()

        vm.onShuffleClick()

        verifyOrder {
            playbackController.setShuffle(ShuffleMode.ON)
            playbackController.playAll(songs, expectedStart)
        }
        collectJob.cancel()
    }

    @Test
    fun onShuffleClick_givenEmptyArtist_leavesControllerUntouched() = runTest {
        val vm = viewModel()
        val collectJob = launch { vm.uiState.collect {} }
        fakeArtistRepository.emitArtist(createTestArtist())
        advanceUntilIdle()

        vm.onShuffleClick()

        verify(exactly = 0) { playbackController.setShuffle(any()) }
        verify(exactly = 0) { playbackController.playAll(any(), any()) }
        collectJob.cancel()
    }

    @Test
    fun init_connectsPlaybackController() = runTest {
        viewModel()

        verify { playbackController.connect() }
    }
}
