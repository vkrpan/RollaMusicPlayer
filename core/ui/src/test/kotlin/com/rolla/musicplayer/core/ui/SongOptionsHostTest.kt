package com.rolla.musicplayer.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** [SongOptionsHost]'s Add to playlist → New playlist → name dialog path, hosted directly. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SongOptionsHostTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val songs = listOf(song(id = "11", title = "First"), song(id = "22", title = "Second"))

    // Snapshot state, so the host's onDismiss closes the options sheet as a real screen would.
    private var shownSong by mutableStateOf<Song?>(null)

    // LEGACY looper for this test only: under the default PAUSED looper, Robolectric never idles while a Dialog hosts
    // a text field (AppNotIdleException), the same hang PlaylistsTabTest and SavePresetDialogTest document.
    // Mutation: swap the arguments in AddToPlaylistFlow's onConfirm (song.id, name) → ("22", "Road trip") → RED.
    @Suppress("DEPRECATION")
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun newPlaylistCreatesItWithTheSheetsSong() {
        val created = mutableListOf<Pair<String, String>>()
        shownSong = songs[1]
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = shownSong,
                    userPlaylists = emptyList(),
                    onDismiss = { shownSong = null },
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { name, songId -> created += name to songId },
                    onEditTagsClick = {},
                )
            }
        }

        composeRule.onNodeWithText("Add to playlist").performClick()
        // Phase 6a ruling 11: the entry is "New playlist" (One UI sheets drop the follow-up-dialog ellipsis).
        composeRule.onNodeWithText("New playlist").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Road trip")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Road trip" to songs[1].id), created)
    }

    // Ruling 6: Remove appears only when the caller passes onRemoveFromPlaylist, and it removes that track. Control:
    // Edit tags is present. Mutation: always show Remove → the second host shows it → RED.
    @Test
    fun removeFromPlaylistOnlyWhenOffered() {
        val removed = mutableListOf<String>()
        var offerRemove by mutableStateOf(true)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = song("7", "Seven"),
                    userPlaylists = emptyList(),
                    onDismiss = {},
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                    onRemoveFromPlaylist = if (offerRemove) { s -> removed += s.id } else null,
                )
            }
        }
        composeRule.onNodeWithText("Edit tags").assertExists()
        composeRule.onNodeWithText("Remove from playlist").performClick()
        assertEquals("Remove from playlist must remove the sheet's track", listOf("7"), removed)
        composeRule.runOnIdle { offerRemove = false }
        composeRule.onNodeWithText("Remove from playlist").assertDoesNotExist()
    }

    // Review Focus 1 / ruling 10: the sheet closes when its track leaves currentSongs. Control: while the track is
    // listed, onDismiss is not called. Mutation: ignore currentSongs → dismissed stays 0 → RED.
    @Test
    fun sheetClosesWhenItsTrackLeavesTheList() {
        var dismissed = 0
        var songs by mutableStateOf(listOf(song("7", "Seven"), song("8", "Eight")))
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = song("7", "Seven"),
                    userPlaylists = emptyList(),
                    onDismiss = { dismissed++ },
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                    currentSongs = songs,
                )
            }
        }
        composeRule.waitForIdle()
        assertEquals("the sheet must stay open while its track is listed", 0, dismissed)
        composeRule.runOnIdle { songs = listOf(song("8", "Eight")) }
        composeRule.waitForIdle()
        assertEquals("the sheet must close once its track leaves currentSongs", 1, dismissed)
    }

    // Ruling 10, second half: the add-to-playlist sheet that follows from the options sheet closes the same way.
    // Control: the picker is open (its heading exists) while the track is listed. Mutation: drop the effect's
    // addToPlaylistSong reset → the picker stays open → RED.
    @Test
    fun addToPlaylistSheetClosesWhenItsTrackLeavesTheList() {
        var songs by mutableStateOf(listOf(song("7", "Seven"), song("8", "Eight")))
        shownSong = songs[0]
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = shownSong,
                    userPlaylists = emptyList(),
                    onDismiss = { shownSong = null },
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                    currentSongs = songs,
                )
            }
        }
        composeRule.onNodeWithText("Add to playlist").performClick()
        composeRule.onNode(hasText("Add to playlist") and isHeading()).assertExists()
        composeRule.runOnIdle { songs = listOf(song("8", "Eight")) }
        composeRule.waitForIdle()
        composeRule.onNode(hasText("Add to playlist") and isHeading()).assertDoesNotExist()
    }

    // Review Focus 4: a 200-character title stays on one line in the sheet heading. Control: the heading exists.
    // Mutation: drop maxLines = 1 from the sheet title → its height exceeds one line → RED.
    @Test
    fun aLongTitleStaysOnOneLine() {
        val long = "A".repeat(200)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                SongOptionsHost(
                    song = song("7", long),
                    userPlaylists = emptyList(),
                    onDismiss = {},
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                )
            }
        }
        val heading = composeRule.onNode(hasText(long) and isHeading()).fetchSemanticsNode()
        assertEquals("the heading carries one text", 1, heading.config[SemanticsProperties.Text].size)
        val height = composeRule.onNode(hasText(long) and isHeading()).getUnclippedBoundsInRoot()
            .let { it.bottom - it.top }
        assertTrue("one line, was $height", height < 40.dp)
    }

    private companion object {
        fun song(id: String, title: String) = Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            durationMs = 1_000L,
            trackNumber = null,
            year = null,
            contentUri = "content://media/external/audio/media/$id",
            // Empty so no artwork load starts under Robolectric.
            artworkUri = "",
        )
    }
}
