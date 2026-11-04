package com.rolla.musicplayer.feature.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §15 smoke path "swipe to Playlists, +, create" (Phase 7 ruling 5): Home's + sets the create-dialog flag, which
 * this host starts with, the way HomeRoute does. Hosts [PlaylistsTabContent] with an in-memory playlist list, never
 * [PlaylistsTab], which needs Hilt.
 */
@RunWith(AndroidJUnit4::class)
class PlaylistsTabSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // Control: before Create the row does not exist (fails if the host showed it regardless). Mutation: onConfirm stops
    // calling onCreatePlaylist → the row never appears → RED.
    @Test
    fun creatingAPlaylistShowsItsRow() {
        val playlists = mutableStateListOf<Playlist>()
        var showCreateDialog by mutableStateOf(true)
        composeTestRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PlaylistsTabContent(
                    smartPlaylists = emptyList(),
                    userPlaylists = playlists,
                    showCreateDialog = showCreateDialog,
                    onDismissCreateDialog = { showCreateDialog = false },
                    onCreatePlaylist = { name ->
                        playlists += Playlist(id = 1L, name = name, songCount = 0, createdAt = 0L, updatedAt = 0L)
                    },
                    onPlaylistClick = {},
                    onSmartPlaylistClick = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Road trip").assertDoesNotExist()

        composeTestRule.onNode(hasSetTextAction()).performTextInput("Road trip")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.onNodeWithText("Road trip").assertIsDisplayed()
    }
}
