package com.rolla.musicplayer.core.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge
import com.rolla.musicplayer.core.designsystem.theme.featureCard
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

    // Ruling 9: the card's ripple is clipped to featureCard, so a pressed card's corner stays the panel colour.
    // Pixel probe at the card's top-left 3 dp corner while pressed (performTouchInput { down(center) } then
    // mainClock.advanceTimeBy). Control: the art, above the glyph, shows the pressed ripple over the placeholder.
    // Mutation: drop the clip → the corner shows ripple tint → RED.
    // SDK 30: API 31+'s patterned RippleDrawable draws nothing in a Robolectric software draw (OneUiTabRowTest).
    @Test
    @Config(sdk = [30])
    fun pressedCardRippleIsClippedToTheCardShape() {
        composeRule.mainClock.autoAdvance = false
        setCard(album(), modifier = Modifier.width(CARD_WIDTH).testTag(CARD_TAG))
        val card = composeRule.onNodeWithTag(CARD_TAG).getUnclippedBoundsInRoot()
        fun Bitmap.at(x: Dp, y: Dp): Int = with(composeRule.density) {
            getPixel((card.left + x).roundToPx(), (card.top + y).roundToPx())
        }
        val idle = composeRule.drawRoot()
        assertTrue(
            "the art probe is placeholder before the press",
            isColorNear(placeholderColor, idle.at(ART_X, ART_Y)),
        )
        assertTrue("the corner is the panel before the press", isColorNear(backgroundColor, idle.at(CORNER, CORNER)))

        composeRule.onNodeWithTag(CARD_TAG).performTouchInput { down(center) }
        composeRule.mainClock.advanceTimeBy(RIPPLE_SETTLE_MS)
        val pressed = composeRule.drawRoot()

        assertFalse(
            "control: the pressed ripple tints the art",
            isColorNear(placeholderColor, pressed.at(ART_X, ART_Y)),
        )
        assertTrue(
            "the pressed card's corner stays the panel colour",
            isColorNear(backgroundColor, pressed.at(CORNER, CORNER)),
        )
    }

    // Phase 4a ruling 8, kept by ruling 9: only the ripple is clipped, never the card's text. The subtitle starts at
    // the card's left edge on its bottom line, so a featureCard clip on the whole card would cut its first glyph where
    // it reaches outside the bottom-left corner arc. "LLLL" puts a stem and a foot there. The probe counts text pixels
    // in that corner zone (more than ZONE_MARGIN outside the arc, inside the card). Mutation: clip the whole card (the
    // plan's literal `modifier.clip(featureCard).clickable(…)`) → no text pixel survives in the zone → RED.
    // Probe moved (implementer, mutation evidence): counting from the arc itself was blind to that mutant, because the
    // clip's anti-aliased edge tints a few pixels just outside the arc (mutation-T6-20-survived-original-probe.log).
    @Test
    fun theCardShapeNeverClipsTheSubtitle() {
        var shape: Shape = RectangleShape
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                backgroundColor = MaterialTheme.colorScheme.background
                shape = MaterialTheme.shapes.featureCard
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    AlbumCard(
                        album = album(),
                        onClick = {},
                        modifier = Modifier.width(CARD_WIDTH).testTag(CARD_TAG),
                        subtitle = "LLLL",
                    )
                }
            }
        }
        composeRule.onNodeWithText("LLLL").assertExists("the subtitle is rendered")
        val card = composeRule.onNodeWithTag(CARD_TAG).getUnclippedBoundsInRoot()
        val textPixelsInCorner = nonBackgroundPixelsOutsideBottomLeftArc(composeRule.drawRoot(), card, shape)
        assertTrue(
            "the subtitle's first glyph is drawn outside the corner arc ($textPixelsInCorner text pixels)",
            textPixelsInCorner > 0,
        )
    }

    /**
     * Counts the pixels of [bitmap] in [card]'s bottom-left corner box that lie more than ZONE_MARGIN outside
     * [shape]'s bottom-left arc and are not the background colour.
     */
    private fun nonBackgroundPixelsOutsideBottomLeftArc(bitmap: Bitmap, card: DpRect, shape: Shape): Int =
        with(composeRule.density) {
            val left = card.left.roundToPx()
            val top = card.top.roundToPx()
            val width = (card.right - card.left).roundToPx()
            val height = (card.bottom - card.top).roundToPx()
            val outline = shape.createOutline(Size(width.toFloat(), height.toFloat()), LayoutDirection.Ltr, this)
            val radius = (outline as Outline.Rounded).roundRect.bottomLeftCornerRadius.x
            val centreY = height - radius
            // The clip's anti-aliased edge partly covers pixels just outside the arc, so the zone starts a margin out.
            val zoneRadius = radius + ZONE_MARGIN.toPx()
            (0 until radius.toInt()).sumOf { x ->
                (centreY.toInt() until height).count { y ->
                    val dx = x + 0.5f - radius
                    val dy = y + 0.5f - centreY
                    dx * dx + dy * dy > zoneRadius * zoneRadius &&
                        !isColorNear(backgroundColor, bitmap.getPixel(left + x, top + y))
                }
            }
        }

    private companion object {
        const val CARD_TAG = "card"
        val CARD_WIDTH = 150.dp

        // Long enough for a pressed ripple to finish spreading and hold (OneUiTabRowTest's value).
        const val RIPPLE_SETTLE_MS = 1_000L

        // 3 dp in from the corner: outside the 20 dp featureCard arc, inside the card's box.
        val CORNER = 3.dp

        // The art's centre column, 20 dp down: inside the art and clear of the centred placeholder glyph.
        val ART_X = 75.dp
        val ART_Y = 20.dp

        // The anti-aliased band outside a clip's arc, which a clipped glyph can still tint (mutation evidence, T6-20).
        val ZONE_MARGIN = 2.dp
    }
}
