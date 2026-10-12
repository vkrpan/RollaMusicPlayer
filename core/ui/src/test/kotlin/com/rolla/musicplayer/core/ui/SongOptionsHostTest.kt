package com.rolla.musicplayer.core.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode

/** [SongOptionsHost]'s Add to playlist → New playlist… → name dialog path, hosted directly. */
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
        composeRule.onNodeWithText("New playlist…").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput("Road trip")
        composeRule.onNodeWithText("Create").performClick()
        composeRule.waitForIdle()

        assertEquals(listOf("Road trip" to songs[1].id), created)
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
