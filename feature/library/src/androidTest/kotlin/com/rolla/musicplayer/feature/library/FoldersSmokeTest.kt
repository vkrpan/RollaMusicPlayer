package com.rolla.musicplayer.feature.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.Song
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §15 smoke path "Folders, open, lists songs" (Phase 7 ruling 5). The host stands in for the navigation between
 * the two screens (FolderDetail(folderPath), covered by the JVM NavHost tests): it shows [FoldersTabContent] until a
 * folder is tapped, then [FolderDetailContent] with that folder's songs from an in-memory map. Never the Hilt routes.
 */
@RunWith(AndroidJUnit4::class)
class FoldersSmokeTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // In library order (spec §9.0), as FolderRepository delivers them: the A–Z rail's sections rely on it.
    private val folders = listOf(
        Folder(path = "Music/Jazz", name = "Jazz", songCount = 1),
        Folder(path = "Music/Rock", name = "Rock", songCount = 2),
    )
    private val songsByFolder = mapOf(
        "Music/Rock" to listOf(song("1", "Bohemian Rhapsody"), song("2", "Hotel California")),
        "Music/Jazz" to listOf(song("3", "So What")),
    )

    // Control: the Jazz song is not listed (fails if the detail showed every song, or the wrong folder). Mutation: the
    // folder row passes another path to onFolderClick → the Rock songs never show → RED.
    @Test
    fun openingAFolderListsItsSongs() {
        var openPath by mutableStateOf<String?>(null)
        composeTestRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                val path = openPath
                if (path == null) {
                    FoldersTabContent(folders = folders, scanState = ScanState.Idle, onFolderClick = { openPath = it })
                } else {
                    FolderDetailContent(
                        title = folders.first { it.path == path }.name,
                        songs = songsByFolder.getValue(path),
                        userPlaylists = emptyList(),
                        onNavigateUp = { openPath = null },
                        onSongClick = {},
                        onShuffleClick = {},
                        onPlayClick = {},
                        onAddSongToPlaylist = { _, _ -> },
                        onCreatePlaylistAndAddSong = { _, _ -> },
                        onEditTagsClick = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Rock").performClick()

        composeTestRule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hotel California").assertIsDisplayed()
        composeTestRule.onNodeWithText("So What").assertDoesNotExist()
    }

    private fun song(id: String, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        durationMs = 200_000L,
        trackNumber = 1,
        year = 2001,
        contentUri = "content://media/external/audio/media/$id",
        artworkUri = "",
    )
}
