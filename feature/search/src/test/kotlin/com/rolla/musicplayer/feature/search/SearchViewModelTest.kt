package com.rolla.musicplayer.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.SearchResults
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakeSearchRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Mirrors SearchViewModel's own debounce window so tests can advance virtual time precisely.
private const val DEBOUNCE_MS = 300L

private const val QUERY_KEY = "query"

/**
 * Unit tests for [SearchViewModel].
 *
 * Every test that needs to drive the debounce window runs via runTest(mainDispatcherRule
 * .testDispatcher) so the TestCoroutineScheduler driving advanceTimeBy is the very same one
 * backing Dispatchers.Main (and therefore viewModelScope) -- otherwise the virtual clocks would
 * be two independent instances and the debounce operator's internal delay would never appear to
 * elapse from the test body's point of view.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fakeSearchRepository = FakeSearchRepository()

    // Relaxed mock: connect() is called in SearchViewModel.init; we don't want to
    // stub it manually in every test (same rationale as LibraryViewModelTest).
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private val sampleSong = Song(
        id = "song-1",
        title = "Song 1",
        artist = "Artist 1",
        album = "Album 1",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = 1,
        year = 2001,
        contentUri = "content://media/external/audio/media/1",
        artworkUri = "content://media/external/audio/albumart/1",
    )

    private fun createViewModel(initialQuery: String? = null): SearchViewModel {
        val savedStateHandle = if (initialQuery != null) {
            SavedStateHandle(mapOf(QUERY_KEY to initialQuery))
        } else {
            SavedStateHandle()
        }
        return SearchViewModel(fakeSearchRepository, savedStateHandle, playbackController)
    }

    @Test
    fun uiState_initial_isIdle() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        assertTrue(fakeSearchRepository.searchedQueries.isEmpty())
    }

    @Test
    fun onQueryChanged_updatesQueryStateFlow() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onQueryChanged("metal")

        assertEquals("metal", viewModel.query.value)
    }

    @Test
    fun onQueryChanged_persistsValueToSavedStateHandle() = runTest(mainDispatcherRule.testDispatcher) {
        val savedStateHandle = SavedStateHandle()
        val viewModel = SearchViewModel(fakeSearchRepository, savedStateHandle, playbackController)

        viewModel.onQueryChanged("metal")

        assertEquals("metal", savedStateHandle.get<String>(QUERY_KEY))
    }

    @Test
    fun onQueryChanged_belowDebounceWindow_doesNotSearchRepositoryYet() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS - 1)
        runCurrent()

        assertTrue(fakeSearchRepository.searchedQueries.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun onQueryChanged_rapidSuccessiveQueries_onlyLastReachesRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("ro")
        advanceTimeBy(100L)
        viewModel.onQueryChanged("roc")
        advanceTimeBy(100L)
        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf("rock"), fakeSearchRepository.searchedQueries)

        collectJob.cancel()
    }

    @Test
    fun onQueryChanged_sameQueryResubmitted_doesNotRestartSearch() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf("rock"), fakeSearchRepository.searchedQueries)

        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf("rock"), fakeSearchRepository.searchedQueries)

        collectJob.cancel()
    }

    @Test
    fun onQueryChanged_clearingToBlank_returnsToIdleImmediatelyWithoutWaitingForDebounce() = runTest(
        mainDispatcherRule.testDispatcher,
    ) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(SearchUiState.Loading, awaitItem())
            assertEquals(SearchUiState.Empty, awaitItem())

            viewModel.onQueryChanged("")
            runCurrent()

            assertEquals(SearchUiState.Idle, awaitItem())
            assertEquals(listOf("rock"), fakeSearchRepository.searchedQueries)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_nonBlankQuery_emitsLoadingBeforeResultsArrive() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(SearchUiState.Loading, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_repositoryEmitsNonEmptyResults_mapsToResultsState() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val results = SearchResults(songs = listOf(sampleSong), albums = emptyList(), artists = emptyList())
        fakeSearchRepository.emit(results)

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(SearchUiState.Loading, awaitItem())
            assertEquals(SearchUiState.Results(results), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun uiState_repositoryEmitsEmptyResults_mapsToEmptyState() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        // FakeSearchRepository defaults its shared results flow to SearchResults.EMPTY already.

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(SearchUiState.Loading, awaitItem())
            assertEquals(SearchUiState.Empty, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun query_seededFromSavedStateHandle_triggersSearchAfterDebounce() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel(initialQuery = "jazz")

        assertEquals("jazz", viewModel.query.value)

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(SearchUiState.Loading, awaitItem())
            assertEquals(listOf("jazz"), fakeSearchRepository.searchedQueries)

            cancelAndIgnoreRemainingEvents()
        }
    }

    // ── play ──────────────────────────────────────────────────────────────────

    @Test
    fun play_delegatesToPlaybackController() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.play(sampleSong)

        verify(exactly = 1) { playbackController.play(sampleSong) }
    }

    // ── recent searches ──────────────────────────────────────────────────────

    @Test
    fun onQueryChanged_executedQuery_recordsRecentSearch() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertEquals(listOf("rock"), fakeSearchRepository.observeRecentSearches().first())

        collectJob.cancel()
    }

    @Test
    fun onQueryChanged_belowDebounceWindow_doesNotRecordRecentSearch() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS - 1)
        runCurrent()

        assertTrue(fakeSearchRepository.observeRecentSearches().first().isEmpty())

        collectJob.cancel()
    }

    @Test
    fun onQueryChanged_blankQuery_neverRecordsRecentSearch() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("   ")
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()

        assertTrue(fakeSearchRepository.observeRecentSearches().first().isEmpty())

        collectJob.cancel()
    }

    @Test
    fun onRecentSearchClicked_updatesQueryAndTriggersSearchAfterDebounce() = runTest(
        mainDispatcherRule.testDispatcher,
    ) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onRecentSearchClicked("jazz")
            assertEquals("jazz", viewModel.query.value)

            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            assertEquals(SearchUiState.Loading, awaitItem())
            assertEquals(listOf("jazz"), fakeSearchRepository.searchedQueries)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun onClearRecentSearches_emptiesRecentSearches() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.uiState.collect {} }
        runCurrent()

        viewModel.onQueryChanged("rock")
        advanceTimeBy(DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf("rock"), fakeSearchRepository.observeRecentSearches().first())

        viewModel.onClearRecentSearches()
        runCurrent()

        assertTrue(fakeSearchRepository.observeRecentSearches().first().isEmpty())

        collectJob.cancel()
    }

    @Test
    fun recentSearches_reflectsRepositoryEmissions() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.recentSearches.test {
            assertEquals(emptyList<String>(), awaitItem())

            fakeSearchRepository.recordRecentSearch("metal")

            assertEquals(listOf("metal"), awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }
}
