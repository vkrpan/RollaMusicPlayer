package com.rolla.musicplayer.feature.player

import app.cash.turbine.test
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [MiniPlayerViewModel].
 *
 * [MiniPlayerViewModel] exposes a focused subset of [PlaybackController] state —
 * [currentSong] and [isPlaying] — plus three transport commands. Each test verifies
 * either state propagation (via Turbine) or command delegation (via MockK verify).
 *
 * Offline: no network calls are made; all state is local and in-memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MiniPlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Controllable upstream flows --------------------------------------------------

    private val currentSongFlow = MutableStateFlow<Song?>(null)
    private val isPlayingFlow = MutableStateFlow(false)

    // Mock dependency -------------------------------------------------------------

    /**
     * Relaxed so connect() (called in MiniPlayerViewModel.init) is a no-op by default.
     * Only the two StateFlow properties consumed by MiniPlayerViewModel are stubbed.
     */
    private val playbackController: PlaybackController = mockk(relaxed = true) {
        every { currentSong } returns currentSongFlow
        every { isPlaying } returns isPlayingFlow
    }

    private lateinit var viewModel: MiniPlayerViewModel

    @Before
    fun setUp() {
        viewModel = MiniPlayerViewModel(playbackController)
    }

    // init ------------------------------------------------------------------------

    @Test
    fun init_callsConnectOnPlaybackController() {
        // ViewModel is constructed in setUp(); connect() must have been called exactly once.
        verify(exactly = 1) { playbackController.connect() }
    }

    // currentSong -----------------------------------------------------------------

    @Test
    fun currentSong_givenNoEmission_emitsInitialNull() = runTest {
        viewModel.currentSong.test {
            assertNull("Initial currentSong must be null", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun currentSong_whenControllerEmitsSong_thatSongIsReflected() = runTest {
        val expected = createTestSong(id = "song-1", title = "Bohemian Rhapsody")

        viewModel.currentSong.test {
            assertNull(awaitItem()) // initial null
            currentSongFlow.value = expected
            assertEquals(
                "currentSong must reflect the song emitted by the controller",
                expected,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun currentSong_whenControllerClearsSong_emitsNull() = runTest {
        val song = createTestSong(id = "song-2", title = "Stairway to Heaven")
        currentSongFlow.value = song

        viewModel.currentSong.test {
            assertEquals(song, awaitItem()) // prefilled value
            currentSongFlow.value = null
            assertNull(
                "currentSong must emit null when the controller clears the song",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun currentSong_emitsMultipleConsecutiveSongs() = runTest {
        val first = createTestSong(id = "song-3", title = "Hotel California")
        val second = createTestSong(id = "song-4", title = "Smells Like Teen Spirit")

        viewModel.currentSong.test {
            assertNull(awaitItem())
            currentSongFlow.value = first
            assertEquals(first, awaitItem())
            currentSongFlow.value = second
            assertEquals(second, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // isPlaying -------------------------------------------------------------------

    @Test
    fun isPlaying_givenNoEmission_emitsFalse() = runTest {
        viewModel.isPlaying.test {
            assertFalse("Initial isPlaying must be false", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isPlaying_whenControllerEmitsTrue_emitsTrue() = runTest {
        viewModel.isPlaying.test {
            assertFalse(awaitItem()) // initial
            isPlayingFlow.value = true
            assertTrue(
                "isPlaying must reflect true when the controller is playing",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isPlaying_whenControllerEmitsFalseAfterTrue_emitsFalse() = runTest {
        isPlayingFlow.value = true

        viewModel.isPlaying.test {
            assertTrue(awaitItem()) // prefilled
            isPlayingFlow.value = false
            assertFalse(
                "isPlaying must reflect false when the controller pauses",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isPlaying_togglesBetweenTrueAndFalse() = runTest {
        viewModel.isPlaying.test {
            assertFalse(awaitItem())
            isPlayingFlow.value = true
            assertTrue(awaitItem())
            isPlayingFlow.value = false
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // command delegation ----------------------------------------------------------

    @Test
    fun togglePlayPause_delegatesToPlaybackController() {
        viewModel.togglePlayPause()
        verify(exactly = 1) { playbackController.togglePlayPause() }
    }

    @Test
    fun next_delegatesToPlaybackController() {
        viewModel.next()
        verify(exactly = 1) { playbackController.next() }
    }

    @Test
    fun previous_delegatesToPlaybackController() {
        viewModel.previous()
        verify(exactly = 1) { playbackController.previous() }
    }

    @Test
    fun togglePlayPause_calledMultipleTimes_eachCallDelegated() {
        viewModel.togglePlayPause()
        viewModel.togglePlayPause()
        verify(exactly = 2) { playbackController.togglePlayPause() }
    }

    @Test
    fun next_calledMultipleTimes_eachCallDelegated() {
        viewModel.next()
        viewModel.next()
        viewModel.next()
        verify(exactly = 3) { playbackController.next() }
    }

    @Test
    fun previous_calledMultipleTimes_eachCallDelegated() {
        viewModel.previous()
        viewModel.previous()
        verify(exactly = 2) { playbackController.previous() }
    }

    // test factory ----------------------------------------------------------------

    @Suppress("LongParameterList")
    private fun createTestSong(
        id: String = "song-default",
        title: String = "Test Song",
        artist: String = "Test Artist",
        album: String = "Test Album",
        albumId: Long = 1L,
        durationMs: Long = 180_000L,
        trackNumber: Int? = 1,
        year: Int? = 2023,
        contentUri: String = "content://media/external/audio/media/1",
        artworkUri: String = "content://media/external/audio/albumart/1",
    ) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        contentUri = contentUri,
        artworkUri = artworkUri,
    )
}
