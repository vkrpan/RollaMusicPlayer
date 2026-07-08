package com.rolla.musicplayer.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.yield
import javax.inject.Inject

/** SavedStateHandle key the nav route's `Search(query: String? = null)` argument lands under. */
private const val QUERY_KEY = "query"

/**
 * Debounce window applied to [SearchViewModel.query] before it reaches [SearchRepository.search].
 * Only non-blank queries wait this long -- see [SearchViewModel.uiState]'s KDoc.
 */
private const val SEARCH_DEBOUNCE_MS = 300L

/**
 * ViewModel for the local search screen (`Search(query: String? = null)`).
 *
 * [query] seeds from [SavedStateHandle] under [QUERY_KEY] -- the raw route argument, or the value
 * left behind by a previous instance of this ViewModel if the process was recreated mid-search --
 * and is written back on every keystroke via [onQueryChanged] so it survives process death too.
 *
 * [uiState] never touches the repository directly from [onQueryChanged]; the whole flow is a
 * single reactive pipeline over [query]:
 * ```
 * query -> debounce(blank ? 0ms : 300ms) -> distinctUntilChanged() -> flatMapLatest(search)
 * ```
 * [kotlinx.coroutines.flow.flatMapLatest] guarantees every new query cancels whatever search was
 * still in flight for the previous one, so a slow, stale response can never land after a newer,
 * faster one. A blank query is deliberately exempted from the 300ms wait (via the conditional
 * [kotlinx.coroutines.flow.debounce] timeout) so clearing the field snaps back to
 * [SearchUiState.Idle] immediately instead of leaving stale results on screen for the debounce
 * window. [SearchUiState.Loading] is emitted via `onStart` the moment a non-blank query enters
 * flight, before [SearchRepository.search] has produced anything; a single `yield()` right after
 * that emission guarantees it is a real, observable state rather than a value StateFlow's
 * conflation could overwrite before any collector ever sees it (see [searchFlow]'s KDoc).
 *
 * [SearchRepository.search] already runs its DB query and domain mapping on `Dispatchers.IO` (see
 * its implementation's `flowOn`), so no dispatcher hop is added here -- everything in this
 * pipeline besides that repository call is cheap, non-blocking flow bookkeeping.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackController,
) : ViewModel() {

    private val _query = MutableStateFlow(savedStateHandle.get<String>(QUERY_KEY).orEmpty())
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        playbackController.connect()
    }

    val uiState: StateFlow<SearchUiState> = _query
        .debounce { text -> if (text.isBlank()) 0L else SEARCH_DEBOUNCE_MS }
        .distinctUntilChanged()
        .flatMapLatest { text -> searchFlow(text) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = SearchUiState.Idle,
        )

    /** Updates the in-flight query text and mirrors it into [savedStateHandle] for process death. */
    fun onQueryChanged(value: String) {
        _query.update { value }
        savedStateHandle[QUERY_KEY] = value
    }

    /** Starts playback of a song tapped from the Songs section of [uiState]'s results. */
    fun play(song: Song) {
        playbackController.play(song)
    }

    private fun searchFlow(text: String) = if (text.isBlank()) {
        flowOf(SearchUiState.Idle)
    } else {
        searchRepository.search(text)
            .map { results -> results.toUiState() }
            .onStart {
                emit(SearchUiState.Loading)
                // StateFlow never suspends its producer on a value write, so without ceding the
                // dispatcher here, a repository that resolves synchronously (as an in-memory fake
                // does; Room's real Flow always has a genuine Dispatchers.IO hop instead) would
                // overwrite Loading with the real result before any collector -- Compose included
                // -- gets a chance to observe it. One yield is enough to guarantee Loading is a
                // real, collectible state rather than an instantaneous, unobservable one.
                yield()
            }
    }
}
