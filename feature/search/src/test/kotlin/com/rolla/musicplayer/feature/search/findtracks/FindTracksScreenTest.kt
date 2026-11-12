package com.rolla.musicplayer.feature.search.findtracks

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.CustomTagKind
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Find tracks' stateless screen (custom tags phase 4, Task 6). Dark theme; NATIVE graphics, because the large-font test
 * reads text layouts and bounds. One setContent per test: two-state tests flip a mutableStateOf.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FindTracksScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val removedFields = mutableListOf<FindTracksField>()
    private val removedTags = mutableListOf<Pair<CustomTagKind, Long>>()
    private val fieldChanges = mutableListOf<Pair<FindTracksField, String>>()
    private val playedSongs = mutableListOf<Song>()
    private var shuffleClicks = 0
    private var playClicks = 0
    private var toggleClicks = 0

    // Every active filter gets a chip, in order, and each ✕ removes its own filter. Trivial stub: no strip → the
    // "Remove filter Title: love" node is missing → RED. Mutation (test-control): omit the Year chip → "Remove filter
    // Year: 1999" missing → RED. The removal lists tell a cross-wired ✕ apart (each field and ID differs).
    // LongMethod: seven chips, each found, tapped and checked in one composition.
    @Suppress("LongMethod")
    @Test
    fun strip_showsEachActiveFilter_andRemoves() {
        setScreen {
            state(
                title = "love",
                artist = "Queen",
                album = "Opera",
                genre = "Rock",
                year = "1999",
                tagIds = setOf(TECHNO_ID),
                subTagIds = setOf(WARM_UP_ID),
                active = listOf(
                    ActiveCustomTagFilter(CustomTagKind.TAG, TECHNO_ID, "Techno"),
                    ActiveCustomTagFilter(CustomTagKind.SUBTAG, WARM_UP_ID, "Warm-up"),
                ),
            )
        }

        val labels = listOf("Title: love", "Artist: Queen", "Album: Opera", "Genre: Rock", "Year: 1999")
        labels.forEach { label ->
            composeRule.onNodeWithContentDescription("Remove filter $label")
                .assertExists("the strip shows a removable \"$label\" chip")
                .performClick()
        }
        composeRule.onNodeWithContentDescription("Remove filter Techno")
            .assertExists("the strip shows the tag by name")
            .performClick()
        composeRule.onNodeWithContentDescription("Remove filter Warm-up")
            .assertExists("the strip shows the subtag by name")
            .performClick()

        assertEquals(
            "each text chip's ✕ removes its own field, once",
            listOf(
                FindTracksField.TITLE,
                FindTracksField.ARTIST,
                FindTracksField.ALBUM,
                FindTracksField.GENRE,
                FindTracksField.YEAR,
            ),
            removedFields,
        )
        assertEquals(
            "each tag chip's ✕ removes its own kind and ID",
            listOf(CustomTagKind.TAG to TECHNO_ID, CustomTagKind.SUBTAG to WARM_UP_ID),
            removedTags,
        )
    }

    // Controller Ruling 6: a filter tag the lists do not name shows as "Unavailable tag" / "Unavailable subtag" while
    // the lists are unavailable, and its ✕ removes it; while they are available (loading) it is skipped, so no nameless
    // chip flashes. Trivial stub: skip every nameless entry → "Remove filter Unavailable tag" missing → RED. Mutation:
    // show nameless entries regardless of listsUnavailable → the chip stays after the flip → RED. Control: the named
    // tag shows in both states.
    // LongMethod: both states of one composition, with the control in each.
    @Suppress("LongMethod")
    @Test
    fun strip_unavailableTag_showsUnavailableChip_andRemoves() {
        var unavailable by mutableStateOf(true)
        setScreen {
            state(
                tagIds = setOf(TECHNO_ID, LOST_TAG_ID),
                subTagIds = setOf(LOST_SUBTAG_ID),
                active = listOf(
                    ActiveCustomTagFilter(CustomTagKind.TAG, TECHNO_ID, "Techno"),
                    ActiveCustomTagFilter(CustomTagKind.TAG, LOST_TAG_ID, name = null),
                    ActiveCustomTagFilter(CustomTagKind.SUBTAG, LOST_SUBTAG_ID, name = null),
                ),
                listsUnavailable = unavailable,
            )
        }

        composeRule.onNodeWithContentDescription("Remove filter Techno").assertExists("the named tag shows")
        composeRule.onNodeWithContentDescription("Remove filter Unavailable tag")
            .assertExists("a tag the failed lists cannot name stays visible")
            .performClick()
        composeRule.onNodeWithContentDescription("Remove filter Unavailable subtag")
            .assertExists("a subtag the failed lists cannot name stays visible")
            .performClick()
        assertEquals(
            "each unavailable chip's ✕ removes its own kind and ID",
            listOf(CustomTagKind.TAG to LOST_TAG_ID, CustomTagKind.SUBTAG to LOST_SUBTAG_ID),
            removedTags,
        )

        unavailable = false
        composeRule.onNodeWithContentDescription("Remove filter Techno").assertExists("the named tag still shows")
        composeRule.onNodeWithContentDescription("Remove filter Unavailable tag").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Remove filter Unavailable subtag").assertDoesNotExist()
    }

    // "Edit filters" unfolds the fields card and "Hide filters" folds it; each field reports its own field. Trivial
    // stub / mutation (test-control): always expanded → the card is still there after "Hide filters" → RED. A
    // cross-wired field (Year reported as Title) fails the change map. The fields follow their reported changes, as the
    // ViewModel's state does, so a field losing focus reports nothing stale.
    // LongMethod: folded, unfolded with typing, and folded again, in one composition.
    @Suppress("LongMethod")
    @Test
    fun editFilters_togglesFieldsCard() {
        var expanded by mutableStateOf(false)
        var title by mutableStateOf("")
        var year by mutableStateOf("")
        setScreen(
            onToggleFilters = { expanded = !expanded },
            onFieldChange = { field, value ->
                when (field) {
                    FindTracksField.TITLE -> title = value
                    FindTracksField.YEAR -> year = value
                    else -> Unit
                }
            },
        ) { state(filtersExpanded = expanded, title = title, year = year) }

        composeRule.onNodeWithTag(FIND_TRACKS_FIELDS_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Edit filters").assertExists("the folded card offers Edit filters").performClick()
        assertEquals("one tap toggles once", 1, toggleClicks)

        composeRule.onNodeWithTag(FIND_TRACKS_FIELDS_TAG).assertExists("Edit filters unfolds the fields card")
        listOf("Title", "Artist", "Album", "Genre", "Year", "Tags", "Subtags").forEach { label ->
            composeRule.onNodeWithText(label).assertExists("the card holds \"$label\"")
        }
        composeRule.onNodeWithText("Title").performTextInput("love")
        composeRule.onNodeWithText("Year").performTextInput("1999")
        assertEquals(
            "each field reports its own field",
            mapOf(FindTracksField.TITLE to "love", FindTracksField.YEAR to "1999"),
            fieldChanges.toMap(),
        )

        composeRule.onNodeWithText("Hide filters").assertExists("the unfolded card offers Hide filters").performClick()
        composeRule.onNodeWithTag(FIND_TRACKS_FIELDS_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Edit filters").assertExists("folded again")
    }

    // Review I1: the first filter typed from an empty filter (fields open, list at its top) shows the strip on screen,
    // not above the viewport. Trivial stub: no strip → the chip is missing → RED. Mutation: emit the strip item only
    // while it has chips → the new item lands above the anchored "toggle" item, out of view → RED. Control: before
    // typing there is no chip.
    @Test
    fun typingFirstFilter_keepsStripInView() {
        var title by mutableStateOf("")
        setScreen(
            onFieldChange = { field, value -> if (field == FindTracksField.TITLE) title = value },
        ) { state(filtersExpanded = true, title = title, results = listOf(LOVE_ME, LOVESONG)) }

        composeRule.onNodeWithContentDescription("Remove filter Title: love").assertDoesNotExist()
        composeRule.onNodeWithText("Title").performTextInput("love")

        composeRule.onNodeWithContentDescription("Remove filter Title: love").assertIsDisplayed()
    }

    // Review M4: the five fields chain with the keyboard's Next (Title → Artist → Album → Genre → Year), and Year ends
    // with Done. Trivial stub / mutation: no imeAction on Title → its action is Default and Next moves nothing → RED.
    @Test
    fun imeNext_movesFocusFromTitleToArtist() {
        setScreen { state(filtersExpanded = true) }

        listOf("Title", "Artist", "Album", "Genre").forEach { label ->
            composeRule.onNodeWithText(label)
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Next))
        }
        composeRule.onNodeWithText("Year")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Done))

        composeRule.onNodeWithText("Title").performClick()
        composeRule.onNodeWithText("Title").assertIsFocused()
        composeRule.onNodeWithText("Title").performImeAction()
        composeRule.onNodeWithText("Artist").assertIsFocused()
    }

    // The header counts the result and covers all of it; a row tap plays that row. Trivial stub: no results section →
    // "2 tracks" missing → RED. Mutation (test-control): a static header label → "2 tracks" missing → RED. The tapped
    // row is the second, so a row reporting the wrong song fails.
    @Test
    fun results_tapPlays_headerShowsCount() {
        setScreen { state(results = listOf(LOVE_ME, LOVESONG)) }

        composeRule.onNodeWithText("2 tracks").assertExists("the header counts the result")
        composeRule.onNodeWithText(LOVESONG.title).performClick()
        assertEquals("a row tap plays that song", listOf(LOVESONG), playedSongs)

        composeRule.onNodeWithContentDescription("Shuffle these tracks").performClick()
        assertEquals("Shuffle fires once", 1, shuffleClicks)
        assertEquals("Shuffle does not play", 0, playClicks)
        composeRule.onNodeWithContentDescription("Play these tracks").performClick()
        assertEquals("Play fires once", 1, playClicks)
        assertEquals("Play does not shuffle", 1, shuffleClicks)
    }

    // Nothing matches → the message, with Shuffle and Play disabled; before the first answer nothing shows. Trivial
    // stub / mutation (test-control): show nothing → the message is missing → RED.
    @Test
    fun empty_showsNoMatchMessage() {
        var results by mutableStateOf<List<Song>?>(null)
        setScreen { state(title = "zzz", results = results) }

        composeRule.onNodeWithText(NO_MATCH_MESSAGE).assertDoesNotExist()
        composeRule.onNodeWithText("0 tracks").assertDoesNotExist()

        results = emptyList()
        composeRule.onNodeWithText(NO_MATCH_MESSAGE).assertExists("an empty result says so")
        composeRule.onNodeWithText("0 tracks").assertExists("the header counts zero")
        composeRule.onNodeWithContentDescription("Play these tracks").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle these tracks").assertIsNotEnabled()
    }

    // At 1.5× font on a 360 dp screen, six filters wrap onto more lines: no chip's label is clipped or ellipsized, and
    // every chip stays inside the strip. Trivial stub / mutation (test-control): a Row instead of the FlowRow → the
    // later chips are squeezed and ellipsize → isLineEllipsized → RED.
    // LongMethod: the large-font host, six labels and per-chip text and bounds checks.
    @Suppress("LongMethod")
    @Test
    fun largeFont_stripWraps_noOverflow() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = LARGE_FONT)) {
                ScreenHost(
                    state(
                        title = "Lovesong",
                        artist = "The Cure",
                        album = "Disintegration",
                        genre = "Rock",
                        year = "1989",
                        tagIds = setOf(TECHNO_ID),
                        active = listOf(ActiveCustomTagFilter(CustomTagKind.TAG, TECHNO_ID, "Techno")),
                    ),
                )
            }
        }

        val labels = listOf(
            "Title: Lovesong",
            "Artist: The Cure",
            "Album: Disintegration",
            "Genre: Rock",
            "Year: 1989",
            "Techno",
        )
        val strip = composeRule.onNodeWithTag(FIND_TRACKS_STRIP_TAG).getUnclippedBoundsInRoot()
        val tops = labels.map { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            val chip = composeRule.onNodeWithText(label, useUnmergedTree = true)
            chip.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            // Not hasVisualOverflow: a one-line ellipsizing Text lays its paragraph out at the full max width and sizes
            // itself to the label, so didOverflowWidth (size < paragraph width) is true even for a label that fits.
            assertFalse("\"$label\" is not ellipsized", layout.isLineEllipsized(0))
            assertFalse("\"$label\" is not cut to fewer lines", layout.multiParagraph.didExceedMaxLines)
            val bounds = chip.getUnclippedBoundsInRoot()
            assertTrue(
                "\"$label\" ends at ${bounds.right}, inside ${strip.right}",
                bounds.right <= strip.right + 0.5.dp,
            )
            bounds.top.value.toInt()
        }
        assertTrue("the chips wrap onto at least two lines: tops $tops", tops.distinct().size >= 2)
    }

    private fun setScreen(
        onToggleFilters: () -> Unit = {},
        onFieldChange: (FindTracksField, String) -> Unit = { _, _ -> },
        uiState: () -> FindTracksUiState,
    ) {
        composeRule.setContent {
            ScreenHost(uiState(), onToggleFilters, onFieldChange)
        }
    }

    // LongMethod: the production screen's full parameter list, each event recorded.
    @Suppress("LongMethod")
    @Composable
    private fun ScreenHost(
        uiState: FindTracksUiState,
        onToggleFilters: () -> Unit = {},
        onFieldChange: (FindTracksField, String) -> Unit = { _, _ -> },
    ) {
        RollaMusicPlayerTheme(darkTheme = true) {
            Box(Modifier.fillMaxSize()) {
                FindTracksScreen(
                    uiState = uiState,
                    userPlaylists = emptyList(),
                    onNavigateUp = {},
                    onFieldChange = { field, value ->
                        fieldChanges += field to value
                        onFieldChange(field, value)
                    },
                    onRemoveTextFilter = { removedFields += it },
                    onRemoveCustomTag = { kind, id -> removedTags += kind to id },
                    onToggleFilters = {
                        toggleClicks++
                        onToggleFilters()
                    },
                    onOpenPicker = {},
                    onPickerPageChange = {},
                    onPickerQueryChange = { _, _ -> },
                    onPickerToggle = { _, _ -> },
                    onPickerDone = {},
                    onSongClick = { playedSongs += it },
                    onShuffleClick = { shuffleClicks++ },
                    onPlayClick = { playClicks++ },
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                )
            }
        }
    }

    private companion object {
        const val LARGE_FONT = 1.5f
        const val TECHNO_ID = 2L
        const val WARM_UP_ID = 1L
        const val LOST_TAG_ID = 7L
        const val LOST_SUBTAG_ID = 8L

        val LOVE_ME = song(id = "1", title = "Love me")
        val LOVESONG = song(id = "2", title = "Lovesong")
    }
}

