package com.rolla.musicplayer.feature.search

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.SearchResults
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.FakeSearchRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Mirrors SearchViewModel's own debounce window so tests can advance virtual time precisely.
private const val DEBOUNCE_MS = 300L

private const val QUERY_KEY = "query"

// Deliberately far longer than the debounce window -- see mockSearchRepository's KDoc.
private const val SLOW_QUERY_DELAY_MS = 10_000L

// How long the refinement's search takes in uiState_refinementAfterResults_emitsNoLoading.
private const val REFINEMENT_DELAY_MS = 1_000L

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
    // stub it manually in every test (same rationale as TracksViewModelTest).
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private val playlistRepository = FakePlaylistRepository()

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
        return SearchViewModel(fakeSearchRepository, savedStateHandle, playbackController, playlistRepository)
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
        val viewModel = SearchViewModel(fakeSearchRepository, savedStateHandle, playbackController, playlistRepository)

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
    fun onQueryChanged_clearedBeforeDebounceElapses_abandonedQueryNeverSearchedOrRecorded() = runTest(
        mainDispatcherRule.testDispatcher,
    ) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS - 1)
            runCurrent()

            // Cleared before "rock"'s debounce window ever elapsed -- debounce() restarts its
            // timer on this new value instead of letting the abandoned one through.
            viewModel.onQueryChanged("")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            // Still Idle -- the blank exemption maps to the same value already current, so
            // stateIn's conflation means no new emission reaches this collector either.
            expectNoEvents()
            assertTrue(
                "abandoned 'rock' query must never reach the repository",
                fakeSearchRepository.searchedQueries.isEmpty(),
            )
            assertTrue(
                "abandoned 'rock' query must never be recorded as a recent search",
                fakeSearchRepository.observeRecentSearches().first().isEmpty(),
            )

            cancelAndIgnoreRemainingEvents()
        }
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
    fun onQueryChanged_whitespaceOnlyQuery_treatedAsBlankEndToEnd() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("   ")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()

            // Whitespace-only text is blank: exempt from the debounce wait, never reaches the
            // repository, and maps to the same Idle value already current -- no new emission.
            expectNoEvents()
            assertTrue(fakeSearchRepository.searchedQueries.isEmpty())
            assertTrue(fakeSearchRepository.observeRecentSearches().first().isEmpty())

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

    // 6b perf audit blocker 1: a refinement goes Results to Results, so the results list stays composed. "ro" answers
    // at once and "rock" after REFINEMENT_DELAY_MS, with different results, so the in-between state is observable.
    // Trivial stub: never emit Loading → the control (the first query's Loading) fails. Mutation: always emit Loading
    // → the refinement shows Loading, not the previous results → RED.
    @Test
    fun uiState_refinementAfterResults_emitsNoLoading() = runTest(mainDispatcherRule.testDispatcher) {
        val roResults = SearchUiState.Results(searchResultsWithSong(id = "ro-song"))
        val rockResults = SearchUiState.Results(searchResultsWithSong(id = "rock-song"))
        val repository = refinementRepository(ro = roResults.results, rock = rockResults.results)
        val viewModel = SearchViewModel(repository, SavedStateHandle(), playbackController, playlistRepository)

        viewModel.uiState.test {
            assertEquals("starts Idle", SearchUiState.Idle, awaitItem())
            viewModel.onQueryChanged("ro")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals("control: a first query from Idle shows Loading", SearchUiState.Loading, awaitItem())
            assertEquals("then its results", roResults, awaitItem())

            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals("the previous results stay while the refinement runs", roResults, viewModel.uiState.value)
            advanceTimeBy(REFINEMENT_DELAY_MS)
            runCurrent()
            assertEquals("a refinement goes Results to Results, with no Loading between", rockResults, awaitItem())

            cancelAndIgnoreRemainingEvents()
        }
    }

    /** "ro" answers [ro] at once; "rock" answers [rock] after [REFINEMENT_DELAY_MS]. */
    private fun refinementRepository(ro: SearchResults, rock: SearchResults): SearchRepository {
        val repository = mockk<SearchRepository>()
        every { repository.observeRecentSearches() } returns flowOf(emptyList())
        coEvery { repository.recordRecentSearch(any()) } just Runs
        every { repository.search("ro") } returns flowOf(ro)
        every { repository.search("rock") } returns flow {
            delay(REFINEMENT_DELAY_MS)
            emit(rock)
        }
        return repository
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

    /**
     * A dedicated [SearchRepository] double, purely for
     * [uiState_fasterQueryArrivesWhileSlowerSearchInFlight_flatMapLatestCancelsStaleSearch].
     * [FakeSearchRepository] shares one results flow across every query, so it can't tell a slow
     * "first query" apart from a fast "second query" -- this gives the two queries genuinely
     * different response timing instead.
     */
    private fun mockSearchRepository(slowResults: SearchResults, fastResults: SearchResults): SearchRepository {
        val repository = mockk<SearchRepository>()
        every { repository.observeRecentSearches() } returns flowOf(emptyList())
        coEvery { repository.recordRecentSearch(any()) } just Runs
        every { repository.search("slow") } returns flow {
            delay(SLOW_QUERY_DELAY_MS)
            emit(slowResults)
        }
        every { repository.search("fast") } returns flowOf(fastResults)
        return repository
    }

    private fun searchResultsWithSong(id: String) =
        SearchResults(songs = listOf(sampleSong.copy(id = id)), albums = emptyList(), artists = emptyList())

    @Test
    fun uiState_fasterQueryArrivesWhileSlowerSearchInFlight_flatMapLatestCancelsStaleSearch() = runTest(
        mainDispatcherRule.testDispatcher,
    ) {
        val fastResults = searchResultsWithSong(id = "fast-song")
        val repository = mockSearchRepository(
            slowResults = searchResultsWithSong(id = "slow-song"),
            fastResults = fastResults,
        )
        val viewModel = SearchViewModel(repository, SavedStateHandle(), playbackController, playlistRepository)

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())

            viewModel.onQueryChanged("slow")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(SearchUiState.Loading, awaitItem())

            viewModel.onQueryChanged("fast")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            // The "slow" query never got past Loading before being superseded, so this
            // transition's own Loading emission is an equal value to what's already current --
            // StateFlow conflates it away. The next distinct value collected is "fast"'s result.
            assertEquals(SearchUiState.Results(fastResults), awaitItem())

            // Advance well past the "slow" query's delay. If flatMapLatest hadn't cancelled its
            // still in-flight collection when "fast" arrived, this is where a stale result would
            // land and overwrite the newer one.
            advanceTimeBy(SLOW_QUERY_DELAY_MS * 2)
            runCurrent()
            expectNoEvents()

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

    // Mutations: `play(song)` keeps `playbackController.play(song)` → RED (playFrom never called);
    // pass a different list or order (e.g. `songs.reversed()`, or `listOf(song)`) → RED (eq mismatch).
    // Three distinct songs, the tapped one second, so list identity, order and start song all differ.
    @Test
    fun play_tappingSecondOfThreeSongResults_playsTheResultListFromTheTappedSong() = runTest(
        mainDispatcherRule.testDispatcher,
    ) {
        val songA = sampleSong.copy(id = "song-a", title = "A")
        val songB = sampleSong.copy(id = "song-b", title = "B")
        val songC = sampleSong.copy(id = "song-c", title = "C")
        val results = SearchResults(songs = listOf(songA, songB, songC), albums = emptyList(), artists = emptyList())
        fakeSearchRepository.emit(results)
        val viewModel = createViewModel()

        viewModel.uiState.test {
            assertEquals(SearchUiState.Idle, awaitItem())
            viewModel.onQueryChanged("rock")
            advanceTimeBy(DEBOUNCE_MS)
            runCurrent()
            assertEquals(SearchUiState.Loading, awaitItem())
            assertEquals(SearchUiState.Results(results), awaitItem())

            viewModel.play(songB)

            verify(exactly = 1) { playbackController.playFrom(listOf(songA, songB, songC), songB) }
            verify(exactly = 0) { playbackController.play(any()) }

            cancelAndIgnoreRemainingEvents()
        }
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

    // Phase 6b ruling 5. Trivial stub: a no-op body → "jazz" stays → RED. Mutation: the same no-op body.
    // Control: both queries are in recentSearches before the removal.
    @Test
    fun removingARecentSearchRemovesIt() = runTest(mainDispatcherRule.testDispatcher) {
        fakeSearchRepository.recordRecentSearch("queen")
        fakeSearchRepository.recordRecentSearch("jazz")
        val viewModel = createViewModel()
        val collectJob = launch { viewModel.recentSearches.collect {} }
        advanceUntilIdle()
        assertEquals("control: both are recent searches first", listOf("jazz", "queen"), viewModel.recentSearches.value)

        viewModel.onRemoveRecentSearch("jazz")
        advanceUntilIdle()

        assertFalse("jazz is removed", "jazz" in viewModel.recentSearches.value)
        assertTrue("queen stays", "queen" in viewModel.recentSearches.value)

        collectJob.cancel()
    }

    // The options sheet's playlist actions (ruling 4). One existing playlist makes the created playlist's id 2, apart
    // from both the song id "1" and the picked playlist 7. Trivial stub: no-op bodies → no addSongs call → RED.
    // Mutation: swap the ids/arguments (createPlaylistWithSong(songId, name)) → a playlist named "1" holding "Mix"
    // → RED.
    @Test
    fun optionsSheetActionsReachThePlaylistRepository() = runTest(mainDispatcherRule.testDispatcher) {
        val existingId = playlistRepository.createPlaylist("Existing")
        val viewModel = createViewModel()

        viewModel.addSongToPlaylist("1", 7L)
        advanceUntilIdle()
        assertEquals(
            "Add to playlist appends the track once",
            listOf(7L to listOf("1")),
            playlistRepository.addSongsCalls,
        )

        viewModel.createPlaylistAndAddSong("Mix", "1")
        advanceUntilIdle()
        val created = playlistRepository.observePlaylists().first().single { it.id != existingId }
        assertEquals("New playlist is created under the typed name", "Mix", created.name)
        assertEquals(
            "the track goes to the new playlist's id",
            listOf(7L to listOf("1"), created.id to listOf("1")),
            playlistRepository.addSongsCalls,
        )
    }
}
