package com.rolla.musicplayer.feature.search.findtracks

import androidx.lifecycle.SavedStateHandle
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.SongFinder
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.CustomTagIds
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.random.Random

private const val SHUFFLE_SEED = 7L

@OptIn(ExperimentalCoroutinesApi::class)
class FindTracksViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // The library in library order: Love me (tag 2, subtag 1), Lovesong (tag 1), Peak (tag 2, subtag 5) and
    // Yesterday (subtag 5, which the lists do not know).
    private val loveMe = song(id = "1", title = "Love me", artist = "Queen", year = 1999)
    private val lovesong = song(id = "2", title = "Lovesong", artist = "The Cure", year = 1989)
    private val peak = song(id = "3", title = "Peak", artist = "Queen", year = 1999)
    private val yesterday = song(id = "4", title = "Yesterday", artist = "The Beatles", year = 1965)
    private val library = listOf(loveMe, lovesong, peak, yesterday)

    private val songFinder = FakeSongFinder(
        library = library,
        links = mapOf(
            loveMe.id to CustomTagIds(tagIds = setOf(2L), subTagIds = setOf(1L)),
            lovesong.id to CustomTagIds(tagIds = setOf(1L), subTagIds = emptySet()),
            peak.id to CustomTagIds(tagIds = setOf(2L), subTagIds = setOf(5L)),
            yesterday.id to CustomTagIds(tagIds = emptySet(), subTagIds = setOf(5L)),
        ),
    )

    private val customTags = FakeCustomTagLists(
        tags = listOf(tag(CustomTagKind.TAG, 1L, "Acid"), tag(CustomTagKind.TAG, 2L, "Techno")),
        subTags = listOf(tag(CustomTagKind.SUBTAG, 1L, "Warm-up")),
    )

    // Relaxed: connect() runs in init.
    private val playbackController = mockk<PlaybackController>(relaxed = true)

    private val playlistRepository = FakePlaylistRepository()

    private fun TestScope.createViewModel(handle: SavedStateHandle = SavedStateHandle()): FindTracksViewModel {
        val viewModel = FindTracksViewModel(
            savedStateHandle = handle,
            songFinder = songFinder,
            customTagRepository = customTags.repository,
            playbackController = playbackController,
            playlistRepository = playlistRepository,
        )
        // uiState is WhileSubscribed, as the screen collects it while visible.
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    /** Opens the TAG picker, ticks [tagIds] and [subTagIds], and taps Done: the filter then holds them. */
    private fun FindTracksViewModel.pick(tagIds: Set<Long> = emptySet(), subTagIds: Set<Long> = emptySet()) {
        onOpenPicker(CustomTagKind.TAG)
        tagIds.forEach { onPickerToggle(CustomTagKind.TAG, it) }
        subTagIds.forEach { onPickerToggle(CustomTagKind.SUBTAG, it) }
        onPickerDone()
    }

    @Test
    fun initial_emptyFilter_showsWholeLibrary_expanded() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue("a fresh screen's filter is empty", state.filter.isEmpty)
        assertEquals("an empty filter shows the whole library in library order", library, state.results)
        assertTrue("the fields card starts expanded on an empty filter", state.filtersExpanded)
        assertEquals("both lists arrive for the chips", listOf("Acid", "Techno"), state.tags.map { it.name })
        assertNull("no picker is open", state.picker)
    }

    @Test
    fun typing_updatesResultsLive() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onTitleChange("love")
        val afterTitle = viewModel.uiState.value
        assertEquals("the title field holds the typed text", "love", afterTitle.title)
        assertEquals("the filter follows the typed title", "love", afterTitle.filter.title)
        assertEquals("results follow the title without a Done", listOf(loveMe, lovesong), afterTitle.results)

        viewModel.onArtistChange("queen")
        assertEquals(
            "a second field narrows the results at once",
            listOf(loveMe),
            viewModel.uiState.value.results,
        )
    }

    @Test
    fun year_rejectsNonDigitsAndFiveDigits() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onYearChange("1999")
        assertEquals("four digits are taken", "1999", viewModel.uiState.value.year)
        assertEquals("the filter's year is the number", 1999, viewModel.uiState.value.filter.year)
        assertEquals("the year filter applies", listOf(loveMe, peak), viewModel.uiState.value.results)

        viewModel.onYearChange("19a9")
        assertEquals("a letter is ignored", "1999", viewModel.uiState.value.year)

        viewModel.onYearChange("19999")
        assertEquals("a fifth digit is ignored", "1999", viewModel.uiState.value.year)

        viewModel.onYearChange("")
        assertEquals("clearing the field is taken", "", viewModel.uiState.value.year)
        assertNull("a blank year is no filter", viewModel.uiState.value.filter.year)
    }

    @Test
    fun removeTextFilter_clearsThatFieldOnly() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onTitleChange("love")
        viewModel.onArtistChange("queen")
        viewModel.onYearChange("1999")

        viewModel.onRemoveTextFilter(FindTracksField.TITLE)
        val afterTitle = viewModel.uiState.value
        assertEquals("the title is cleared", "", afterTitle.title)
        assertEquals("the artist stays", "queen", afterTitle.artist)
        assertEquals("the year stays", "1999", afterTitle.year)
        assertEquals("results drop only the title filter", listOf(loveMe, peak), afterTitle.results)

        viewModel.onRemoveTextFilter(FindTracksField.YEAR)
        val afterYear = viewModel.uiState.value
        assertEquals("the year is cleared", "", afterYear.year)
        assertNull("the filter's year is cleared", afterYear.filter.year)
        assertEquals("the artist still stays", "queen", afterYear.filter.artist)
    }

    @Test
    fun picker_toggleAppliesOnlyOnDone() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()

        viewModel.onOpenPicker(CustomTagKind.TAG)
        val opened = viewModel.uiState.value.picker
        assertNotNull("the picker opens", opened)
        assertEquals("it opens on the tapped page", CustomTagKind.TAG, opened?.page)
        assertEquals("it lists the tags", listOf("Acid", "Techno"), opened?.tags?.map { it.name })

        viewModel.onPickerToggle(CustomTagKind.TAG, 2L)
        viewModel.onPickerToggle(CustomTagKind.SUBTAG, 1L)
        val ticks = CustomTagIds(tagIds = setOf(2L), subTagIds = setOf(1L))
        val ticked = viewModel.uiState.value
        assertEquals("the picker shows the ticks", ticks, ticked.picker?.selected)
        assertEquals("a tick leaves the filter unchanged", SongFilter(), ticked.filter)
        assertEquals("a tick leaves the results unchanged", library, ticked.results)

        viewModel.onPickerDone()
        val done = viewModel.uiState.value
        assertNull("Done closes the picker", done.picker)
        assertEquals("Done applies the ticks", SongFilter(tagIds = setOf(2L), subTagIds = setOf(1L)), done.filter)
        assertEquals("the results follow the applied tags", listOf(loveMe), done.results)

        viewModel.onOpenPicker(CustomTagKind.SUBTAG)
        assertEquals("reopening shows the applied ticks", ticks, viewModel.uiState.value.picker?.selected)
    }

    @Test
    fun listsChange_prunesDeletedFilterTag() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.pick(tagIds = setOf(2L))
        viewModel.onOpenPicker(CustomTagKind.TAG)
        viewModel.onPickerToggle(CustomTagKind.TAG, 1L)
        assertEquals("the filter holds Techno (2)", setOf(2L), viewModel.uiState.value.filter.tagIds)
        assertEquals("Techno's songs match", listOf(loveMe, peak), viewModel.uiState.value.results)

        // Manage tags, reached from a result's Edit tags, deletes Techno (2).
        customTags.tags.value = listOf(tag(CustomTagKind.TAG, 1L, "Acid"))

        val state = viewModel.uiState.value
        assertEquals("the deleted tag leaves the filter", emptySet<Long>(), state.filter.tagIds)
        assertEquals("the results recompute without it", library, state.results)
        assertEquals("the chips' list follows the lists", listOf("Acid"), state.tags.map { it.name })
        assertEquals(
            "the open picker keeps only known ticks",
            CustomTagIds(tagIds = setOf(1L), subTagIds = emptySet()),
            state.picker?.selected,
        )
    }

    @Test
    fun listsFail_keepsFilterTags() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.pick(tagIds = setOf(2L))
        viewModel.onOpenPicker(CustomTagKind.TAG)
        viewModel.onPickerToggle(CustomTagKind.TAG, 1L)

        customTags.failLists()

        val state = viewModel.uiState.value
        assertEquals("the failed lists fall back to empty", emptyList<CustomTag>(), state.tags)
        assertEquals("the fallback keeps the filter's tags", setOf(2L), state.filter.tagIds)
        assertEquals("the results still apply them", listOf(loveMe, peak), state.results)
        assertEquals(
            "the fallback keeps the open picker's ticks",
            CustomTagIds(tagIds = setOf(1L, 2L), subTagIds = emptySet()),
            state.picker?.selected,
        )
    }

    @Test
    fun listsFail_filterTagsStayRemovable() = runTest(mainDispatcherRule.testDispatcher) {
        customTags.failLists()
        val handle = SavedStateHandle(mapOf(TAG_IDS_KEY to longArrayOf(2L), SUB_TAG_IDS_KEY to longArrayOf(5L)))
        val viewModel = createViewModel(handle)

        val failed = viewModel.uiState.value
        assertTrue("the failed lists are reported", failed.listsUnavailable)
        assertEquals(
            "every filter ID has a strip entry, without a name",
            listOf(
                ActiveCustomTagFilter(CustomTagKind.TAG, 2L, name = null),
                ActiveCustomTagFilter(CustomTagKind.SUBTAG, 5L, name = null),
            ),
            failed.activeCustomTagFilters,
        )
        assertEquals("both IDs filter the results", listOf(peak), failed.results)

        viewModel.onRemoveCustomTag(CustomTagKind.TAG, 2L)
        val removed = viewModel.uiState.value
        assertEquals("the removed tag leaves the filter", SongFilter(subTagIds = setOf(5L)), removed.filter)
        assertEquals(
            "its entry leaves the strip",
            listOf(ActiveCustomTagFilter(CustomTagKind.SUBTAG, 5L, name = null)),
            removed.activeCustomTagFilters,
        )
        assertEquals("the results drop it", listOf(peak, yesterday), removed.results)
    }

    @Test
    fun activeCustomTagFilters_loadedLists_carryNames() = runTest(mainDispatcherRule.testDispatcher) {
        val handle = SavedStateHandle(mapOf(TAG_IDS_KEY to longArrayOf(2L, 1L), SUB_TAG_IDS_KEY to longArrayOf(1L)))
        val state = createViewModel(handle).uiState.value

        assertEquals("loaded lists are available", false, state.listsUnavailable)
        assertEquals(
            "each entry carries its list's name, tags first, each kind in library order",
            listOf(
                ActiveCustomTagFilter(CustomTagKind.TAG, 1L, "Acid"),
                ActiveCustomTagFilter(CustomTagKind.TAG, 2L, "Techno"),
                ActiveCustomTagFilter(CustomTagKind.SUBTAG, 1L, "Warm-up"),
            ),
            state.activeCustomTagFilters,
        )
    }

    @Test
    fun activeCustomTagFilters_knownInLibraryOrderThenUnknownById() {
        val entries = activeCustomTagFilters(
            tagIds = linkedSetOf(9L, 2L, 7L, 5L),
            subTagIds = linkedSetOf(4L, 3L),
            tags = listOf(tag(CustomTagKind.TAG, 5L, "Acid"), tag(CustomTagKind.TAG, 2L, "Techno")),
            subTags = listOf(tag(CustomTagKind.SUBTAG, 3L, "Warm-up")),
        )

        assertEquals(
            "known names in list order, then unknown IDs ascending, tags before subtags",
            listOf(
                ActiveCustomTagFilter(CustomTagKind.TAG, 5L, "Acid"),
                ActiveCustomTagFilter(CustomTagKind.TAG, 2L, "Techno"),
                ActiveCustomTagFilter(CustomTagKind.TAG, 7L, null),
                ActiveCustomTagFilter(CustomTagKind.TAG, 9L, null),
                ActiveCustomTagFilter(CustomTagKind.SUBTAG, 3L, "Warm-up"),
                ActiveCustomTagFilter(CustomTagKind.SUBTAG, 4L, null),
            ),
            entries,
        )
    }

    @Test
    fun restore_fromSavedStateHandle_rebuildsFilter() = runTest(mainDispatcherRule.testDispatcher) {
        val handle = SavedStateHandle()
        val first = createViewModel(handle)
        first.onTitleChange("love")
        first.onYearChange("1999")
        first.pick(tagIds = setOf(2L), subTagIds = setOf(1L))
        first.onToggleFilters()

        // A new ViewModel over the same handle, as after process death.
        val restored = createViewModel(handle).uiState.value

        assertEquals("the title is restored", "love", restored.title)
        assertEquals("the year text is restored", "1999", restored.year)
        assertEquals(
            "the filter is rebuilt from the saved fields",
            SongFilter(title = "love", year = 1999, tagIds = setOf(2L), subTagIds = setOf(1L)),
            restored.filter,
        )
        assertEquals("the collapsed card is restored", false, restored.filtersExpanded)
        assertEquals("the results follow the restored filter", listOf(loveMe), restored.results)
    }

    @Test
    fun play_usesWholeResultAsQueue() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onTitleChange("love")
        val results = viewModel.uiState.value.results.orEmpty()
        assertEquals("two songs match", listOf(loveMe, lovesong), results)

        val calls = mutableListOf<Pair<List<Song>, Song>>()
        every { playbackController.playFrom(any(), any()) } answers { calls += firstArg<List<Song>>() to secondArg() }

        viewModel.play(lovesong)

        assertEquals("one tap starts playback once", 1, calls.size)
        assertEquals("the queue is the whole result in order", results, calls.single().first)
        assertEquals("playback starts at the tapped song", lovesong, calls.single().second)
    }

    @Test
    fun shuffleAll_emptyResults_isNoOp() = runTest(mainDispatcherRule.testDispatcher) {
        val viewModel = createViewModel()
        val seeded = Random(SHUFFLE_SEED)
        viewModel.random = seeded
        val calls = mutableListOf<Pair<List<Song>, Random>>()
        every { playbackController.shuffleAll(any(), any()) } answers { calls += firstArg<List<Song>>() to secondArg() }
        viewModel.onTitleChange("love")

        viewModel.shuffleAll()
        assertEquals("Shuffle over results calls the controller once", 1, calls.size)
        assertEquals("Shuffle plays the results", listOf(loveMe, lovesong), calls.single().first)
        assertSame("Shuffle passes the seeded random", seeded, calls.single().second)

        viewModel.onTitleChange("no such song")
        assertEquals("nothing matches", emptyList<Song>(), viewModel.uiState.value.results)
        viewModel.shuffleAll()

        assertEquals("Shuffle over no results calls the controller no more", 1, calls.size)
    }

    // A result's ⋮ (Task 6): the options sheet's playlist actions, as on Search. One existing playlist makes the
    // created playlist's id 2, apart from both the song id "1" and the picked playlist 7. Trivial stub: no-op bodies →
    // no addSongs call → RED. Mutation: createPlaylistWithSong(songId, name) → a playlist named "1" → RED.
    @Test
    fun optionsSheetActions_reachThePlaylistRepository() = runTest(mainDispatcherRule.testDispatcher) {
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

/**
 * [SongFinder] over a fixed [library] (already in library order) and fixed song-to-tag [links]: title and artist
 * match any part ignoring case, year exactly, and every tag and subtag ID must be carried.
 */
private class FakeSongFinder(
    library: List<Song>,
    private val links: Map<String, CustomTagIds>,
) : SongFinder {
    private val library = MutableStateFlow(library)

    override fun observeMatches(filter: Flow<SongFilter>): Flow<List<Song>> =
        combine(library, filter) { songs, songFilter -> songs.filter { songFilter.fakeMatches(it) } }

    private fun SongFilter.fakeMatches(song: Song): Boolean {
        val ids = links[song.id] ?: CustomTagIds.EMPTY
        return song.title.contains(title.trim(), ignoreCase = true) &&
            song.artist.contains(artist.trim(), ignoreCase = true) &&
            song.album.contains(album.trim(), ignoreCase = true) &&
            (year == null || song.year == year) &&
            ids.tagIds.containsAll(tagIds) &&
            ids.subTagIds.containsAll(subTagIds)
    }
}

/** Both custom tag lists as flows the test edits; [failLists] makes them throw, as a broken table would. */
private class FakeCustomTagLists(tags: List<CustomTag>, subTags: List<CustomTag>) {
    val tags = MutableStateFlow(tags)
    val subTags = MutableStateFlow(subTags)
    private val failed = MutableStateFlow(false)

    val repository: CustomTagRepository = object : CustomTagRepository by mockk(relaxed = true) {
        override fun observeCustomTags(kind: CustomTagKind): Flow<List<CustomTag>> {
            val list = if (kind == CustomTagKind.TAG) this@FakeCustomTagLists.tags else this@FakeCustomTagLists.subTags
            return combine(list, failed) { entries, isFailed ->
                check(!isFailed) { "custom_tags is broken" }
                entries
            }
        }
    }

    fun failLists() {
        failed.value = true
    }
}

private fun tag(kind: CustomTagKind, id: Long, name: String) = CustomTag(kind = kind, id = id, name = name)

private fun song(id: String, title: String, artist: String, year: Int) = Song(
    id = id,
    title = title,
    artist = artist,
    album = "Album $id",
    albumId = id.toLong(),
    durationMs = 180_000L,
    trackNumber = 1,
    year = year,
    contentUri = "content://media/external/audio/media/$id",
    artworkUri = "rolla-artwork://song/$id?v=1",
)
