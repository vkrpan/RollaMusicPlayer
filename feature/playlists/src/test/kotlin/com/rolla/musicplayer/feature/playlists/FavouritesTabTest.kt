package com.rolla.musicplayer.feature.playlists

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.FAST_SCROLL_RAIL_TEST_TAG
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics, like the other Home tab tests: rows are measured from their text. Nothing here captures pixels.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FavouritesTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Review Focus 4. Mutations: render the empty state for null too (songs.isNullOrEmpty()) → the text exists → RED;
    // enabled = songs?.isEmpty() != true → Play and Shuffle are enabled while loading → RED.
    @Test
    fun `nothing renders before the favourites load`() {
        setContent(songs = null)
        composeRule.onNodeWithText("No favourite tracks yet").assertDoesNotExist()
        composeRule.onNodeWithTag(FAVOURITES_LIST_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play favourite tracks").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle favourite tracks").assertIsNotEnabled()
    }

    // Spec §7.3. Mutation: drop the list's bottom contentPadding → the last row ends under the 60 dp pill → RED.
    @Test
    fun `the last row scrolls fully above the mini-player`() {
        val songs = List(20) { song(id = "$it", title = "Track $it") }
        setContent(songs = songs, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(FAVOURITES_LIST_TEST_TAG).performScrollToIndex(songs.lastIndex)
        val last = composeRule.onNodeWithText("Track 19").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }

    // Review Focus 3. Mutation: enabled = true unconditionally → assertIsNotEnabled fails → RED.
    @Test
    fun `an empty list shows the empty state and disables Play and Shuffle`() {
        var plays = 0
        setContent(songs = emptyList(), onPlayClick = { plays++ }, onShuffleClick = { plays++ })
        composeRule.onNodeWithText("No favourite tracks yet").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play favourite tracks").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle favourite tracks").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Play favourite tracks").performClick()
        assertEquals(0, plays)
    }

    // Mutation: wire Play to onShuffleClick → RED.
    @Test
    fun `Play and Shuffle call their own callbacks`() {
        var played = 0
        var shuffled = 0
        setContent(songs = listOf(songA, songB), onPlayClick = { played++ }, onShuffleClick = { shuffled++ })
        composeRule.onNodeWithContentDescription("Play favourite tracks").performClick()
        composeRule.onNodeWithContentDescription("Shuffle favourite tracks").performClick()
        assertEquals(1, played)
        assertEquals(1, shuffled)
    }

    // Mutation: the row's onClick passes the first song instead of the tapped one → RED.
    @Test
    fun `tapping a row plays that track`() {
        var tapped: Song? = null
        setContent(songs = listOf(songA, songB), onSongClick = { tapped = it })
        composeRule.onNodeWithText(songB.title).performClick()
        assertEquals(songB, tapped)
    }

    // Mutation: onMoreClick = {} (a dead ⋮) → the sheet never opens → RED.
    @Test
    fun `the overflow opens the options sheet and Edit tags reports the track`() {
        var edited: Song? = null
        setContent(songs = listOf(songA), onEditTagsClick = { edited = it })
        composeRule.onNodeWithContentDescription("More options for ${songA.title}").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()
        assertEquals(songA, edited)
    }

    // Mutation: reuse SONG_LIST_TEST_TAG on this list → the favourites tag is missing → RED
    // (baseline-profile contract).
    @Test
    fun `the list carries its own test tag`() {
        setContent(songs = listOf(songA))
        composeRule.onNodeWithTag(FAVOURITES_LIST_TEST_TAG).assertExists()
    }

    // Mutation: leave FavouritesList without the container → no rail → RED.
    @Test
    fun `the rail jumps to the first favourite of a letter`() {
        setContent(songs = alphaMango())
        jumpTo("M")
        assertAtListTop(FAVOURITES_LIST_TEST_TAG, "Mango 0")
    }

    private fun jumpTo(label: String) {
        val rail = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).fetchSemanticsNode()
        val actions = rail.config[SemanticsActions.CustomActions]
        val matches = actions.filter { it.label == "Jump to $label" }
        assertEquals("One \"Jump to $label\" action among ${actions.map { it.label }}", 1, matches.size)
        composeRule.runOnIdle { matches.single().action() }
        composeRule.waitForIdle()
    }

    private fun assertAtListTop(listTag: String, rowText: String) {
        val listTop = composeRule.onNodeWithTag(listTag).getUnclippedBoundsInRoot().top
        val rowTop = composeRule.onNodeWithText(rowText).getUnclippedBoundsInRoot().top
        assertEquals(listTop.value, rowTop.value, 0.5f)
    }

    // Defaulted test-host knobs: each test overrides only the inputs it probes.
    @Suppress("LongParameterList")
    private fun setContent(
        songs: List<Song>?,
        onSongClick: (Song) -> Unit = {},
        onShuffleClick: () -> Unit = {},
        onPlayClick: () -> Unit = {},
        onEditTagsClick: (Song) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    FavouritesTabContent(
                        songs = songs,
                        userPlaylists = emptyList(),
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
        val songA = song(id = "a", title = "Alpha")
        val songB = song(id = "b", title = "Bravo")

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

        /** As in TracksTabTest: 25 "Alpha" titles, then 25 "Mango" titles, in library order; every artist is "Zed". */
        fun alphaMango(): List<Song> =
            (List(25) { song(id = "a$it", title = "Alpha $it") } + List(25) { song(id = "m$it", title = "Mango $it") })
                .map { it.copy(artist = "Zed") }
    }
}
