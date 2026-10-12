package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun setRow(album: Album, onClick: () -> Unit = {}) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    AlbumRow(album = album, onClick = onClick)
                }
            }
        }
    }

    // Mutation: subtitle keeps the old "${artist} · ${songCount} songs" → RED.
    @Test
    fun `subtitle is artist and track count`() {
        setRow(Album(id = 7L, title = "Jazz", artist = "Queen", songCount = 12, artworkUri = ""))
        composeRule.onNodeWithText("Queen, 12 tracks").assertExists()
    }

    // Mutation: keep the old 56 dp thumbnail and 16 dp padding → text starts at 84 dp → RED.
    @Test
    fun `title starts at the list text column`() {
        setRow(Album(id = 7L, title = "Jazz", artist = "Queen", songCount = 1, artworkUri = ""))
        assertEquals(
            RollaDimens.listTextStart.value,
            composeRule.onNodeWithText("Jazz", useUnmergedTree = true).getUnclippedBoundsInRoot().left.value,
            0.5f,
        )
    }
}
