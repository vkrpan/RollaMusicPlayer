package com.rolla.musicplayer.home

import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.data.scanner.SyncResult
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import com.rolla.musicplayer.feature.library.ScanState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * The first-grant library sync, moved with its three tests from LibraryViewModel (Phase 2 ruling 2). The test bodies
 * are verbatim; only the constructor changed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Strict mock: any un-stubbed call will throw, catching accidental invocations.
    private val libraryIndexer = mockk<LibraryIndexer>()

    private lateinit var viewModel: HomeViewModel

    @Before
    fun setup() {
        viewModel = HomeViewModel(libraryIndexer)
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

    @Test
    fun `onPermissionGranted_syncFailure_setsError`() = runTest {
        coEvery { libraryIndexer.sync() } throws IllegalStateException("boom")
        viewModel.onPermissionGranted()
        advanceUntilIdle()
        assertEquals(ScanState.Error("boom"), viewModel.scanState.value)
    }
}
