package com.rolla.musicplayer.feature.player

import app.cash.turbine.test
import com.rolla.musicplayer.core.data.repository.SongRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.RepeatMode
import com.rolla.musicplayer.core.model.ShuffleMode
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [PlayerViewModel].
 *
 * All flows are driven through a MockK-relaxed [PlaybackController] whose StateFlow
 * properties are replaced with controllable [MutableStateFlow] instances. Turbine is
 * used for every flow assertion so only the expected events are consumed.
 *
 * Offline: no network calls are made; all state is local and in-memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Controllable upstream flows --------------------------------------------------

    private val currentSongFlow = MutableStateFlow<Song?>(null)
    private val isPlayingFlow = MutableStateFlow(false)
    private val positionMsFlow = MutableStateFlow(0L)
    private val durationMsFlow = MutableStateFlow(0L)
    private val shuffleModeFlow = MutableStateFlow(ShuffleMode.OFF)
    private val repeatModeFlow = MutableStateFlow(RepeatMode.OFF)
    private val favouritesFlow = MutableStateFlow<List<Song>>(emptyList())

    // Mock dependency -------------------------------------------------------------

    /**
     * Relaxed so connect() (called in PlayerViewModel.init) is a no-op by default.
     * StateFlow properties are overridden with controllable MutableStateFlow instances.
     */
    private val playbackController: PlaybackController = mockk(relaxed = true) {
        every { currentSong } returns currentSongFlow
        every { isPlaying } returns isPlayingFlow
        every { positionMs } returns positionMsFlow
        every { durationMs } returns durationMsFlow
        every { shuffleMode } returns shuffleModeFlow
        every { repeatMode } returns repeatModeFlow
    }

    /** Relaxed so unrelated suspend calls (e.g. recordPlaybackStarted) are no-ops by default. */
    private val songRepository: SongRepository = mockk(relaxed = true) {
        every { observeFavourites() } returns favouritesFlow
    }

    private lateinit var viewModel: PlayerViewModel

    @Before
    fun setUp() {
        viewModel = PlayerViewModel(playbackController, songRepository)
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

    // positionMs ------------------------------------------------------------------

    @Test
    fun positionMs_givenNoEmission_emitsZero() = runTest {
        viewModel.positionMs.test {
            assertEquals("Initial positionMs must be 0", 0L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun positionMs_whenControllerEmitsPosition_thatPositionIsReflected() = runTest {
        val expectedPosition = 45_000L

        viewModel.positionMs.test {
            assertEquals(0L, awaitItem()) // initial
            positionMsFlow.value = expectedPosition
            assertEquals(
                "positionMs must equal the value emitted by the controller",
                expectedPosition,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun positionMs_emitsUpdatingPosition() = runTest {
        viewModel.positionMs.test {
            assertEquals(0L, awaitItem())
            positionMsFlow.value = 10_000L
            assertEquals(10_000L, awaitItem())
            positionMsFlow.value = 20_000L
            assertEquals(20_000L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    // durationMs ------------------------------------------------------------------

    @Test
    fun durationMs_givenNoEmission_emitsZero() = runTest {
        viewModel.durationMs.test {
            assertEquals("Initial durationMs must be 0", 0L, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun durationMs_whenControllerEmitsDuration_thatDurationIsReflected() = runTest {
        val expectedDuration = 240_000L

        viewModel.durationMs.test {
            assertEquals(0L, awaitItem()) // initial
            durationMsFlow.value = expectedDuration
            assertEquals(
                "durationMs must equal the value emitted by the controller",
                expectedDuration,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // shuffleMode -----------------------------------------------------------------

    @Test
    fun shuffleMode_givenNoEmission_emitsOff() = runTest {
        viewModel.shuffleMode.test {
            assertEquals("Initial shuffleMode must be OFF", ShuffleMode.OFF, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shuffleMode_whenControllerEmitsOn_emitsOn() = runTest {
        viewModel.shuffleMode.test {
            assertEquals(ShuffleMode.OFF, awaitItem()) // initial
            shuffleModeFlow.value = ShuffleMode.ON
            assertEquals(
                "shuffleMode must reflect ON when controller enables shuffle",
                ShuffleMode.ON,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun shuffleMode_whenControllerEmitsOffAfterOn_emitsOff() = runTest {
        shuffleModeFlow.value = ShuffleMode.ON

        viewModel.shuffleMode.test {
            assertEquals(ShuffleMode.ON, awaitItem()) // prefilled
            shuffleModeFlow.value = ShuffleMode.OFF
            assertEquals(
                "shuffleMode must reflect OFF when controller disables shuffle",
                ShuffleMode.OFF,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // repeatMode ------------------------------------------------------------------

    @Test
    fun repeatMode_givenNoEmission_emitsOff() = runTest {
        viewModel.repeatMode.test {
            assertEquals("Initial repeatMode must be OFF", RepeatMode.OFF, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repeatMode_whenControllerEmitsOne_emitsOne() = runTest {
        viewModel.repeatMode.test {
            assertEquals(RepeatMode.OFF, awaitItem()) // initial
            repeatModeFlow.value = RepeatMode.ONE
            assertEquals(
                "repeatMode must be ONE when controller cycles to repeat-one",
                RepeatMode.ONE,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repeatMode_whenControllerEmitsAll_emitsAll() = runTest {
        viewModel.repeatMode.test {
            assertEquals(RepeatMode.OFF, awaitItem()) // initial
            repeatModeFlow.value = RepeatMode.ALL
            assertEquals(
                "repeatMode must be ALL when controller cycles to repeat-all",
                RepeatMode.ALL,
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun repeatMode_cyclesThroughOffOneAll() = runTest {
        viewModel.repeatMode.test {
            assertEquals(RepeatMode.OFF, awaitItem())
            repeatModeFlow.value = RepeatMode.ONE
            assertEquals(RepeatMode.ONE, awaitItem())
            repeatModeFlow.value = RepeatMode.ALL
            assertEquals(RepeatMode.ALL, awaitItem())
            repeatModeFlow.value = RepeatMode.OFF
            assertEquals(RepeatMode.OFF, awaitItem())
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
    fun seekTo_delegatesToPlaybackControllerWithCorrectPosition() {
        val position = 60_000L
        viewModel.seekTo(position)
        verify(exactly = 1) { playbackController.seekTo(position) }
    }

    @Test
    fun seekTo_passesDifferentPositionsThrough() {
        viewModel.seekTo(0L)
        viewModel.seekTo(120_000L)
        verify(exactly = 1) { playbackController.seekTo(0L) }
        verify(exactly = 1) { playbackController.seekTo(120_000L) }
    }

    @Test
    fun setShuffle_givenModeOn_delegatesToPlaybackControllerWithOn() {
        viewModel.setShuffle(ShuffleMode.ON)
        verify(exactly = 1) { playbackController.setShuffle(ShuffleMode.ON) }
    }

    @Test
    fun setShuffle_givenModeOff_delegatesToPlaybackControllerWithOff() {
        viewModel.setShuffle(ShuffleMode.OFF)
        verify(exactly = 1) { playbackController.setShuffle(ShuffleMode.OFF) }
    }

    @Test
    fun cycleRepeatMode_delegatesToPlaybackController() {
        viewModel.cycleRepeatMode()
        verify(exactly = 1) { playbackController.cycleRepeatMode() }
    }

    @Test
    fun cycleRepeatMode_calledMultipleTimes_eachCallDelegated() {
        viewModel.cycleRepeatMode()
        viewModel.cycleRepeatMode()
        viewModel.cycleRepeatMode()
        verify(exactly = 3) { playbackController.cycleRepeatMode() }
    }

    // isCurrentSongFavorite ---------------------------------------------------------

    @Test
    fun isCurrentSongFavorite_givenNoCurrentSong_emitsFalse() = runTest {
        viewModel.isCurrentSongFavorite.test {
            assertFalse("Initial isCurrentSongFavorite must be false", awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isCurrentSongFavorite_givenCurrentSongNotInFavourites_emitsFalse() = runTest {
        val song = createTestSong(id = "song-5", title = "Imagine")
        favouritesFlow.value = listOf(createTestSong(id = "song-other", title = "Yesterday"))

        viewModel.isCurrentSongFavorite.test {
            assertFalse(awaitItem()) // initial

            // Value stays false (song is absent from favourites), so the underlying StateFlow
            // does not re-emit an equal consecutive value — assert the current value directly.
            currentSongFlow.value = song
            advanceUntilIdle()
            assertFalse(
                "isCurrentSongFavorite must be false when current song is absent from favourites",
                viewModel.isCurrentSongFavorite.value,
            )
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isCurrentSongFavorite_givenCurrentSongInFavourites_emitsTrue() = runTest {
        val song = createTestSong(id = "song-6", title = "Let It Be")

        viewModel.isCurrentSongFavorite.test {
            assertFalse(awaitItem()) // initial

            // Still false (not yet in favourites) — no new emission for an equal value.
            currentSongFlow.value = song
            advanceUntilIdle()
            expectNoEvents()

            favouritesFlow.value = listOf(song)
            assertTrue(
                "isCurrentSongFavorite must be true once current song appears in favourites",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isCurrentSongFavorite_updatesReactivelyWhenFavouritesChange() = runTest {
        val song = createTestSong(id = "song-7", title = "Hey Jude")
        currentSongFlow.value = song
        favouritesFlow.value = listOf(song)

        viewModel.isCurrentSongFavorite.test {
            assertTrue(awaitItem()) // prefilled true

            favouritesFlow.value = emptyList()
            assertFalse(
                "isCurrentSongFavorite must become false when the song is removed from favourites",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun isCurrentSongFavorite_updatesReactivelyWhenCurrentSongChanges() = runTest {
        val favoriteSong = createTestSong(id = "song-8", title = "Come Together")
        val otherSong = createTestSong(id = "song-9", title = "Something")
        favouritesFlow.value = listOf(favoriteSong)
        currentSongFlow.value = favoriteSong

        viewModel.isCurrentSongFavorite.test {
            assertTrue(awaitItem()) // prefilled true

            currentSongFlow.value = otherSong
            assertFalse(
                "isCurrentSongFavorite must become false when the current song changes to a non-favourite",
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    // toggleFavorite ------------------------------------------------------------------

    @Test
    fun toggleFavorite_givenCurrentSong_delegatesToSongRepositoryWithSongId() = runTest {
        val song = createTestSong(id = "song-10", title = "Across the Universe")

        // currentSong is WhileSubscribed(5_000L) — actively subscribe so the shared upstream
        // starts and viewModel.currentSong.value actually reflects the controller's emission.
        viewModel.currentSong.test {
            assertNull(awaitItem()) // initial

            currentSongFlow.value = song
            assertEquals(song, awaitItem())

            viewModel.toggleFavorite()
            advanceUntilIdle()

            coVerify(exactly = 1) { songRepository.toggleFavorite(song.id) }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun toggleFavorite_givenNoCurrentSong_doesNotCallSongRepository() = runTest {
        viewModel.toggleFavorite()
        advanceUntilIdle()

        coVerify(exactly = 0) { songRepository.toggleFavorite(any()) }
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
