package com.rolla.musicplayer.feature.playlists

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.accentText
import com.rolla.musicplayer.core.designsystem.theme.destructiveText
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.testing.drawLatestDialog
import com.rolla.musicplayer.core.testing.isColorNear
import com.rolla.musicplayer.core.testing.pixelsIn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

// NATIVE graphics: rows are measured from their text, and the delete dialog's button ink is probed in pixels.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistDetailScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // The hosted state. setContent runs once per test (ruling P5 rule 1); a test that needs a second state flips this.
    private var state by mutableStateOf(PlaylistDetailUiState())

    // Theme colours read inside the composition, for the pixel probe.
    private var accent = Color.Unspecified
    private var destructive = Color.Unspecified
    private var primary = Color.Unspecified

    // Review Focus 2 / ruling 6: a user playlist's ⋮ offers Remove; a smart playlist's never does. One setContent,
    // then the playlist flips to smart (ruling P5). Control: Remove is present and works on the user playlist first,
    // and Edit tags is in both sheets. Mutation: pass onRemoveFromPlaylist for smart playlists too → RED on the second
    // half.
    @Test
    fun `only a user playlist offers remove`() {
        val removed = mutableListOf<String>()
        setContent(loaded(songs(3)), onRemoveSong = { removed += it.id })
        composeRule.onNodeWithContentDescription("More options for Track 1").performClick()
        composeRule.onNodeWithText("Remove from playlist").assertExists("a user playlist's sheet offers Remove")
        composeRule.onNodeWithText("Remove from playlist").performClick()
        assertEquals("Remove passes the tapped track", listOf("1"), removed)

        composeRule.runOnIdle { state = loaded(songs(3), isUserPlaylist = false) }
        composeRule.onNodeWithContentDescription("More options for Track 1").performClick()
        composeRule.onNodeWithText("Edit tags").assertExists("a smart playlist's sheet still offers Edit tags")
        composeRule.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }

    // Review Focus 3 / 4a carry-over: the delete confirmation draws "Delete" in destructiveText (ruling P2) and
    // "Cancel" in accentText, never primary. Error is repainted to a far colour (the Task 1 fixture), because dark
    // error sits within the pixel tolerance of destructiveText and light uses one value for both. Control: both
    // labels exist and draw their ink. Mutation: Cancel → stock TextButton → primary ink → RED.
    @Test
    fun `the delete dialog colours its buttons correctly`() {
        setContent(loaded(songs(2)), repaintError = true)
        openOverflowItem("Delete playlist")
        composeRule.onNodeWithText("Delete").assertExists("the dialog shows Delete")
        composeRule.onNodeWithText("Cancel").assertExists("the dialog shows Cancel")
        val bitmap = composeRule.drawLatestDialog()
        val deleteInk = composeRule.pixelsIn(bitmap, composeRule.onNodeWithText("Delete").getUnclippedBoundsInRoot())
        val cancelInk = composeRule.pixelsIn(bitmap, composeRule.onNodeWithText("Cancel").getUnclippedBoundsInRoot())
        assertTrue("Delete draws destructiveText", deleteInk.any { isColorNear(destructive, it) })
        assertFalse("Delete must not draw error", deleteInk.any { isColorNear(ERROR_SEPARATOR, it) })
        assertTrue("Cancel draws accentText", cancelInk.any { isColorNear(accent, it) })
        assertFalse("Cancel must not draw primary", cancelInk.any { isColorNear(primary, it) })
    }

    // Delete confirms, then leaves. Control: Cancel neither deletes nor leaves. Mutation: drop onNavigateUp after
    // delete → RED.
    @Test
    fun `confirming delete deletes and navigates up`() {
        val calls = mutableListOf<String>()
        setContent(loaded(songs(2)), onNavigateUp = { calls += "up" }, onDeletePlaylist = { calls += "delete" })
        openOverflowItem("Delete playlist")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Delete").assertDoesNotExist()
        assertEquals("Cancel neither deletes nor leaves", emptyList<String>(), calls)

        openOverflowItem("Delete playlist")
        composeRule.onNodeWithText("Delete").performClick()
        assertEquals("Delete deletes the playlist, then leaves", listOf("delete", "up"), calls)
    }

    // Rename opens the name dialog with the current title. Control: the dialog is absent before the tap and present
    // after it. Mutation: initialName = "" → the field lacks the title → RED.
    // LEGACY looper, as PlaylistsTabTest.createDialogFollowsTheHoistedFlag: under the default PAUSED looper Robolectric
    // never idles while a Dialog hosts a text field (AppNotIdleException; reproduced here with the dialog alone).
    @Suppress("DEPRECATION")
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun `rename opens the dialog with the current name`() {
        val renamed = mutableListOf<String>()
        setContent(loaded(songs(2)), onRenamePlaylist = { renamed += it })
        composeRule.onNodeWithText("Rename playlist").assertDoesNotExist()
        openOverflowItem("Rename")
        composeRule.onNodeWithText("Rename playlist").assertExists("the rename dialog opens")
        val field = composeRule.onNode(hasSetTextAction()).fetchSemanticsNode()
        assertEquals(
            "the name field starts with the playlist's title",
            "Road trip",
            field.config[SemanticsProperties.EditableText].text,
        )
        composeRule.onNode(hasSetTextAction()).performTextReplacement("Summer")
        composeRule.onNodeWithText("Rename").performClick()
        assertEquals("Rename passes the new name", listOf("Summer"), renamed)
    }

    // Ruling 5: sort labels. uiState.sortLabel drives the header; one setContent, then the state flips (ruling P5).
    // Control: the user label shows "Custom order". Mutation: one label for all → RED.
    @Test
    fun `the sort label describes the order`() {
        setContent(loaded(songs(2)))
        composeRule.onNodeWithText("Custom order").assertExists("a user playlist's sort label")
        composeRule.runOnIdle { state = loaded(songs(2), isUserPlaylist = false, sortLabel = "Play count") }
        composeRule.onNodeWithText("Play count").assertExists("a smart playlist's sort label")
        composeRule.onNodeWithText("Custom order").assertDoesNotExist()
    }

    // 4a/Phase 2 carry-over: empty-state copy says tracks. Control: a non-empty playlist shows no empty message (after
    // the message was shown, so the flip proves the branch). Mutation: "songs" copy → RED.
    @Test
    fun `an empty playlist says so in tracks copy`() {
        setContent(loaded(emptyList()))
        composeRule.onNodeWithText("No tracks in \"Road trip\" yet.").assertExists("the empty playlist says so")
        composeRule.runOnIdle { state = loaded(songs(2)) }
        composeRule.onNodeWithText("Track 1").assertExists("the filled playlist renders its tracks")
        composeRule.onNodeWithText("No tracks in \"Road trip\" yet.").assertDoesNotExist()
    }

    // The accessible reorder actions survive the rewrite. Control: a smart playlist has none (after the user playlist
    // showed them, in one setContent). Mutation: drop the customActions modifier → RED.
    @Test
    fun `user playlist rows keep move up and move down`() {
        setContent(loaded(songs(3)))
        assertEquals("row 2 can move both ways", listOf("Move up", "Move down"), rowActionLabels("Track 2"))
        composeRule.runOnIdle { state = loaded(songs(3), isUserPlaylist = false) }
        composeRule.onNodeWithText("Track 2").assertExists("the smart playlist renders row 2")
        assertEquals("a smart playlist row has no reorder actions", emptyList<String>(), rowActionLabels("Track 2"))
    }

    // Review Focus 5: the last row scrolls fully above the pill (position, not presence: Task 5 found "is displayed"
    // blind on Robolectric's root). Control: the list exists. Mutation: drop the bottom contentPadding → RED.
    @Test
    fun `the last track scrolls fully above the mini-player`() {
        val songs = songs(20)
        setContent(loaded(songs), miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(PLAYLIST_SONG_LIST_TEST_TAG).performScrollToIndex(songs.lastIndex)
        val last = composeRule.onNodeWithText("Track 20").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(
            "the last track's bottom ${last.bottom} is at or above the pill's top ${root.bottom - 60.dp}",
            last.bottom <= root.bottom - 60.dp,
        )
    }

    // Ruling 10 / controller ruling P6: the sheet closes when its track leaves the list the rows render. Control: the
    // sheet is open on track 2 first. Mutation: drop currentSongs from the SongOptionsHost call → the sheet stays →
    // RED.
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

    private fun openOverflowItem(label: String) {
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText(label).performClick()
    }

    /** The custom accessibility action labels on the row that shows [title]. */
    private fun rowActionLabels(title: String): List<String> =
        composeRule.onAllNodes(
            hasAnyDescendant(hasText(title)) and SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions),
        ).fetchSemanticsNodes().flatMap { node -> node.config[SemanticsActions.CustomActions].map { it.label } }

    // Defaulted test-host knobs: each test overrides only the inputs it probes. Long only because the stateless content
    // takes thirteen distinct inputs, each passed by name.
    @Suppress("LongParameterList", "LongMethod")
    private fun setContent(
        uiState: PlaylistDetailUiState,
        onNavigateUp: () -> Unit = {},
        onRemoveSong: (Song) -> Unit = {},
        onRenamePlaylist: (String) -> Unit = {},
        onDeletePlaylist: () -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
        repaintError: Boolean = false,
    ) {
        state = uiState
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val base = MaterialTheme.colorScheme
                MaterialTheme(colorScheme = if (repaintError) base.copy(error = ERROR_SEPARATOR) else base) {
                    accent = MaterialTheme.colorScheme.accentText
                    destructive = MaterialTheme.colorScheme.destructiveText
                    primary = MaterialTheme.colorScheme.primary
                    CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                        PlaylistDetailContent(
                            uiState = state,
                            userPlaylists = emptyList(),
                            onNavigateUp = onNavigateUp,
                            onPlayAll = {},
                            onShuffleAll = {},
                            onSongClick = {},
                            onRemoveSong = onRemoveSong,
                            onReorder = {},
                            onRenamePlaylist = onRenamePlaylist,
                            onDeletePlaylist = onDeletePlaylist,
                            onAddSongToPlaylist = { _, _ -> },
                            onCreatePlaylistAndAddSong = { _, _ -> },
                            onEditTagsClick = {},
                        )
                    }
                }
            }
        }
    }

    private companion object {
        // Far from every token on screen, so a probe can tell a repainted error from destructiveText (Task 1 fixture).
        val ERROR_SEPARATOR = Color(0xFF00FF00)

        fun loaded(
            songs: List<Song>,
            isUserPlaylist: Boolean = true,
            sortLabel: String = if (isUserPlaylist) "Custom order" else "Name",
        ) = PlaylistDetailUiState(
            title = "Road trip",
            songs = songs,
            isUserPlaylist = isUserPlaylist,
            isLoading = false,
            sortLabel = sortLabel,
        )

        fun songs(n: Int): List<Song> = (1..n).map { index ->
            Song(
                id = "$index",
                title = "Track $index",
                artist = "The Wanderers",
                album = "Sunrise",
                albumId = 1L,
                durationMs = 200_000L,
                trackNumber = index,
                year = 2021,
                contentUri = "content://media/external/audio/media/$index",
                // Empty so rows render ArtworkPlaceholder and Coil never starts a load under Robolectric.
                artworkUri = "",
            )
        }
    }
}
