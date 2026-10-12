package com.rolla.musicplayer.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
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
import com.rolla.musicplayer.core.model.Folder
import com.rolla.musicplayer.core.model.folderName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics: the row geometry depends on its text, which LEGACY mode measures at ~36 dp per line.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FoldersTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Review Focus 3. Two folders named "Music". Mutations: key = { it.name } → duplicate key → crash → RED;
    // onFolderClick(folder.name) → ["Music", "Music"] → RED.
    @Test
    fun `same-name folders both show and open their own paths`() {
        val opened = mutableListOf<String>()
        setContent(folders = listOf(folder("Download/Music"), folder("Music")), onFolderClick = { opened += it })
        composeRule.onNodeWithText("/Internal storage/Music").performClick()
        composeRule.onNodeWithText("/Internal storage/Download/Music").performClick()
        assertEquals(listOf("Music", "Download/Music"), opened)
    }

    // Review Focus 1. Mutation: onFolderClick(folder.name) → "Internal storage" → RED.
    @Test
    fun `the root folder opens the empty path`() {
        val opened = mutableListOf<String>()
        setContent(folders = listOf(folder("")), onFolderClick = { opened += it })
        composeRule.onNodeWithText("Internal storage").performClick()
        assertEquals(listOf(""), opened)
    }

    // Mutation: treat null as empty → "No folders found" flashes → RED.
    @Test
    fun `nothing renders before the folders load`() {
        setContent(folders = null)
        composeRule.onNodeWithText("No folders found").assertDoesNotExist()
        composeRule.onNodeWithTag(FOLDER_LIST_TEST_TAG).assertDoesNotExist()
    }

    // Mutation: drop the Scanning branch → the empty message shows mid-scan → RED.
    @Test
    fun `scanning shows progress over an empty list`() {
        setContent(folders = emptyList(), scanState = ScanState.Scanning)
        composeRule.onNodeWithContentDescription("Scanning library").assertExists()
        composeRule.onNodeWithText("No folders found").assertDoesNotExist()
    }

    @Test
    fun `an empty library says no folders found`() {
        setContent(folders = emptyList(), scanState = ScanState.Done(0, 0))
        composeRule.onNodeWithText("No folders found").assertIsDisplayed()
    }

    // Mutation: drop the bottom contentPadding → RED (ArtistsTabTest's probe).
    @Test
    fun `the last row scrolls fully above the mini-player`() {
        val folders = List(20) { folder("Folder $it") }
        setContent(folders = folders, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(FOLDER_LIST_TEST_TAG).performScrollToIndex(folders.lastIndex)
        val last = composeRule.onNodeWithText("/Internal storage/Folder 19").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }

    private fun setContent(
        folders: List<Folder>?,
        scanState: ScanState = ScanState.Done(1, 0),
        onFolderClick: (String) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    FoldersTabContent(folders = folders, scanState = scanState, onFolderClick = onFolderClick)
                }
            }
        }
    }

    private companion object {
        fun folder(path: String) = Folder(path = path, name = folderName(path), songCount = 1, artworkUri = "")
    }
}
