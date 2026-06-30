@file:Suppress("ktlint:standard:no-wildcard-imports", "WildcardImport")

package com.rolla.musicplayer.feature.library

import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.data.scanner.SyncResult
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // ── Dependencies ──────────────────────────────────────────────────────────

    private val fakeRepository = FakeSongRepository()

    // Strict mock: any un-stubbed call will throw, catching accidental invocations.
    private val libraryIndexer = mockk<LibraryIndexer>()

    // Relaxed mock: connect() is called in LibraryViewModel.init; we don't want to
    // stub it manually in every test.
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        viewModel = LibraryViewModel(
            songRepository = fakeRepository,
            libraryIndexer = libraryIndexer,
            playbackController = playbackController,
        )
    }

    // ── songs StateFlow ───────────────────────────────────────────────────────

    @Test
    fun `songs_emitsFromRepository`() = runTest {
        val expectedSongs = listOf(
            Song(
                id = "1",
                title = "Bohemian Rhapsody",
                artist = "Queen",
                album = "A Night at the Opera",
                albumId = 10L,
                durationMs = 354_000L,
                trackNumber = 11,
                year = 1975,
                contentUri = "content://media/external/audio/media/1",
                artworkUri = "content://media/external/audio/albumart/10",
            ),
        )
        val received = mutableListOf<List<Song>>()

        // Subscribing starts the WhileSubscribed upstream from fakeRepository.
        val collectJob = launch { viewModel.songs.collect { received.add(it) } }

        fakeRepository.emit(expectedSongs)
        advanceUntilIdle()

        assertEquals(
            "Last emission must equal the songs pushed into fakeRepository",
            expectedSongs,
            received.last(),
        )
        collectJob.cancel()
    }

    // ── onPermissionGranted ───────────────────────────────────────────────────

    @Test
    fun `onPermissionGranted_transitionsToScanningThenDone`() = runTest {
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 2, removed = 0)

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        assertEquals(
            "scanState must be Done(2, 0) after a successful sync",
            ScanState.Done(added = 2, removed = 0),
            viewModel.scanState.value,
        )
    }

    @Test
    fun `onPermissionGranted_whenAlreadyScanning_isIgnored`() = runTest {
        // Block sync() indefinitely so the ViewModel stays in Scanning state.
        val blockingSync = CompletableDeferred<SyncResult>()
        coEvery { libraryIndexer.sync() } coAnswers { blockingSync.await() }

        // First call: transitions to Scanning, then suspends inside sync().
        viewModel.onPermissionGranted()
        // Second call: the guard (_scanState is Scanning) short-circuits immediately.
        viewModel.onPermissionGranted()

        coVerify(exactly = 1) { libraryIndexer.sync() }

        // Unblock the in-flight coroutine so viewModelScope can finish cleanly.
        blockingSync.complete(SyncResult(added = 0, removed = 0))
        advanceUntilIdle()
    }

    @Test
    fun `onPermissionGranted_whenDone_isIgnored`() = runTest {
        // Once a scan completes successfully, subsequent calls (e.g. from config-change
        // recomposition) must be no-ops so users don't see a spinner flash on rotation.
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 1, removed = 0)

        viewModel.onPermissionGranted()
        advanceUntilIdle()
        assertTrue(
            "First scan must complete and reach Done",
            viewModel.scanState.value is ScanState.Done,
        )

        viewModel.onPermissionGranted()
        advanceUntilIdle()

        coVerify(exactly = 1) { libraryIndexer.sync() }
    }

    // ── play ──────────────────────────────────────────────────────────────────

    @Test
    fun `play_delegatesToPlaybackController`() {
        val song = Song(
            id = "42",
            title = "Stairway to Heaven",
            artist = "Led Zeppelin",
            album = "Led Zeppelin IV",
            albumId = 2L,
            durationMs = 482_000L,
            trackNumber = 4,
            year = 1971,
            contentUri = "content://media/external/audio/media/42",
            artworkUri = "",
        )

        viewModel.play(song)

        verify(exactly = 1) { playbackController.play(song) }
    }
}
