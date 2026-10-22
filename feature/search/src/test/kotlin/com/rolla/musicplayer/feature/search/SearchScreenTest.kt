package com.rolla.musicplayer.feature.search

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.SearchResults
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The One UI Search screen (Phase 6b rulings 3–5). Dark theme; NATIVE graphics because rows are measured from their
 * text (the pill-clearance test reads bounds). One setContent per test: two-state tests flip a mutableStateOf.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Ruling 4 / ruling 7 copy. Trivial stub: render nothing → "Tracks" is missing → RED. Mutation: header text
    // "Songs" → "Tracks" is missing (and "Songs" exists) → RED.
    @Test
    fun resultsAreSectionedTracksAlbumsArtists() {
        setContent(uiState = SearchUiState.Results(results(songs = listOf(song("1", "Song One")))))

        // The "Tracks" assertion is the control for the "Songs" absence check below: the header renders, under the
        // new copy.
        composeRule.onNodeWithText("Tracks").assertExists("the track section is headed Tracks")
        composeRule.onNodeWithText("Albums").assertExists("the album section is headed Albums")
        composeRule.onNodeWithText("Artists").assertExists("the artist section is headed Artists")
        composeRule.onNodeWithText("Songs").assertDoesNotExist()
    }

    // Ruling 4 options sheet. Trivial stub: the ⋮ does nothing → "Edit tags" never appears → RED. Mutation: omit
    // onMoreClick on the track rows → RED. Control: before the click the sheet is closed.
    @Test
    fun aTracksMoreOpensTheOptionsSheetAndEditTags() {
        val edited = mutableListOf<String>()
        setContent(
            uiState = SearchUiState.Results(results(songs = listOf(song("1", "Song One"), song("2", "Song Two")))),
            onEditTagsClick = { edited += it.title },
        )
        composeRule.onNodeWithText("Edit tags").assertDoesNotExist()

        composeRule.onNodeWithContentDescription("More options for Song One").performClick()

        composeRule.onNodeWithText("Add to playlist").assertExists("the sheet offers Add to playlist")
        composeRule.onNodeWithText("Edit tags").assertExists("the sheet offers Edit tags")
        composeRule.onNodeWithText("Edit tags").performClick()
        assertEquals("Edit tags reports the row's track", listOf("Song One"), edited)
    }

    // Review Focus 5 / 6a ruling 10. Trivial stub: the sheet ignores the results → it stays → RED. Mutation:
    // currentSongs = null → RED. Control: the sheet is open first.
    @Test
    fun theSheetClosesWhenItsTrackLeavesTheResults() {
        var uiState: SearchUiState by mutableStateOf(
            SearchUiState.Results(results(songs = listOf(song("1", "Song One"), song("2", "Song Two")))),
        )
        setContent(uiStateProvider = { uiState })
        composeRule.onNodeWithContentDescription("More options for Song One").performClick()
        composeRule.onNodeWithText("Edit tags").assertExists("control: the sheet is open on Song One")

        composeRule.runOnIdle { uiState = SearchUiState.Results(results(songs = listOf(song("2", "Song Two")))) }

        composeRule.onNodeWithText("Song Two").assertExists("the remaining result still renders")
        composeRule.onNodeWithText("Edit tags").assertDoesNotExist()
    }

    // Fix round 1: returning after more than 5 s in the background restarts the WhileSubscribed search, which passes
    // through Loading. The sheet must survive Loading, then close on Empty (the track no longer matches). Trivial stub:
    // the sheet ignores the results → it survives Empty → RED. Mutation: `.orEmpty()` for every non-Results state →
    // the sheet closes on Loading → RED. Control: the sheet is open first.
    @Test
    fun theSheetSurvivesLoadingAndClosesOnEmpty() {
        var uiState: SearchUiState by mutableStateOf(
            SearchUiState.Results(results(songs = listOf(song("1", "Song One")))),
        )
        setContent(uiStateProvider = { uiState })
        composeRule.onNodeWithContentDescription("More options for Song One").performClick()
        composeRule.onNodeWithText("Edit tags").assertExists("control: the sheet is open on Song One")

        composeRule.runOnIdle { uiState = SearchUiState.Loading }
        composeRule.onNodeWithText("Edit tags").assertExists("the sheet stays open while the search reloads")

        composeRule.runOnIdle { uiState = SearchUiState.Empty }
        composeRule.onNodeWithText("No results").assertExists("the Empty state renders")
        composeRule.onNodeWithText("Edit tags").assertDoesNotExist()
    }

    // Ruling 4 recents, Review Focus 3. Trivial stub: inert rows → nothing reported → RED. Mutation: wire Close to
    // onRecentSearchClick → the report reads search:jazz → RED. Each channel tags its reports, so the exact list also
    // shows the Close tap did not also fire the row's "search again".
    @Test
    fun recentRowsSearchAgainAndRemoveThemselves() {
        val reports = mutableListOf<String>()
        setContent(
            uiState = SearchUiState.Idle,
            recentSearches = listOf("queen", "jazz"),
            onRecentSearchClick = { reports += "search:$it" },
            onRemoveRecentSearch = { reports += "remove:$it" },
        )

        composeRule.onNodeWithContentDescription("Search again for queen").performClick()
        composeRule.onNodeWithContentDescription("Remove jazz from recent searches").performClick()

        assertEquals("one report per channel, in tap order", listOf("search:queen", "remove:jazz"), reports)
    }

    // Ruling 4 loading (6a §8.7). Trivial stub: the spinner is kept → one indeterminate node → RED. Mutation: keep
    // CircularProgressIndicator in the Loading branch → RED. Control: a result row exists before the flip.
    @Test
    fun loadingShowsNoSpinner() {
        var uiState: SearchUiState by mutableStateOf(
            SearchUiState.Results(results(songs = listOf(song("1", "Song One")))),
        )
        setContent(uiStateProvider = { uiState })
        composeRule.onNodeWithText("Song One").assertExists("control: a result row renders first")

        composeRule.runOnIdle { uiState = SearchUiState.Loading }

        composeRule.onNodeWithText("Song One").assertDoesNotExist()
        composeRule.onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate),
        ).assertCountEquals(0)
    }

    // Ruling 3, Review Focus 1 (pill half; the keyboard half is Task 8 on the device). Trivial stub: no bottom
    // padding → the last row ends at the panel's bottom → RED. Mutation: drop the list's contentPadding → RED.
    // Control: before the scroll the last row is not composed.
    @Test
    fun theLastResultClearsThePill() {
        val songs = List(30) { song("$it", "Track $it") }
        // Tracks only, so the last track is the list's last item.
        val tracksOnly = SearchResults(songs = songs, albums = emptyList(), artists = emptyList())
        setContent(uiState = SearchUiState.Results(tracksOnly), miniPlayerInset = 120.dp)
        composeRule.onNodeWithText("Track 29").assertDoesNotExist()

        // The Tracks header is item 0, so the last track is item 30.
        composeRule.onNodeWithTag(SEARCH_RESULTS_LIST_TEST_TAG).performScrollToIndex(songs.size)

        val last = composeRule.onNodeWithText("Track 29").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the last row's bottom ${last.bottom} sits at least 120 dp above the root's ${root.bottom}",
            last.bottom <= root.bottom - 120.dp + 1.dp,
        )
    }

    // Ruling 4 (the field still takes focus on open). Trivial stub: no focus request → RED. Mutation: remove
    // requestFocus() → RED. The up count is 0 before the click.
    @Test
    fun theFieldIsFocusedOnOpenAndBackNavigatesUp() {
        var ups = 0
        setContent(uiState = SearchUiState.Idle, onNavigateUp = { ups++ })

        composeRule.onNode(hasSetTextAction()).assertIsFocused()
        assertEquals("no up before the click", 0, ups)
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals("Navigate up calls onNavigateUp once", 1, ups)
    }

    // Task 1 review M1: OneUiSearchField's IME Search calls the screen's onSearch, which hides the keyboard. A fake
    // keyboard controller is the seam. Trivial stub: onSearch = {} → no hide → RED. Mutation: drop the onSearch
    // argument from the OneUiSearchField call → RED. Control: nothing hid the keyboard before the action.
    @Test
    fun theSearchActionHidesTheKeyboard() {
        val keyboard = CountingKeyboardController()
        setContent(uiState = SearchUiState.Idle, keyboardController = keyboard)
        assertEquals("control: the keyboard is not hidden before the action", 0, keyboard.hides)

        composeRule.onNode(hasSetTextAction()).performImeAction()

        assertEquals("IME Search hides the keyboard once", 1, keyboard.hides)
    }

    // Defaulted test-host knobs: each test overrides only the inputs it probes.
    @Suppress("LongParameterList")
    private fun setContent(
        uiState: SearchUiState = SearchUiState.Idle,
        uiStateProvider: () -> SearchUiState = { uiState },
        recentSearches: List<String> = emptyList(),
        onNavigateUp: () -> Unit = {},
        onRecentSearchClick: (String) -> Unit = {},
        onRemoveRecentSearch: (String) -> Unit = {},
        onEditTagsClick: (Song) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
        keyboardController: SoftwareKeyboardController? = null,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(
                    LocalMiniPlayerInset provides miniPlayerInset,
                    LocalSoftwareKeyboardController provides (keyboardController ?: NoOpKeyboardController),
                ) {
                    SearchScreen(
                        query = "",
                        uiState = uiStateProvider(),
                        recentSearches = recentSearches,
                        userPlaylists = emptyList(),
                        onQueryChanged = {},
                        onNavigateUp = onNavigateUp,
                        onSongClick = {},
                        onAlbumClick = {},
                        onArtistClick = {},
                        onRecentSearchClick = onRecentSearchClick,
                        onClearRecentSearches = {},
                        onRemoveRecentSearch = onRemoveRecentSearch,
                        onAddSongToPlaylist = { _, _ -> },
                        onCreatePlaylistAndAddSong = { _, _ -> },
                        onEditTagsClick = onEditTagsClick,
                    )
                }
            }
        }
    }

    /** Counts hide() calls; show() is ignored, as the field may ask for the keyboard when it takes focus. */
    private class CountingKeyboardController : SoftwareKeyboardController {
        var hides = 0
        override fun show() = Unit
        override fun hide() {
            hides++
        }
    }

    private object NoOpKeyboardController : SoftwareKeyboardController {
        override fun show() = Unit
        override fun hide() = Unit
    }

    private companion object {
        fun song(id: String, title: String) = Song(
            id = id,
            title = title,
            artist = "Artist $id",
            album = "Album",
            albumId = 1L,
            durationMs = 200_000L,
            trackNumber = null,
            year = 2001,
            contentUri = "content://media/external/audio/media/$id",
            // Empty so rows render ArtworkPlaceholder and Coil never starts a load under Robolectric.
            artworkUri = "",
        )

        fun results(songs: List<Song>) = SearchResults(
            songs = songs,
            albums = listOf(Album(id = 5L, title = "Night Album", artist = "Queen", songCount = 3, artworkUri = "")),
            artists = listOf(Artist(id = 9L, name = "Queen", albumCount = 1, songCount = 3)),
        )
    }
}
