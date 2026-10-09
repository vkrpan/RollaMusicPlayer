package com.rolla.musicplayer.feature.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke coverage for the stateless [TracksTabContent] (Flow 1's first hop: library -> tap song),
 * following `.claude/skills/add-ui-testing-compose/SKILL.md`. Hosts [TracksTabContent] directly with
 * fake songs -- never [TracksTab], which pulls in `hiltViewModel()`.
 */
@RunWith(AndroidJUnit4::class)
class TracksTabSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakeSongs = listOf(
        testSong(id = "1", title = "Bohemian Rhapsody", artist = "Queen"),
        testSong(id = "2", title = "Stairway to Heaven", artist = "Led Zeppelin"),
        testSong(id = "3", title = "Hotel California", artist = "Eagles"),
    )

    @Test
    fun songTitles_areAllDisplayed() {
        setContent(songs = fakeSongs)

        fakeSongs.forEach { song ->
            composeTestRule.onNodeWithText(song.title).assertIsDisplayed()
        }
    }

    @Test
    fun clickingASongRow_invokesOnSongClickWithThatSong() {
        var clickedSong: Song? = null
        setContent(songs = fakeSongs, onSongClick = { clickedSong = it })

        composeTestRule.onNodeWithText("Stairway to Heaven").performClick()

        assertEquals(fakeSongs[1], clickedSong)
    }

    @Test
    fun emptySongs_showsEmptyStateMessage() {
        setContent(songs = emptyList())

        composeTestRule.onNodeWithText("No music found on this device").assertIsDisplayed()
    }

    private fun setContent(
        songs: List<Song>,
        onSongClick: (Song) -> Unit = {},
    ) {
        composeTestRule.setContent {
            RollaMusicPlayerTheme {
                TracksTabContent(
                    songs = songs,
                    scanState = ScanState.Idle,
                    selection = rememberSongSelectionState(),
                    userPlaylists = emptyList(),
                    onSongClick = onSongClick,
                    onShuffleClick = {},
                    onPlayClick = {},
                    onAddSongToPlaylist = { _, _ -> },
                    onCreatePlaylistAndAddSong = { _, _ -> },
                    onEditTagsClick = {},
                )
            }
        }
    }
}

private fun testSong(id: String, title: String, artist: String) = Song(
    id = id,
    title = title,
    artist = artist,
    album = "Sample Album",
    albumId = 1L,
    durationMs = 200_000L,
    trackNumber = 1,
    year = 2001,
    contentUri = "content://media/external/audio/media/$id",
    artworkUri = "",
)
