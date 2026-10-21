package com.rolla.musicplayer.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

// NATIVE graphics, like the Home tab tests: rows are measured from their text. Nothing here captures pixels.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutations: OneUiDetailTopBar(title = "") → "Chill" missing → RED; OneUiDetailTopBar(onNavigateUp = {}) → 0
    // calls → RED.
    @Test
    fun `the bar shows the folder name and navigates up`() {
        var ups = 0
        setContent(title = "Chill", songs = listOf(song("1", "One")), onNavigateUp = { ups++ })
        composeRule.onNodeWithText("Chill").assertExists()
        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        assertEquals(1, ups)
    }

    // Mutation: onClick = { onSongClick(songs.first()) } → plays "One" for a tap on "Two" → RED.
    @Test
    fun `tapping a track plays from it`() {
        val played = mutableListOf<String>()
        setContent(songs = listOf(song("1", "One"), song("2", "Two")), onSongClick = { played += it.id })
        composeRule.onNodeWithText("Two").performClick()
        assertEquals(listOf("2"), played)
    }

    // Mutation: swap the two callbacks → RED.
    @Test
    fun `shuffle and play call through`() {
        val calls = mutableListOf<String>()
        setContent(
            songs = listOf(song("1", "One")),
            onShuffleClick = { calls += "shuffle" },
            onPlayClick = { calls += "play" },
        )
        composeRule.onNodeWithContentDescription("Shuffle tracks in this folder").performClick()
        composeRule.onNodeWithContentDescription("Play tracks in this folder").performClick()
        assertEquals(listOf("shuffle", "play"), calls)
    }

    // Review Focus 5. Mutations: enabled = true → RED; drop the empty branch → the message is missing → RED.
    @Test
    fun `an emptied folder says so and disables play and shuffle`() {
        setContent(songs = emptyList())
        composeRule.onNodeWithText("No tracks in this folder").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Play tracks in this folder").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle tracks in this folder").assertIsNotEnabled()
    }

    // Mutations: treat null as empty → the message flashes → RED; enabled = songs?.isEmpty() != true → enabled → RED;
    // give Shuffle its own `enabled = true` → Shuffle is enabled → RED.
    @Test
    fun `nothing renders before the songs load`() {
        setContent(songs = null)
        composeRule.onNodeWithText("No tracks in this folder").assertDoesNotExist()
        composeRule.onNodeWithTag(FOLDER_SONG_LIST_TEST_TAG).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Play tracks in this folder").assertIsNotEnabled()
        composeRule.onNodeWithContentDescription("Shuffle tracks in this folder").assertIsNotEnabled()
    }

    // No dead ⋮ (ruling 8). Mutation: onMoreClick = {} → the sheet never opens → "Edit tags" missing → RED.
    @Test
    fun `the overflow opens the options sheet and edit tags names the song`() {
        val edited = mutableListOf<String>()
        setContent(songs = listOf(song("1", "One"), song("2", "Two")), onEditTagsClick = { edited += it.id })
        composeRule.onNodeWithContentDescription("More options for Two").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()
        assertEquals(listOf("2"), edited)
    }

    // Mutation: drop the list's bottom contentPadding → RED.
    @Test
    fun `the last row scrolls fully above the mini-player`() {
        val songs = List(20) { song("$it", "Track $it") }
        setContent(songs = songs, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(FOLDER_SONG_LIST_TEST_TAG).performScrollToIndex(songs.lastIndex)
        val last = composeRule.onNodeWithText("Track 19").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }

    // Ruling 10 / controller ruling P6: the sheet closes when its track leaves the list the rows render. One
    // setContent, then the list flips (ruling P5 rule 1). Control: the sheet is open on "Two" first. Mutation: drop
    // currentSongs from the SongOptionsHost call → the sheet stays → RED.
    @Test
    fun `the options sheet closes when its track leaves the list`() {
        var hosted by mutableStateOf(listOf(song("1", "One"), song("2", "Two"), song("3", "Three")))
        setContent(songs = null, songsProvider = { hosted })
        composeRule.onNodeWithContentDescription("More options for Two").performClick()
        composeRule.onNodeWithText("Edit tags").assertIsDisplayed()
        composeRule.runOnIdle { hosted = hosted.filterNot { it.id == "2" } }
        composeRule.onNodeWithText("Three").assertExists("the list still renders the remaining tracks")
        composeRule.onNodeWithText("Edit tags").assertDoesNotExist()
    }

    // Defaulted test-host knobs: each test overrides only the inputs it probes.
    @Suppress("LongParameterList")
    private fun setContent(
        songs: List<Song>?,
        // Read inside the composition, so a test can swap the list (a rescan) after setContent.
        songsProvider: () -> List<Song>? = { songs },
        title: String = "Music",
        onNavigateUp: () -> Unit = {},
        onSongClick: (Song) -> Unit = {},
        onShuffleClick: () -> Unit = {},
        onPlayClick: () -> Unit = {},
        onEditTagsClick: (Song) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    FolderDetailContent(
                        title = title,
                        songs = songsProvider(),
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
    }
}
