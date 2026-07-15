package com.rolla.musicplayer.feature.settings

import app.cash.turbine.test
import com.rolla.musicplayer.core.data.artwork.AlbumArtworkCache
import com.rolla.musicplayer.core.data.repository.DEFAULT_PLAYBACK_SPEED
import com.rolla.musicplayer.core.data.repository.SettingsRepository
import com.rolla.musicplayer.core.data.scanner.LibraryIndexer
import com.rolla.musicplayer.core.data.scanner.SyncResult
import com.rolla.musicplayer.core.datastore.EqualizerPreferences
import com.rolla.musicplayer.core.model.ThemeMode
import com.rolla.musicplayer.core.testing.FakeEqualizerPreferences
import com.rolla.musicplayer.core.testing.FakeSettingsRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/** API level Material You dynamic color first exists on -- Android 12 (S). */
private const val API_S = 31

/** Unit tests for [SettingsViewModel]. */
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSettingsRepository = FakeSettingsRepository()
    private val fakeEqualizerPreferences = FakeEqualizerPreferences()
    private val libraryIndexer: LibraryIndexer = mockk {
        coEvery { sync() } returns SyncResult(added = 0, removed = 0)
    }
    private val albumArtworkCache: AlbumArtworkCache = mockk(relaxed = true)

    private fun createViewModel(
        settingsRepository: SettingsRepository = fakeSettingsRepository,
        equalizerPreferences: EqualizerPreferences = fakeEqualizerPreferences,
        indexer: LibraryIndexer = libraryIndexer,
        artworkCache: AlbumArtworkCache = albumArtworkCache,
    ): SettingsViewModel = SettingsViewModel(settingsRepository, equalizerPreferences, indexer, artworkCache)

    // ── uiState defaults ─────────────────────────────────────────────────────

    @Test
    fun uiState_beforeAnyEmission_hasDefaultValues() = runTest {
        val viewModel = createViewModel()

        assertEquals(SettingsUiState(), viewModel.uiState.value)
    }

    // ── uiState reflects repository emissions ────────────────────────────────

    @Test
    fun uiState_themeModeEmission_isReflected() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(ThemeMode.SYSTEM, awaitItem().themeMode)

            fakeSettingsRepository.setThemeMode(ThemeMode.DARK)

            assertEquals(ThemeMode.DARK, awaitItem().themeMode)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_useDynamicColorEmission_isReflected() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().useDynamicColor)

            fakeSettingsRepository.setUseDynamicColor(true)

            assertEquals(true, awaitItem().useDynamicColor)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_playbackSpeedEmission_isReflected() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(DEFAULT_PLAYBACK_SPEED, awaitItem().playbackSpeed)

            fakeSettingsRepository.setPlaybackSpeed(1.5f)

            assertEquals(1.5f, awaitItem().playbackSpeed)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_skipSilenceEmission_isReflected() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().skipSilence)

            fakeSettingsRepository.setSkipSilence(true)

            assertEquals(true, awaitItem().skipSilence)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_equalizerEnabledEmission_isReflected() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().equalizerEnabled)

            fakeEqualizerPreferences.setEnabled(true)

            assertEquals(true, awaitItem().equalizerEnabled)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── isDynamicColorAvailable ───────────────────────────────────────────────

    @Test
    fun uiState_sdkBelowS_isDynamicColorAvailableFalse() = runTest {
        val viewModel = createViewModel()
        viewModel.sdkIntProvider = { API_S - 1 }

        // .value alone would only surface the initialValue baked in before this override (see
        // SettingsViewModel's field-initialization order) -- collecting forces a fresh emission
        // from the upstream combine(), which re-evaluates isDynamicColorAvailable() against the
        // now-overridden provider.
        viewModel.uiState.test {
            assertEquals(false, awaitItem().isDynamicColorAvailable)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_sdkAtLeastS_isDynamicColorAvailableTrue() = runTest {
        val viewModel = createViewModel()
        viewModel.sdkIntProvider = { API_S }

        viewModel.uiState.test {
            assertEquals(true, awaitItem().isDynamicColorAvailable)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── on* persistence ───────────────────────────────────────────────────────

    @Test
    fun onThemeModeSelected_persistsViaRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.onThemeModeSelected(ThemeMode.LIGHT)

        assertEquals(listOf(ThemeMode.LIGHT), fakeSettingsRepository.setThemeModeCalls)
    }

    @Test
    fun onUseDynamicColorChanged_persistsViaRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.onUseDynamicColorChanged(true)

        assertEquals(listOf(true), fakeSettingsRepository.setUseDynamicColorCalls)
    }

    @Test
    fun onPlaybackSpeedChanged_persistsViaRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.onPlaybackSpeedChanged(1.75f)

        assertEquals(listOf(1.75f), fakeSettingsRepository.setPlaybackSpeedCalls)
    }

    @Test
    fun onSkipSilenceChanged_persistsViaRepository() = runTest {
        val viewModel = createViewModel()

        viewModel.onSkipSilenceChanged(true)

        assertEquals(listOf(true), fakeSettingsRepository.setSkipSilenceCalls)
    }

    // ── best-effort persistence ───────────────────────────────────────────────

    private fun mockThrowingSettingsRepository(): SettingsRepository {
        val repository = mockk<SettingsRepository>()
        every { repository.observeThemeMode() } returns flowOf(ThemeMode.SYSTEM)
        every { repository.observeUseDynamicColor() } returns flowOf(false)
        every { repository.observePlaybackSpeed() } returns flowOf(DEFAULT_PLAYBACK_SPEED)
        every { repository.observeSkipSilence() } returns flowOf(false)
        coEvery { repository.setThemeMode(any()) } throws IOException("disk full")
        coEvery { repository.setUseDynamicColor(any()) } throws IOException("disk full")
        coEvery { repository.setPlaybackSpeed(any()) } throws IOException("disk full")
        coEvery { repository.setSkipSilence(any()) } throws IOException("disk full")
        return repository
    }

    @Test
    fun onThemeModeSelected_repositoryWriteThrows_doesNotCrash() = runTest {
        val viewModel = createViewModel(settingsRepository = mockThrowingSettingsRepository())

        viewModel.onThemeModeSelected(ThemeMode.DARK)

        // Reaching this line without an uncaught exception is the assertion: the IOException
        // thrown by the repository's setter must never escape viewModelScope's launch.
        assertTrue(true)
    }

    @Test
    fun onUseDynamicColorChanged_repositoryWriteThrows_doesNotCrash() = runTest {
        val viewModel = createViewModel(settingsRepository = mockThrowingSettingsRepository())

        viewModel.onUseDynamicColorChanged(true)

        assertTrue(true)
    }

    @Test
    fun onPlaybackSpeedChanged_repositoryWriteThrows_doesNotCrash() = runTest {
        val viewModel = createViewModel(settingsRepository = mockThrowingSettingsRepository())

        viewModel.onPlaybackSpeedChanged(1.25f)

        assertTrue(true)
    }

    @Test
    fun onSkipSilenceChanged_repositoryWriteThrows_doesNotCrash() = runTest {
        val viewModel = createViewModel(settingsRepository = mockThrowingSettingsRepository())

        viewModel.onSkipSilenceChanged(true)

        assertTrue(true)
    }

    // ── manual rescan ─────────────────────────────────────────────────────────

    @Test
    fun onRescanClick_showsProgressThenResultMessageWithCounts() = runTest {
        // Gate keeps the sync in flight so the isRescanning=true state is deterministically
        // observable before completion (rather than racing StateFlow conflation).
        val gate = CompletableDeferred<Unit>()
        coEvery { libraryIndexer.sync() } coAnswers {
            gate.await()
            SyncResult(added = 5, removed = 2)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            assertEquals(true, awaitItem().isRescanning)

            gate.complete(Unit)
            val finished = awaitItem()
            assertEquals(false, finished.isRescanning)
            assertEquals("Library rescanned: 5 added or updated, 2 removed", finished.rescanMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onRescanClick_onSuccess_clearsArtworkCacheAfterSync() = runTest {
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 5, removed = 2)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            assertEquals(true, awaitItem().isRescanning)

            val finished = awaitItem()
            assertEquals(false, finished.isRescanning)

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { albumArtworkCache.clear() }
        coVerifyOrder {
            libraryIndexer.sync()
            albumArtworkCache.clear()
        }
    }

    @Test
    fun onRescanClick_whileRescanInFlight_secondCallIsIgnored() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { libraryIndexer.sync() } coAnswers {
            gate.await()
            SyncResult(added = 0, removed = 0)
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            assertEquals(true, awaitItem().isRescanning)
            viewModel.onRescanClick()

            gate.complete(Unit)
            assertEquals(false, awaitItem().isRescanning)

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 1) { libraryIndexer.sync() }
    }

    @Test
    fun onRescanClick_syncThrows_setsFriendlyFailureMessageWithoutCrash() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { libraryIndexer.sync() } coAnswers {
            gate.await()
            throw IOException("mediastore unavailable")
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            assertEquals(true, awaitItem().isRescanning)

            gate.complete(Unit)
            val failed = awaitItem()
            assertEquals(false, failed.isRescanning)
            assertEquals("Couldn't rescan the library. Please try again.", failed.rescanMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onRescanClick_artworkCacheClearThrows_stillReportsSuccess() = runTest {
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 5, removed = 2)
        coEvery { albumArtworkCache.clear() } throws IOException("disk full")
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            assertEquals(true, awaitItem().isRescanning)

            val finished = awaitItem()
            assertEquals(false, finished.isRescanning)
            assertEquals("Library rescanned: 5 added or updated, 2 removed", finished.rescanMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onRescanClick_syncThrows_artworkCacheIsNeverCleared() = runTest {
        val gate = CompletableDeferred<Unit>()
        coEvery { libraryIndexer.sync() } coAnswers {
            gate.await()
            throw IOException("mediastore unavailable")
        }
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            assertEquals(true, awaitItem().isRescanning)

            gate.complete(Unit)
            val failed = awaitItem()
            assertEquals(false, failed.isRescanning)
            assertEquals("Couldn't rescan the library. Please try again.", failed.rescanMessage)

            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { albumArtworkCache.clear() }
    }

    @Test
    fun dismissRescanMessage_clearsTheMessage() = runTest {
        coEvery { libraryIndexer.sync() } returns SyncResult(added = 1, removed = 0)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(false, awaitItem().isRescanning)

            viewModel.onRescanClick()
            // Skip intermediate emissions (in-flight state may conflate with completion here --
            // the deterministic ordering is already pinned by the gated tests above).
            var item = awaitItem()
            while (item.rescanMessage == null) {
                item = awaitItem()
            }

            viewModel.dismissRescanMessage()
            assertEquals(null, awaitItem().rescanMessage)

            cancelAndIgnoreRemainingEvents()
        }
    }
}
