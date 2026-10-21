package com.rolla.musicplayer.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics, like FolderDetailScreenTest: rows are measured from their text. Nothing here captures pixels.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // The hosted state. setContent runs once per test (ruling P5 rule 1); a test that needs a second state flips this.
    private var state by mutableStateOf(AlbumDetailUiState())

    // Spec §12 / ruling 6: the ⋮ on a track opens the options sheet (it was dead). Control: the row exists. Mutation:
    // onMoreClick = {} → no "Add to playlist" → RED.
    @Test
    fun `a track's more button opens the options sheet`() {
        setContent(loaded(songs(3)))
        composeRule.onNodeWithText("Track 1").assertExists("the row is rendered")
        composeRule.onNodeWithText("Add to playlist").assertDoesNotExist()
        composeRule.onAllNodesWithContentDescription("More options for Track 1")[0].performClick()
        composeRule.onNodeWithText("Add to playlist").assertExists("the options sheet offers Add to playlist")
        composeRule.onNodeWithText("Edit tags").assertExists("the options sheet offers Edit tags")
    }

    // Ruling 6: Edit tags hands the tapped track out. Control: nothing is passed before the tap; the tapped id is
    // passed after it. Mutation: pass songs.first() → RED for track 2.
    @Test
    fun `edit tags passes the tapped track`() {
        val edited = mutableListOf<String>()
        setContent(loaded(songs(3)), onEditTagsClick = { edited += it.id })
        assertEquals("nothing is passed before the tap", emptyList<String>(), edited)
        composeRule.onNodeWithContentDescription("More options for Track 2").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()
        assertEquals("Edit tags passes the tapped track", listOf("2"), edited)
    }

    // Ruling 5 / spec §8.7: the hero subtitle reads "<artist> · N tracks" and the sort label is "Track number".
    // Control: the title is in the top bar. Mutation: songCountLabel "songs" copy → RED.
    @Test
    fun `the header says artist and track count`() {
        setContent(loaded(songs(2)))
        composeRule.onNodeWithText("A Night at the Opera").assertExists("the album title is in the top bar")
        composeRule.onNodeWithText("Queen · 2 tracks").assertExists("the hero subtitle is artist · N tracks")
        composeRule.onNodeWithText("Track number").assertExists("the sort label describes the album order")
    }

    // Shuffle and Play are real (spec §12). Control: both callbacks start at 0. Mutation: swap the callbacks → RED.
    @Test
    fun `shuffle and play call their actions`() {
        val calls = mutableListOf<String>()
        setContent(loaded(songs(2)), onShuffleClick = { calls += "shuffle" }, onPlayClick = { calls += "play" })
        assertEquals("no action runs before a tap", emptyList<String>(), calls)
        composeRule.onNodeWithContentDescription("Shuffle album").performClick()
        composeRule.onNodeWithContentDescription("Play album").performClick()
        assertEquals("Shuffle then Play ran their own actions", listOf("shuffle", "play"), calls)
    }

    // Review Focus 5: the last track scrolls fully above the pill. Control: the list exists. Mutation: drop the bottom
    // contentPadding → RED.
    @Test
    fun `the last track scrolls fully above the mini-player`() {
        val songs = songs(20)
        setContent(loaded(songs), miniPlayerInset = 60.dp)
        // The hero and the sort row are items 0 and 1, so the last track is item songs.size + 1.
        composeRule.onNodeWithTag(ALBUM_SONG_LIST_TEST_TAG).performScrollToIndex(songs.size + 1)
        val last = composeRule.onNodeWithText("Track 20").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the last track's bottom ${last.bottom} is at or above the pill's top ${root.bottom - 60.dp}",
            last.bottom <= root.bottom - 60.dp,
        )
    }

    // Review Focus 4: a 200-character album title keeps Shuffle and Play on screen. Control: Play is displayed.
    // Mutation: drop maxLines from the top bar title → the bar grows and pushes the panel → Play moves → RED.
    // Probe moved (implementer, mutation evidence): the brief's probe alone was blind to that mutant. The unclamped
    // title wraps to a 308 dp bar, but at w360dp-h640dp Play only moves from 285 dp to 529 dp, which is still on
    // screen. The test now also pins Play's position against a short title's, flipped in one setContent (ruling P5).
    @Test
    fun `a very long title keeps the controls visible`() {
        setContent(loaded(songs(2)))
        val shortTitlePlayTop = composeRule.onNodeWithContentDescription("Play album").getUnclippedBoundsInRoot().top
        composeRule.runOnIdle { state = loaded(songs(2), title = "A".repeat(200)) }
        composeRule.onNodeWithText("A".repeat(200)).assertExists("the long title is in the top bar")
        composeRule.onNodeWithContentDescription("Play album").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Shuffle album").assertIsDisplayed()
        val play = composeRule.onNodeWithContentDescription("Play album").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue("Play's bottom ${play.bottom} is on screen (${root.bottom})", play.bottom <= root.bottom)
        assertEquals(
            "the long title stays one line, so Play stays where a short title puts it",
            shortTitlePlayTop.value,
            play.top.value,
            POSITION_TOLERANCE_DP,
        )
    }

    // Not found: a gone album says so. Control: the message shows once loading ends with no album (the loading state,
    // which renders nothing, is the extra check). Mutation: treat null album as loading → message missing → RED.
    @Test
    fun `a missing album says it is not available`() {
        setContent(AlbumDetailUiState(isLoading = true))
        composeRule.onNodeWithText("Album not available").assertDoesNotExist()
        composeRule.runOnIdle { state = AlbumDetailUiState(isLoading = false, album = null) }
        composeRule.onNodeWithText("Album not available").assertExists("a null album after loading is not found")
    }

    // Ruling 10 / controller ruling P6 (Task 5 review, M1): the sheet closes when its track leaves the list the rows
    // render. Control: the sheet is open on track 2 first. Mutation: drop currentSongs from the SongOptionsHost call →
    // the sheet stays → RED.
    @Test
    fun `the options sheet closes when its track leaves the list`() {
        val songs = songs(3)
        setContent(loaded(songs))
        composeRule.onNodeWithContentDescription("More options for Track 2").performClick()
        composeRule.onNodeWithText("Edit tags").assertIsDisplayed()
        composeRule.runOnIdle { state = loaded(songs.filterNot { it.id == "2" }) }
        composeRule.onNodeWithText("Track 3").assertExists("the list still renders the remaining tracks")
        composeRule.onNodeWithText("Edit tags").assertDoesNotExist()
    }

    // Defaulted test-host knobs: each test overrides only the inputs it probes.
    @Suppress("LongParameterList")
    private fun setContent(
        uiState: AlbumDetailUiState,
        onNavigateUp: () -> Unit = {},
        onSongClick: (Song) -> Unit = {},
        onShuffleClick: () -> Unit = {},
        onPlayClick: () -> Unit = {},
        onEditTagsClick: (Song) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        state = uiState
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    AlbumDetailContent(
                        uiState = state,
                        userPlaylists = emptyList(),
                        onNavigateUp = onNavigateUp,
                        onSongClick = onSongClick,
                        onShuffleClick = onShuffleClick,
                        onPlayClick = onPlayClick,
                        onAddSongToPlaylist = { _, _ -> },
                        onCreatePlaylistAndAddSong = { _, _ -> },
                        onEditTagsClick = onEditTagsClick,
                    )
                }
            }
        }
    }

    private companion object {
        // Sub-pixel slack for comparing two layouts' positions in dp.
        const val POSITION_TOLERANCE_DP = 0.5f

        fun loaded(songs: List<Song>, title: String = "A Night at the Opera") =
            AlbumDetailUiState(isLoading = false, album = album(title, songs.size), songs = songs)

        fun album(title: String = "A Night at the Opera", songCount: Int = 0) = Album(
            id = 1L,
            title = title,
            artist = "Queen",
            songCount = songCount,
            // Empty so the hero renders ArtworkPlaceholder and Coil never starts a load under Robolectric.
            artworkUri = "",
        )

        fun songs(n: Int): List<Song> = (1..n).map { index ->
            Song(
                id = "$index",
                title = "Track $index",
                artist = "Queen",
                album = "A Night at the Opera",
                albumId = 1L,
                durationMs = 200_000L,
                trackNumber = index,
                year = 1975,
                contentUri = "content://media/external/audio/media/$index",
                artworkUri = "",
            )
        }
    }
}
