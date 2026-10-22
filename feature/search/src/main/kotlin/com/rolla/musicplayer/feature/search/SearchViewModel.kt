package com.rolla.musicplayer.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SearchRepository
import com.rolla.musicplayer.core.data.repository.createPlaylistWithSong
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
import kotlinx.coroutines.launch
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
 * flight, before [SearchRepository.search] has produced anything, unless results are already on
 * screen: a refinement goes straight from one [SearchUiState.Results] to the next, so the results
 * list stays composed. A single `yield()` right after a Loading emission guarantees it is a real,
 * observable state rather than a value StateFlow's conflation could overwrite before any collector
 * ever sees it (see [searchFlow]).
 *
 * [SearchRepository.search] already runs its DB query and domain mapping on `Dispatchers.IO` (see
 * its implementation's `flowOn`), so no dispatcher hop is added here -- everything in this
 * pipeline besides that repository call is cheap, non-blocking flow bookkeeping.
 *
 * Recent-search history: [recentSearches] mirrors [SearchRepository.observeRecentSearches] for
 * [SearchUiState.Idle] to render. A query is only ever recorded from [searchFlow]'s non-blank
 * `onStart` block -- i.e. only once it has survived the debounce/distinctUntilChanged stage and
 * actually committed to a search -- never from [onQueryChanged] directly, so raw keystrokes never
 * pollute history. The write is launched alongside the search, never awaited by it.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val savedStateHandle: SavedStateHandle,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
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

    /** Recent search history, most-recent-first, for [SearchUiState.Idle] to render. */
    val recentSearches: StateFlow<List<String>> = searchRepository.observeRecentSearches()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    /** User-created playlists, for the track options sheet's "Add to playlist" step. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000L),
            initialValue = emptyList(),
        )

    /** Updates the in-flight query text and mirrors it into [savedStateHandle] for process death. */
    fun onQueryChanged(value: String) {
        _query.update { value }
        savedStateHandle[QUERY_KEY] = value
    }

    /**
     * Plays the Songs section of [uiState]'s results as the queue, in displayed order, starting at the tapped
     * [song] (ruling 5). [uiState] is WhileSubscribed and SearchScreen collects it while visible, so its value is
     * the list the user tapped in. Outside [SearchUiState.Results] the list is empty and `playFrom` plays [song] alone.
     */
    fun play(song: Song) {
        val songs = (uiState.value as? SearchUiState.Results)?.results?.songs.orEmpty()
        playbackController.playFrom(songs, song)
    }

    /** Re-runs a query tapped from the recent-searches list in [SearchUiState.Idle]. */
    fun onRecentSearchClicked(query: String) {
        onQueryChanged(query)
    }

    /**
     * Removes one recent search, matching [query] exactly (Phase 6b ruling 5). Best-effort, like
     * [onClearRecentSearches]: a failed DataStore write is dropped.
     */
    fun onRemoveRecentSearch(query: String) {
        viewModelScope.launch {
            try {
                searchRepository.removeRecentSearch(query)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                // History is cosmetic; see onClearRecentSearches.
            }
        }
    }

    /** The options sheet's "Add to playlist" pick: appends [songId] to [playlistId]. */
    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, listOf(songId)) }
    }

    /** The options sheet's "New playlist": creates [name] holding [songId]. A blank name writes nothing. */
    fun createPlaylistAndAddSong(name: String, songId: String) {
        viewModelScope.launch { playlistRepository.createPlaylistWithSong(name, songId) }
    }

    /** Clears all recorded recent-search history. Best-effort: a failed DataStore write is dropped. */
    fun onClearRecentSearches() {
        viewModelScope.launch {
            try {
                searchRepository.clearRecentSearches()
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                // History is cosmetic; an IOException from DataStore (disk full) must not crash
                // the process via viewModelScope's default handler.
            }
        }
    }

    private fun searchFlow(text: String) = if (text.isBlank()) {
        flowOf(SearchUiState.Idle)
    } else {
        searchRepository.search(text)
            .map { results -> results.toUiState() }
            .onStart {
                // By the time this query is in flight at all, it has survived debounce/distinctUntilChanged and is
                // committed to being searched, so it goes into history now. A pipeline restart re-recording the same
                // query is harmless: the repository dedupes to front.
                recordRecentSearchInBackground(text)
                // A refinement keeps the previous results on screen until the new ones arrive: going Results ->
                // Loading -> Results would take the results list out of composition (its scroll state and row
                // subcompositions disposed) and leave a blank panel frame on every committed query (6b perf audit).
                if (uiState.value !is SearchUiState.Results) {
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

    /**
     * Records [text] in the recent-search history without making the search wait for the DataStore write (write,
     * fsync, rename: 6b perf audit). Best-effort: a failed write (disk full -> IOException) is dropped, so the search
     * proceeds without the history entry and the failure never reaches viewModelScope's default handler.
     */
    private fun recordRecentSearchInBackground(text: String) {
        viewModelScope.launch {
            try {
                searchRepository.recordRecentSearch(text)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") ignored: Exception) {
                // History is cosmetic; see above.
            }
        }
    }
}
