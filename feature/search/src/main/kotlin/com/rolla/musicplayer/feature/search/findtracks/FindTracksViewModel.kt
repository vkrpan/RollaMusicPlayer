package com.rolla.musicplayer.feature.search.findtracks

import androidx.annotation.VisibleForTesting
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.PlaylistRepository
import com.rolla.musicplayer.core.data.repository.SongFinder
import com.rolla.musicplayer.core.data.repository.createPlaylistWithSong
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Playlist
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.ui.customtags.CustomTagPickerState
import com.rolla.musicplayer.core.ui.customtags.toggled
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.random.Random

private const val TITLE_KEY = "findTracks.title"
private const val ARTIST_KEY = "findTracks.artist"
private const val ALBUM_KEY = "findTracks.album"
private const val GENRE_KEY = "findTracks.genre"
private const val YEAR_KEY = "findTracks.year"
internal const val TAG_IDS_KEY = "findTracks.tagIds"
internal const val SUB_TAG_IDS_KEY = "findTracks.subTagIds"
private const val FILTERS_EXPANDED_KEY = "findTracks.filtersExpanded"

/** The longest Year the field accepts, in digits. */
private const val YEAR_MAX_DIGITS = 4

/**
 * State and actions for Find tracks (custom tags phase 4): the library filtered by title, artist, album, genre, year
 * and custom tags, live.
 *
 * The five texts, both ID sets and whether the fields card is expanded are kept in [SavedStateHandle] on every change,
 * so the filter survives process death; [FindTracksUiState.filter] is derived from them. The fields card starts
 * expanded when the restored filter is empty. [SongFinder] answers each filter in library order and again whenever
 * the library or a song's tags change.
 *
 * Both custom tag lists are observed for the ViewModel's lifetime. Each list emission drops the filter's (and an open
 * picker's) IDs the lists no longer know: Manage tags, reached from a result's Edit tags, may delete a tag in the
 * filter, or an import replace the lists, while Find tracks sits under it. A lists flow that fails degrades to empty
 * lists marked unavailable ([FindTracksUiState.listsUnavailable]); that fallback says nothing about deletions, so it
 * prunes nothing, and [FindTracksUiState.activeCustomTagFilters] keeps every filter ID removable without its name.
 *
 * The picker's ticks reach the filter only on Done ([onPickerDone]).
 *
 * A result's ⋮ opens the shared track options sheet, so the ViewModel also offers its "Add to playlist" step
 * ([userPlaylists], [addSongToPlaylist], [createPlaylistAndAddSong]), as every other track list's ViewModel does.
 */
