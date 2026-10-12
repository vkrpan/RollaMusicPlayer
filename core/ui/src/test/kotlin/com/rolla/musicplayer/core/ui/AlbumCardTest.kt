package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge
import com.rolla.musicplayer.core.model.Album
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
class AlbumCardTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backgroundColor = Color.Unspecified
    private var placeholderColor = Color.Unspecified

    private fun album(
        id: Long = 1L,
        title: String = "Jazz",
        artist: String = "Queen",
        songCount: Int = 3,
        artworkUri: String = "",
    ) = Album(id = id, title = title, artist = artist, songCount = songCount, artworkUri = artworkUri)

    private fun setCard(
        album: Album,
        modifier: Modifier = Modifier,
        subtitle: String? = null,
        onClick: () -> Unit = {},
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                backgroundColor = MaterialTheme.colorScheme.background
                placeholderColor = MaterialTheme.colorScheme.artworkPlaceholderLarge
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    if (subtitle == null) {
                        AlbumCard(album = album, onClick = onClick, modifier = modifier)
                    } else {
                        AlbumCard(album = album, onClick = onClick, modifier = modifier, subtitle = subtitle)
                    }
                }
            }
        }
    }

    // Mutation: default subtitle = tracksCountLabel(album.songCount) → "Queen" missing → RED.
    @Test
    fun `card shows title and artist by default`() {
        setCard(album(title = "Jazz", artist = "Queen"))
        composeRule.onNodeWithText("Jazz").assertExists()
        composeRule.onNodeWithText("Queen").assertExists()
    }

    // Mutation: ignore the subtitle parameter → RED.
    @Test
    fun `subtitle override replaces the artist`() {
        setCard(album(title = "Jazz", artist = "Queen"), subtitle = "12 tracks")
        composeRule.onNodeWithText("12 tracks").assertExists()
        composeRule.onNodeWithText("Queen").assertDoesNotExist()
    }

    // Mutation: drop aspectRatio(1f) from the art box → its height collapses to 0 (no image loads in the test) → RED.
    @Test
    fun `art box is square and fills the card width`() {
        setCard(album(), modifier = Modifier.width(150.dp))
        val art = composeRule.onNodeWithTag(ALBUM_CARD_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(150f, (art.right - art.left).value, 0.5f)
        assertEquals(150f, (art.bottom - art.top).value, 0.5f)
    }

    // Proves presence, not draw order. Mutation: draw the placeholder only when artworkUri is blank → missing for a
    // non-blank uri, and a failed load would show nothing → RED.
    @Test
    fun `placeholder is composed even when there is an artwork uri`() {
        setCard(album(artworkUri = "content://media/external/audio/albumart/99"))
        composeRule.onNodeWithTag(ALBUM_CARD_PLACEHOLDER_TAG, useUnmergedTree = true).assertExists()
    }

    // Mutation: drop clip(featureCard) from the art box → the corner probe reads the placeholder colour → RED.
    @Test
    fun `art corners are rounded`() {
        setCard(album(), modifier = Modifier.width(150.dp))
        // Guard: if a token change made the two colours equal, the corner probe would prove nothing.
        assertFalse(
            "background and placeholder must be distinguishable",
            isColorNear(backgroundColor, placeholderColor.toArgb()),
        )
        val art = composeRule.onNodeWithTag(ALBUM_CARD_ART_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        val bitmap = composeRule.drawRoot()
        val corner = with(composeRule.density) {
            bitmap.getPixel((art.left + 1.dp).roundToPx(), (art.top + 1.dp).roundToPx())
        }
        assertTrue(isColorNear(backgroundColor, corner))
    }

    // Review Focus 5. Mutations: drop maxLines = 1 from the title, or from the subtitle → the long card grows taller →
    // RED.
    @Test
    fun `a long title keeps both cards in a row level`() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Row {
                    AlbumCard(album(id = 1L, title = "Short"), {}, Modifier.width(150.dp).testTag("a"))
                    AlbumCard(
                        album(
                            id = 2L,
                            title = "A very long album title that would wrap onto several lines",
                            artist = "A very long artist name that would wrap onto several lines",
                        ),
                        {},
                        Modifier.width(150.dp).testTag("b"),
                    )
                }
            }
        }
        val a = composeRule.onNodeWithTag("a").getUnclippedBoundsInRoot()
        val b = composeRule.onNodeWithTag("b").getUnclippedBoundsInRoot()
        assertEquals((a.bottom - a.top).value, (b.bottom - b.top).value, 0.5f)
    }

    // Mutation: drop clickable from the card → RED.
    @Test
    fun `tapping the card calls onClick`() {
        var clicks = 0
        setCard(album(), onClick = { clicks++ })
        composeRule.onNodeWithText("Jazz").performClick()
        assertEquals(1, clicks)
    }
}
