package com.rolla.musicplayer.feature.search

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.model.SearchResults
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val FAKE_ALBUM_ID = 1L
private const val FAKE_ARTIST_ID = 1L

/**
 * Smoke coverage for the stateless [SearchScreen] (Flow 2: search query -> tap result), following
 * `.claude/skills/add-ui-testing-compose/SKILL.md`. The search field is found via
 * [hasSetTextAction] -- a semantics-based matcher for the one editable text node on this screen --
 * rather than a testTag, since it is unambiguous without one.
 */
@RunWith(AndroidJUnit4::class)
class SearchScreenSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeResults = SearchResults(
        songs = listOf(testSong()),
        albums = listOf(testAlbum()),
        artists = listOf(testArtist()),
    )

    @Test
    fun resultsState_rendersAllThreeSections() {
        setContent(uiState = SearchUiState.Results(fakeResults))

        composeTestRule.onNodeWithText("Songs").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artists").assertIsDisplayed()
    }

    @Test
    fun typingInSearchField_invokesOnQueryChangedWithTypedText() {
        var lastQuery: String? = null
        setContent(uiState = SearchUiState.Idle, onQueryChanged = { lastQuery = it })

        composeTestRule.onNode(hasSetTextAction()).performTextInput("queen")

        assertEquals("queen", lastQuery)
    }

    @Test
    fun clickingASongResult_invokesOnSongClick() {
        var clickedSong: Song? = null
        setContent(uiState = SearchUiState.Results(fakeResults), onSongClick = { clickedSong = it })

        composeTestRule.onNodeWithText(fakeResults.songs.first().title).performClick()

        assertEquals(fakeResults.songs.first(), clickedSong)
    }

    @Test
    fun clickingAnAlbumResult_invokesOnAlbumClickWithItsId() {
        var clickedAlbumId: Long? = null
        setContent(uiState = SearchUiState.Results(fakeResults), onAlbumClick = { clickedAlbumId = it })

        composeTestRule.onNodeWithText(fakeResults.albums.first().title).performClick()

        assertEquals(FAKE_ALBUM_ID, clickedAlbumId)
    }

    @Suppress("LongParameterList")
    private fun setContent(
        uiState: SearchUiState,
        onQueryChanged: (String) -> Unit = {},
        onSongClick: (Song) -> Unit = {},
        onAlbumClick: (Long) -> Unit = {},
    ) {
        composeTestRule.setContent {
            RollaMusicPlayerTheme {
                SearchScreen(
                    query = "",
                    uiState = uiState,
                    recentSearches = emptyList(),
                    onQueryChanged = onQueryChanged,
                    onNavigateUp = {},
                    onSongClick = onSongClick,
                    onAlbumClick = onAlbumClick,
                    onArtistClick = {},
                    onRecentSearchClick = {},
                    onClearRecentSearches = {},
                )
            }
        }
    }
}

private fun testSong() = Song(
    id = "1",
    title = "Bohemian Rhapsody",
    artist = "Queen",
    album = "A Night at the Opera",
    albumId = FAKE_ALBUM_ID,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 1975,
    contentUri = "content://media/external/audio/media/1",
    artworkUri = "",
)

private fun testAlbum() = Album(
    id = FAKE_ALBUM_ID,
    title = "A Night at the Opera",
    artist = "Queen",
    songCount = 12,
    artworkUri = "",
)

private fun testArtist() = Artist(
    id = FAKE_ARTIST_ID,
    name = "Queen",
    albumCount = 15,
    songCount = 180,
)
