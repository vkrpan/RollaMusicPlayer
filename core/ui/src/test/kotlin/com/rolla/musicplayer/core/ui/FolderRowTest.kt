package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Folder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FolderRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setRow(folder: Folder, onClick: () -> Unit = {}) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                FolderRow(folder = folder, onClick = onClick)
            }
        }
    }

    // Mutations: subtitle = folder.path → "Music/Chill" → RED; title = folder.path → RED.
    @Test
    fun `shows the folder name over its display path`() {
        setRow(Folder(path = "Music/Chill", name = "Chill", songCount = 3, artworkUri = ""))
        composeRule.onNodeWithText("Chill").assertExists()
        composeRule.onNodeWithText("/Internal storage/Music/Chill").assertExists()
    }

    // Mutations: Alignment.TopStart → RED; drop the inset padding → both edges off by folderBadgeInset → RED;
    // unwrap the Box and drop `.align` → the badge lays out after the thumbnail (86 vs 64 dp) → RED; testTag before
    // the padding → width reads 22 dp → RED (the right-edge check trips first, 66 vs 64 dp).
    @Test
    fun `the badge sits inset in the thumbnail's bottom-end corner`() {
        setRow(Folder(path = "Music", name = "Music", songCount = 1, artworkUri = ""))
        val thumb = composeRule.onNodeWithTag(FOLDER_THUMB_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val badge = composeRule.onNodeWithTag(FOLDER_BADGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(thumb.right - RollaDimens.folderBadgeInset, badge.right)
        assertEquals(thumb.bottom - RollaDimens.folderBadgeInset, badge.bottom)
        assertEquals(RollaDimens.folderBadge, badge.right - badge.left)
    }

    // Mutation: onClick = {} in FolderRow → 0 calls → RED.
    @Test
    fun `tapping the row calls onClick once`() {
        var clicks = 0
        setRow(Folder(path = "Music", name = "Music", songCount = 1, artworkUri = ""), onClick = { clicks++ })
        composeRule.onNodeWithText("Music").performClick()
        assertEquals(1, clicks)
    }
}
