package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholder
import com.rolla.musicplayer.core.model.Artist
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ArtistRowTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backgroundColor = Color.Unspecified
    private var placeholderColor = Color.Unspecified

    private fun setRow(artist: Artist, onClick: () -> Unit = {}) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                backgroundColor = MaterialTheme.colorScheme.background
                placeholderColor = MaterialTheme.colorScheme.artworkPlaceholder
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    Box(Modifier.testTag(ROW_HOST)) {
                        ArtistRow(artist = artist, onClick = onClick)
                    }
                }
            }
        }
    }

    // Mutation: swap albumCount and songCount in the subtitle → "5 albums, 2 tracks" → RED.
    @Test
    fun `subtitle counts albums then tracks`() {
        setRow(Artist(id = 1L, name = "Queen", albumCount = 2, songCount = 5))
        composeRule.onNodeWithText("2 albums, 5 tracks").assertExists()
    }

    // Mutation: drop the leading Spacer(listThumbStart) → the name starts at 64 dp → RED.
    @Test
    fun `name starts at the list text column and the row is a two-line row`() {
        setRow(Artist(id = 1L, name = "Queen", albumCount = 1, songCount = 1))
        val name = composeRule.onNodeWithText("Queen", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.listTextStart.value, name.left.value, 0.5f)
        val row = composeRule.onNodeWithTag(ROW_HOST).getUnclippedBoundsInRoot()
        assertEquals(RollaDimens.listRowHeight.value, (row.bottom - row.top).value, 0.5f)
    }

    // Mutation: ArtworkThumbnail ignores `shape` (clips shapes.small, 11 dp) → the probe at (5 dp, 5 dp) lands inside
    // the rounded square, so it reads the placeholder colour, not the background → RED. (5,5) is 2.9 dp outside a 48 dp
    // circle.
    @Test
    fun `avatar is a circle`() {
        setRow(Artist(id = 1L, name = "Queen", albumCount = 1, songCount = 1, artworkUri = ""))
        // Guard: if a token change made the two colours equal, the probes below would prove nothing.
        assertFalse(
            "background and placeholder must be distinguishable",
            isColorNear(backgroundColor, placeholderColor.toArgb()),
        )
        val avatar = composeRule.onNodeWithTag(ARTIST_AVATAR_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bitmap = composeRule.drawRoot()
        val probe = with(composeRule.density) {
            bitmap.getPixel((avatar.left + 5.dp).roundToPx(), (avatar.top + 5.dp).roundToPx())
        }
        // Inside the circle (18 dp from its centre) but above the 24 dp glyph, which covers 12..36 dp: the exact centre
        // is the glyph, not the placeholder fill.
        val inside = with(composeRule.density) {
            bitmap.getPixel((avatar.left + 24.dp).roundToPx(), (avatar.top + 6.dp).roundToPx())
        }
        assertTrue("outside the circle must be the background", isColorNear(backgroundColor, probe))
        assertTrue("inside the circle must be the placeholder", isColorNear(placeholderColor, inside))
    }

    // Mutation: drop clickable from the row → performClick throws / count stays 0 → RED.
    @Test
    fun `tapping the row calls onClick once`() {
        var clicks = 0
        setRow(Artist(id = 1L, name = "Queen", albumCount = 1, songCount = 1), onClick = { clicks++ })
        composeRule.onNodeWithText("Queen").performClick()
        assertEquals(1, clicks)
    }

    private companion object {
        const val ROW_HOST = "row_host"
    }
}