/** A screen state whose filter matches its text fields and IDs, as the ViewModel derives it. */
@Suppress("LongParameterList")
private fun state(
    title: String = "",
    artist: String = "",
    album: String = "",
    genre: String = "",
    year: String = "",
    tagIds: Set<Long> = emptySet(),
    subTagIds: Set<Long> = emptySet(),
    active: List<ActiveCustomTagFilter> = emptyList(),
    results: List<Song>? = emptyList(),
    filtersExpanded: Boolean = false,
    listsUnavailable: Boolean = false,
) = FindTracksUiState(
    title = title,
    artist = artist,
    album = album,
    genre = genre,
    year = year,
    tags = emptyList(),
    subTags = emptyList(),
    filter = SongFilter(
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        year = year.toIntOrNull(),
        tagIds = tagIds,
        subTagIds = subTagIds,
    ),
    results = results,
    filtersExpanded = filtersExpanded,
    picker = null,
    listsUnavailable = listsUnavailable,
    activeCustomTagFilters = active,
)

private fun song(id: String, title: String) = Song(
    id = id,
    title = title,
    artist = "The Cure",
    album = "Disintegration",
    albumId = 1L,
    durationMs = 180_000L,
    trackNumber = 1,
    year = 1989,
    contentUri = "",
    // Empty, so rows draw their placeholder and Coil never starts a load under Robolectric.
    artworkUri = "",
)
