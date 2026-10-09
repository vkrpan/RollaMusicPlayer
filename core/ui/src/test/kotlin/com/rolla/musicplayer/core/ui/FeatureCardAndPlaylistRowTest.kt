package com.rolla.musicplayer.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

// NATIVE graphics: under LEGACY, Robolectric measures every text line at ~36 dp and ~1 px per char, which makes the
// row-height and trailing-text-edge assertions meaningless.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureCardAndPlaylistRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun featureCardIsOneClickableNodeWithLabelAndCount() {
        var clicks = 0
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                FeatureCard("Favourite tracks", "5 tracks", emptyList(), onClick = { clicks++ })
            }
        }
        // Exactly one click target, and it is the merged node carrying both texts.
        composeRule.onAllNodes(hasClickAction()).assertCountEquals(1)
        composeRule.onNodeWithText("Favourite tracks")
            .assert(hasText("5 tracks") and hasClickAction())
            .performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun featureCardArtBoxIsTheMeasuredSize() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                FeatureCard("Recently added", "0 tracks", emptyList(), onClick = {})
            }
        }
        composeRule.onNodeWithTag("feature_card_art", useUnmergedTree = true)
            .assertWidthIsEqualTo(RollaDimens.featureCardSize)
            .assertHeightIsEqualTo(RollaDimens.featureCardSize)
    }

    @Test
    fun playlistRowIsSingleLineHeightWithTrailingCount() {
        val playlist = Playlist(id = 1L, name = "Light", songCount = 0, createdAt = 0L, updatedAt = 0L)
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(Modifier.width(360.dp).testTag("host")) { PlaylistRow(playlist, onClick = {}) }
            }
        }
        composeRule.onNodeWithTag("host").assertHeightIsEqualTo(RollaDimens.singleLineRowHeight)
        composeRule.onNodeWithText("0 tracks", useUnmergedTree = true)
            .getBoundsInRoot().right
            .assertIsEqualTo(360.dp - RollaDimens.listTrailingEnd, "count right", tolerance = 0.5.dp)
        composeRule.onNodeWithText("Light", useUnmergedTree = true)
            .getBoundsInRoot().left
            .assertIsEqualTo(RollaDimens.listTextStart, "name left", tolerance = 0.5.dp)
    }

    @Test
    fun playlistRowCountIsSingularOnlyForOneTrack() {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Column {
                    listOf(0, 1, 2).forEach { count ->
                        val playlist = Playlist(
                            id = count.toLong(),
                            name = "P$count",
                            songCount = count,
                            createdAt = 0L,
                            updatedAt = 0L,
                        )
                        PlaylistRow(playlist, onClick = {})
                    }
                }
            }
        }
        composeRule.onNodeWithText("0 tracks", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("1 track", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithText("2 tracks", useUnmergedTree = true).assertExists()
        assertEquals("1 track", tracksCountLabel(1))
        assertEquals("12 tracks", tracksCountLabel(12))
    }

    @Test
    fun featureCardArtShowsPlaceholderSingleImageOrCollageOfFirstFour() {
        var uris by mutableStateOf(emptyList<String>())
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                FeatureCard("Recently played", "0 tracks", uris, onClick = {})
            }
        }
        // Layout nodes only: Coil load failures for these fake URIs don't matter.
        assertArt(images = 0, cellPlaceholders = 0, backdrops = 0)

        uris = listOf("", "")
        assertArt(images = 0, cellPlaceholders = 0, backdrops = 0)

        uris = listOf("", " ")
        assertArt(images = 0, cellPlaceholders = 0, backdrops = 0)

        // A single image keeps the card's own large placeholder as its fallback: no per-cell backdrop.
        uris = listOf("a")
        assertArt(images = 1, cellPlaceholders = 0, backdrops = 0, cellWidth = RollaDimens.featureCardSize)

        uris = listOf("a", "b")
        assertArt(images = 2, cellPlaceholders = 2, backdrops = 2, cellWidth = RollaDimens.featureCardSize / 2)
        assertEveryCollageImageSitsOnBackdrop()

        uris = listOf("a", "b", "c", "d", "e")
        assertArt(images = 4, cellPlaceholders = 0, backdrops = 4, cellWidth = RollaDimens.featureCardSize / 2)
        assertEveryCollageImageSitsOnBackdrop()
    }

    /**
     * Device defect D1: a collage image that fails to load (a MediaStore albumart URI with no art) left its cell
     * transparent and exposed a fragment of the card's large glyph. Every collage image must sit on its own backdrop
     * covering exactly the image's bounds, drawn beneath it. The fake URIs here never load, which is the failed case
     * itself. Counts are asserted by [assertArt].
     */
    private fun assertEveryCollageImageSitsOnBackdrop() {
        val images = composeRule.onAllNodesWithTag(FEATURE_CARD_ART_IMAGE_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
        val backdrops = composeRule.onAllNodesWithTag(FEATURE_CARD_ART_IMAGE_BACKDROP_TAG, useUnmergedTree = true)
            .fetchSemanticsNodes()
        images.forEach { image ->
            val match = backdrops.firstOrNull { hasSameBounds(it.boundsInRoot, image.boundsInRoot) }
            val backdrop = requireNotNull(match) {
                "collage image at ${image.boundsInRoot} has no backdrop with equal bounds"
            }
            assertDrawnBeneath(backdrop, image)
        }
    }

    /**
     * Unmerged semantics children are listed in z (draw) order, so a node drawn beneath its sibling comes first.
     * Both tagged nodes are siblings under the art box (the cell Box and the Row/Column carry no semantics).
     */
    private fun assertDrawnBeneath(below: SemanticsNode, above: SemanticsNode) {
        val parent = requireNotNull(above.parent) { "collage image has no semantics parent" }
        assertEquals("backdrop and image must share a parent", parent.id, below.parent?.id)
        val order = parent.children.map { it.id }
        val belowIndex = order.indexOf(below.id)
        val aboveIndex = order.indexOf(above.id)
        assertTrue(
            "backdrop (child $belowIndex) must be drawn beneath its image (child $aboveIndex)",
            belowIndex in 0 until aboveIndex,
        )
    }

    private fun hasSameBounds(a: Rect, b: Rect): Boolean = with(composeRule.density) {
        listOf(a.left - b.left, a.top - b.top, a.right - b.right, a.bottom - b.bottom)
            .all { delta -> abs(delta.toDp().value) <= BOUNDS_TOLERANCE_DP }
    }

    private companion object {
        const val BOUNDS_TOLERANCE_DP = 0.5f
    }

    private fun assertArt(images: Int, cellPlaceholders: Int, backdrops: Int, cellWidth: Dp? = null) {
        val imageNodes = composeRule.onAllNodesWithTag(FEATURE_CARD_ART_IMAGE_TAG, useUnmergedTree = true)
        val placeholderNodes =
            composeRule.onAllNodesWithTag(FEATURE_CARD_ART_CELL_PLACEHOLDER_TAG, useUnmergedTree = true)
        imageNodes.assertCountEquals(images)
        placeholderNodes.assertCountEquals(cellPlaceholders)
        composeRule.onAllNodesWithTag(FEATURE_CARD_ART_IMAGE_BACKDROP_TAG, useUnmergedTree = true)
            .assertCountEquals(backdrops)
        if (cellWidth == null) return
        val cells = imageNodes.fetchSemanticsNodes() + placeholderNodes.fetchSemanticsNodes()
        // ±1 dp absorbs one px of weight rounding at mdpi (143 px splits 71/72) and survives a fractional
        // featureCardSize after Phase 7 calibration; it still separates full (143), half (71.5) and third (47.7).
        cells.forEach { node ->
            with(composeRule.density) { node.boundsInRoot.width.toDp() }
                .assertIsEqualTo(cellWidth, "art cell width", tolerance = 1.dp)
        }
    }
}
