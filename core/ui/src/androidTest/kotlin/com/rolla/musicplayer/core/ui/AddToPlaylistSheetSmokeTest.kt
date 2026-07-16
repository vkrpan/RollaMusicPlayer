package com.rolla.musicplayer.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val WORKOUT_PLAYLIST_ID = 1L
private const val CHILL_PLAYLIST_ID = 2L

/**
 * Smoke coverage for [AddToPlaylistSheet] (Flow 3: add-to-playlist), following
 * `.claude/skills/add-ui-testing-compose/SKILL.md`'s ComposeUnitTest pattern -- the sheet is
 * presentational only, so this hosts it directly with fake playlists via [createComposeRule] and
 * asserts its two callbacks fire with the right arguments. [ModalBottomSheet] hosts fine under
 * this rule, no Activity/NavHost scaffolding required.
 */
@RunWith(AndroidJUnit4::class)
class AddToPlaylistSheetSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val fakePlaylists = listOf(
        testPlaylist(id = WORKOUT_PLAYLIST_ID, name = "Workout Mix"),
        testPlaylist(id = CHILL_PLAYLIST_ID, name = "Chill Evenings"),
    )

    @Test
    fun playlistNamesAndNewPlaylistRow_areAllDisplayed() {
        setContent(playlists = fakePlaylists)

        composeTestRule.onNodeWithText("Workout Mix").assertIsDisplayed()
        composeTestRule.onNodeWithText("Chill Evenings").assertIsDisplayed()
        composeTestRule.onNodeWithText("New playlist…").assertIsDisplayed()
    }

    @Test
    fun clickingAPlaylistRow_invokesOnPlaylistSelectedWithItsId() {
        var selectedId: Long? = null
        setContent(playlists = fakePlaylists, onPlaylistSelected = { selectedId = it })

        composeTestRule.onNodeWithText("Chill Evenings").performClick()

        assertEquals(CHILL_PLAYLIST_ID, selectedId)
    }

    @Test
    fun clickingNewPlaylistRow_invokesOnCreateNewPlaylist() {
        var createClicked = false
        setContent(playlists = fakePlaylists, onCreateNewPlaylist = { createClicked = true })

        composeTestRule.onNodeWithText("New playlist…").performClick()

        assertTrue(createClicked)
    }

    private fun setContent(
        playlists: List<Playlist>,
        onPlaylistSelected: (Long) -> Unit = {},
        onCreateNewPlaylist: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            RollaMusicPlayerTheme {
                AddToPlaylistSheet(
                    playlists = playlists,
                    onPlaylistSelected = onPlaylistSelected,
                    onCreateNewPlaylist = onCreateNewPlaylist,
                    onDismissRequest = {},
                )
            }
        }
    }
}

private fun testPlaylist(id: Long, name: String) = Playlist(
    id = id,
    name = name,
    songCount = 0,
    createdAt = 0L,
    updatedAt = 0L,
)
