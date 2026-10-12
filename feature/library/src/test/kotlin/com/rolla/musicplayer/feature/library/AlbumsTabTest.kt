package com.rolla.musicplayer.feature.library

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.LocalMiniPlayerInset
import com.rolla.musicplayer.core.designsystem.theme.RollaDimens
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Album
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// NATIVE graphics: the card height depends on its two text lines, which LEGACY mode measures at ~36 dp each.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AlbumsTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Mutation: GridCells.Fixed(1) → the second card sits below the first → RED.
    // Mutation: drop horizontalArrangement → the gap is 0 → RED.
    // Mutation: drop the start contentPadding → the first card starts at 0 → RED.
    // Mutation: drop verticalArrangement → the second row touches the first → RED.
    // Mutation: drop the end contentPadding → the second column reaches the root's right edge → RED.
    @Test
    fun `albums lay out in two columns with the card gap`() {
        setContent(albums = listOf(album(1L, "A"), album(2L, "B"), album(3L, "C")))
        val first = parentCardBounds("A")
        val second = parentCardBounds("B")
        val third = parentCardBounds("C")
        val root = composeRule.onRoot().getBoundsInRoot()
        assertEquals(first.top.value, second.top.value, 0.5f)
        assertEquals(RollaDimens.featureCardRowStart.value, first.left.value, 0.5f)
        assertEquals(RollaDimens.featureCardGap.value, (second.left - first.right).value, 0.5f)
        assertEquals(RollaDimens.featureCardGap.value, (third.top - first.bottom).value, 0.5f)
        assertEquals((root.right - RollaDimens.featureCardRowStart).value, second.right.value, 0.5f)
    }

    // Review Focus 2. Mutation: key = { it.title } → duplicate key → IllegalArgumentException → RED.
    @Test
    fun `two albums with the same title both show and open their own ids`() {
        val opened = mutableListOf<Long>()
        setContent(
            albums = listOf(album(30L, "Greatest Hits"), album(40L, "Greatest Hits")),
            onAlbumClick = { opened += it },
        )
        val cards = composeRule.onAllNodesWithText("Greatest Hits")
        cards.assertCountEquals(2)
        cards[1].performClick()
        assertEquals(listOf(40L), opened)
    }

    // Review Focus 4. Mutation: treat null as empty → "No albums found" exists → RED.
    @Test
    fun `nothing renders before the albums load`() {
        setContent(albums = null)
        composeRule.onNodeWithText("No albums found").assertDoesNotExist()
        composeRule.onNodeWithTag(ALBUM_GRID_TEST_TAG).assertDoesNotExist()
    }

    // Mutation: show the empty text while scanning → RED (the scanning spinner must win, as on Tracks).
    @Test
    fun `scanning shows progress, an empty library shows the message`() {
        setContent(albums = emptyList(), scanState = ScanState.Scanning)
        composeRule.onNodeWithContentDescription("Scanning library").assertExists()
        composeRule.onNodeWithText("No albums found").assertDoesNotExist()
    }

    @Test
    fun `an empty library says no albums found`() {
        setContent(albums = emptyList(), scanState = ScanState.Done(0, 0))
        composeRule.onNodeWithText("No albums found").assertIsDisplayed()
    }

    // Spec §7.3. Mutation: drop the bottom contentPadding → the last card ends under the 60 dp pill → RED.
    @Test
    fun `the last card scrolls fully above the mini-player`() {
        val albums = List(20) { album(it.toLong(), "Album $it") }
        setContent(albums = albums, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(ALBUM_GRID_TEST_TAG).performScrollToIndex(albums.lastIndex)
        val last = parentCardBounds("Album 19")
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }

    /**
     * The bounds of the card whose title is [title]. AlbumCard merges its art, title and subtitle into one clickable
     * node, so the node carrying both the title and the click action is the whole card, art box included. Scroll the
     * card into view first: getBoundsInRoot clamps off-screen nodes.
     */
    private fun parentCardBounds(title: String): DpRect =
        composeRule.onNode(hasText(title) and hasClickAction()).getBoundsInRoot()

    private fun setContent(
        albums: List<Album>?,
        scanState: ScanState = ScanState.Done(1, 0),
        onAlbumClick: (Long) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    AlbumsTabContent(albums = albums, scanState = scanState, onAlbumClick = onAlbumClick)
                }
            }
        }
    }

    private companion object {
        // Each album gets a distinct artist, so a title lookup never matches another card's subtitle.
        fun album(id: Long, title: String) = Album(
            id = id,
            title = title,
            artist = "Artist of $title #$id",
            songCount = 1,
            artworkUri = "",
        )
    }
}
