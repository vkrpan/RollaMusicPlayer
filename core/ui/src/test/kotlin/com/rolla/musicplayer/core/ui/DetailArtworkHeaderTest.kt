package com.rolla.musicplayer.core.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.designsystem.theme.artworkPlaceholderLarge
import com.rolla.musicplayer.core.testing.drawRoot
import com.rolla.musicplayer.core.testing.isColorNear
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The detail screens' hero (spec §8.7): [DetailArtworkHeader]'s size and its circular (artist) clip. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailArtworkHeaderTest {

    // An activity rule (what createComposeRule() builds internally), so the clip test can use drawRoot().
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Spec §8.7 hero: artwork at detailArtworkSize, subtitle one line below. Control: the subtitle is shown. Mutation:
    // size the artwork listThumb → RED.
    @Test
    fun artworkIsDetailArtworkSizeAndTheSubtitleIsOneLine() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                DetailArtworkHeader(artworkUri = "", subtitle = "Queen · 12 tracks")
            }
        }
        composeRule.onNodeWithText("Queen · 12 tracks").assertExists()
        composeRule.onNodeWithTag(DETAIL_ARTWORK_TEST_TAG)
            .assertWidthIsEqualTo(RollaDimens.detailArtworkSize)
            .assertHeightIsEqualTo(RollaDimens.detailArtworkSize)
    }

    // 0017: the artist hero is a circle. Pixel probe at CORNER_PROBE diagonally in from the artwork box's top-left
    // corner. Geometry (detailArtworkSize 168 dp, extraLarge = panelRadius 26 dp): a point (d, d) lies inside the
    // 84 dp-radius circle only when d ≥ 84·(1 − 1/√2) ≈ 24.6 dp, and inside the 26 dp-radius rounded square when
    // d ≥ 26·(1 − 1/√2) ≈ 7.6 dp. So at d = 16 dp the circle shows the background (12 dp outside its edge) and the
    // rounded square shows the placeholder (12 dp inside its corner arc). A 4 dp probe would sit outside both shapes
    // and could not tell them apart. Control: the centre column, above the 60 dp glyph, is placeholder in both.
    // The hero draws the large placeholder, as Now Playing does (controller ruling P4, Phase 6a Task 5: this expected
    // colour was artworkPlaceholder, the list thumbnail's, until then).
    // Mutations: ignore circular → the circular variant's corner probe hits placeholder → RED; DetailArtworkHeader
    // passes large = false → the centre column is artworkPlaceholder, not artworkPlaceholderLarge → RED.
    @Test
    fun circularHeroClipsItsCorners() {
        var background = Color.Unspecified
        var placeholder = Color.Unspecified
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                background = MaterialTheme.colorScheme.background
                placeholder = MaterialTheme.colorScheme.artworkPlaceholderLarge
                Column(Modifier.fillMaxWidth().background(background)) {
                    DetailArtworkHeader(artworkUri = "", subtitle = "Artist", circular = true)
                    DetailArtworkHeader(artworkUri = "", subtitle = "Album", circular = false)
                }
            }
        }
        val artworks = composeRule.onAllNodesWithTag(DETAIL_ARTWORK_TEST_TAG)
        artworks.assertCountEquals(2)
        // Sorted by top: [0] is the circular hero, [1] the rounded square.
        val (circle, square) = artworks.fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.top }
        val bitmap = composeRule.drawRoot()
        fun pixel(box: Rect, x: Dp, y: Dp): Int = with(composeRule.density) {
            bitmap.getPixel((box.left + x.toPx()).toInt(), (box.top + y.toPx()).toInt())
        }
        val centreX = RollaDimens.detailArtworkSize / 2
        assertTrue("circle: centre column is placeholder", isColorNear(placeholder, pixel(circle, centreX, CONTROL_Y)))
        assertTrue("square: centre column is placeholder", isColorNear(placeholder, pixel(square, centreX, CONTROL_Y)))
        assertTrue(
            "circle: the corner probe shows the background",
            isColorNear(background, pixel(circle, CORNER_PROBE, CORNER_PROBE)),
        )
        assertTrue(
            "square: the corner probe shows the placeholder",
            isColorNear(placeholder, pixel(square, CORNER_PROBE, CORNER_PROBE)),
        )
    }

    private companion object {
        val CORNER_PROBE = 16.dp

        // A quarter of the way down the 168 dp box: inside both shapes and clear of the centred 60 dp glyph (54-114).
        val CONTROL_Y = 42.dp
    }
}