// Justified: one handler per field, chip, picker event and playback button; splitting them would only scatter the
// SavedStateHandle writes the screen's state depends on.
@Suppress("TooManyFunctions")
@HiltViewModel
class FindTracksViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    songFinder: SongFinder,
    customTagRepository: CustomTagRepository,
    private val playbackController: PlaybackController,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {

    /** Picks Shuffle's start track. Tests swap in a seeded [Random]; Hilt's constructor stays unchanged. */
    @VisibleForTesting
    internal var random: Random = Random.Default

    private val fields = MutableStateFlow(restoreFields())

    /** Both lists in library order; empty until the first answer, and the empty fallback after a failure. */
    private val lists = MutableStateFlow(FindTracksLists.EMPTY)

    private val picker = MutableStateFlow<CustomTagPickerState?>(null)

    /** The songs matching the filter, in library order; null until [SongFinder]'s first answer. */
    private val results: StateFlow<List<Song>?> =
        songFinder.observeMatches(fields.map { it.toFilter() }.distinctUntilChanged())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val uiState: StateFlow<FindTracksUiState> = combine(fields, lists, results, picker) { f, l, r, p ->
        f.toUiState(lists = l, results = r, picker = p)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = fields.value.toUiState(lists = FindTracksLists.EMPTY, results = null, picker = null),
    )

    /** User-created playlists, for the track options sheet's "Add to playlist" step. */
    val userPlaylists: StateFlow<List<Playlist>> = playlistRepository.observePlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    init {
        playbackController.connect()
        viewModelScope.launch {
            combine(
                customTagRepository.observeCustomTags(CustomTagKind.TAG),
                customTagRepository.observeCustomTags(CustomTagKind.SUBTAG),
            ) { tags, subTags -> FindTracksLists(tags, subTags) }
                // A broken custom tag table degrades to empty lists rather than crashing the screen. The fallback is
                // not a deletion, so it never prunes the filter or the picker's ticks (observation 0028).
                .catch { emit(FindTracksLists.UNAVAILABLE) }
                .collect(::onListsChanged)
        }
    }

    fun onTitleChange(value: String) = editFields { it.copy(title = value) }

    fun onArtistChange(value: String) = editFields { it.copy(artist = value) }

    fun onAlbumChange(value: String) = editFields { it.copy(album = value) }

    fun onGenreChange(value: String) = editFields { it.copy(genre = value) }

    /** Takes [value] when it is digits only and at most four of them (or empty); anything else is ignored. */
    fun onYearChange(value: String) {
        if (value.length > YEAR_MAX_DIGITS || !value.all { it in '0'..'9' }) return
        editFields { it.copy(year = value) }
    }

    /** ✕ on a text field's chip: clears that field only. */
    fun onRemoveTextFilter(field: FindTracksField) = editFields {
        when (field) {
            FindTracksField.TITLE -> it.copy(title = "")
            FindTracksField.ARTIST -> it.copy(artist = "")
            FindTracksField.ALBUM -> it.copy(album = "")
            FindTracksField.GENRE -> it.copy(genre = "")
            FindTracksField.YEAR -> it.copy(year = "")
        }
    }

    /** ✕ on a tag's or subtag's chip: drops [id] of [kind] from the filter. */
    fun onRemoveCustomTag(kind: CustomTagKind, id: Long) = editFields {
        when (kind) {
            CustomTagKind.TAG -> it.copy(tagIds = it.tagIds - id)
            CustomTagKind.SUBTAG -> it.copy(subTagIds = it.subTagIds - id)
        }
    }

    /** "Edit filters" / "Hide filters": shows or hides the fields card. */
    fun onToggleFilters() = editFields { it.copy(filtersExpanded = !it.filtersExpanded) }

    /** The Tags or Subtags row: opens the picker on [kind]'s page, ticked with the filter's IDs, searches empty. */
    fun onOpenPicker(kind: CustomTagKind) {
        val current = fields.value
        val lists = lists.value
        picker.value = CustomTagPickerState(
            tags = lists.tags,
            subTags = lists.subTags,
            selected = CustomTagIds(tagIds = current.tagIds, subTagIds = current.subTagIds),
            page = kind,
        )
    }

    /** The picker's tab or pager moved to [kind]'s page. */
    fun onPickerPageChange(kind: CustomTagKind) {
        picker.update { it?.copy(page = kind) }
    }

    /** The search text of [kind]'s page changed. */
    fun onPickerQueryChange(kind: CustomTagKind, query: String) {
        picker.update {
            when (kind) {
                CustomTagKind.TAG -> it?.copy(tagQuery = query)
                CustomTagKind.SUBTAG -> it?.copy(subTagQuery = query)
            }
        }
    }

    /** Ticks or unticks entry [id] of [kind]'s list in the picker; the filter changes only on [onPickerDone]. */
    fun onPickerToggle(kind: CustomTagKind, id: Long) {
        picker.update { it?.copy(selected = it.selected.toggled(kind, id)) }
    }

    /** "Done", or the sheet dismissed: the picker's ticks become the filter's IDs, and the picker closes. */
    fun onPickerDone() {
        val closing = picker.value ?: return
        editFields { it.copy(tagIds = closing.selected.tagIds, subTagIds = closing.selected.subTagIds) }
        picker.value = null
    }

    /** Plays every result as the queue, in library order, starting at the tapped [song]. */
    fun play(song: Song) {
        playbackController.playFrom(currentResults(), song)
    }

    /** Plays every result in order from the first. A no-op when nothing matches. */
    fun playAll() {
        val songs = currentResults()
        if (songs.isEmpty()) return
        playbackController.playAllInOrder(songs)
    }

    /** Shuffles the results from a random start track. A no-op when nothing matches. */
    fun shuffleAll() {
        val songs = currentResults()
        if (songs.isEmpty()) return
        playbackController.shuffleAll(songs, random)
    }

    /** The options sheet's "Add to playlist" pick: appends [songId] to [playlistId]. */
    fun addSongToPlaylist(songId: String, playlistId: Long) {
        viewModelScope.launch { playlistRepository.addSongs(playlistId, listOf(songId)) }
    }

    /** The options sheet's "New playlist": creates [name] holding [songId]. A blank name writes nothing. */
    fun createPlaylistAndAddSong(name: String, songId: String) {
        viewModelScope.launch { playlistRepository.createPlaylistWithSong(name, songId) }
    }

    // [results] is WhileSubscribed and the screen collects [uiState] while visible, so its value is current when a
    // row or a header button is tapped.
    private fun currentResults(): List<Song> = results.value.orEmpty()

    /**
     * New lists: the chips' names and an open picker's lists follow them. A successful emission also drops the
     * filter's and the picker's IDs the lists do not know; the failure fallback prunes nothing.
     */
    private fun onListsChanged(newLists: FindTracksLists) {
        lists.value = newLists
        if (newLists.unavailable) {
            picker.update { it?.copy(tags = newLists.tags, subTags = newLists.subTags) }
            return
        }
        val knownTagIds = newLists.tags.mapTo(HashSet()) { it.id }
        val knownSubTagIds = newLists.subTags.mapTo(HashSet()) { it.id }
        val current = fields.value
        val prunedTagIds = current.tagIds.filterTo(HashSet()) { it in knownTagIds }
        val prunedSubTagIds = current.subTagIds.filterTo(HashSet()) { it in knownSubTagIds }
        if (prunedTagIds != current.tagIds || prunedSubTagIds != current.subTagIds) {
            editFields { it.copy(tagIds = prunedTagIds, subTagIds = prunedSubTagIds) }
        }
        picker.update { open ->
            open?.copy(
                tags = newLists.tags,
                subTags = newLists.subTags,
                selected = CustomTagIds(
                    tagIds = open.selected.tagIds.filterTo(HashSet()) { it in knownTagIds },
                    subTagIds = open.selected.subTagIds.filterTo(HashSet()) { it in knownSubTagIds },
                ),
            )
        }
    }

    /** Applies [transform] to the fields and writes every one of them to [savedStateHandle]. */
    private inline fun editFields(transform: (FindTracksFields) -> FindTracksFields) {
        fields.update(transform)
        val saved = fields.value
        savedStateHandle[TITLE_KEY] = saved.title
        savedStateHandle[ARTIST_KEY] = saved.artist
        savedStateHandle[ALBUM_KEY] = saved.album
        savedStateHandle[GENRE_KEY] = saved.genre
        savedStateHandle[YEAR_KEY] = saved.year
        savedStateHandle[TAG_IDS_KEY] = saved.tagIds.toLongArray()
        savedStateHandle[SUB_TAG_IDS_KEY] = saved.subTagIds.toLongArray()
        savedStateHandle[FILTERS_EXPANDED_KEY] = saved.filtersExpanded
    }

    /** The fields [savedStateHandle] holds; a fresh screen has none, so an empty filter with the card expanded. */
    private fun restoreFields(): FindTracksFields {
        val restored = FindTracksFields(
            title = savedStateHandle.get<String>(TITLE_KEY).orEmpty(),
            artist = savedStateHandle.get<String>(ARTIST_KEY).orEmpty(),
            album = savedStateHandle.get<String>(ALBUM_KEY).orEmpty(),
            genre = savedStateHandle.get<String>(GENRE_KEY).orEmpty(),
            year = savedStateHandle.get<String>(YEAR_KEY).orEmpty(),
            tagIds = savedStateHandle.get<LongArray>(TAG_IDS_KEY)?.toSet().orEmpty(),
            subTagIds = savedStateHandle.get<LongArray>(SUB_TAG_IDS_KEY)?.toSet().orEmpty(),
            filtersExpanded = false,
        )
        val expanded = savedStateHandle.get<Boolean>(FILTERS_EXPANDED_KEY) ?: restored.toFilter().isEmpty
        return restored.copy(filtersExpanded = expanded)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The filter's saved parts, as [SavedStateHandle] keeps them. */
private data class FindTracksFields(
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: String,
    val tagIds: Set<Long>,
    val subTagIds: Set<Long>,
    val filtersExpanded: Boolean,
) {
    fun toFilter(): SongFilter = SongFilter(
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        year = year.toIntOrNull(),
        tagIds = tagIds,
        subTagIds = subTagIds,
    )

    fun toUiState(lists: FindTracksLists, results: List<Song>?, picker: CustomTagPickerState?) = FindTracksUiState(
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        year = year,
        tags = lists.tags,
        subTags = lists.subTags,
        filter = toFilter(),
        results = results,
        filtersExpanded = filtersExpanded,
        picker = picker,
        listsUnavailable = lists.unavailable,
        activeCustomTagFilters = activeCustomTagFilters(tagIds, subTagIds, lists.tags, lists.subTags),
    )
}

/** Both custom tag lists, in library order; [unavailable] marks the empty fallback of a failed lists flow. */
private data class FindTracksLists(
    val tags: List<CustomTag>,
    val subTags: List<CustomTag>,
    val unavailable: Boolean = false,
) {
    companion object {
        val EMPTY = FindTracksLists(tags = emptyList(), subTags = emptyList())

        /** The lists could not be read: empty, and never a reason to prune. */
        val UNAVAILABLE = FindTracksLists(tags = emptyList(), subTags = emptyList(), unavailable = true)
    }
}
