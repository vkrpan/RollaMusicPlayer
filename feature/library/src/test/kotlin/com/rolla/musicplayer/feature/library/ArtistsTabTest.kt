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
import com.rolla.musicplayer.core.model.Artist
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
class ArtistsTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Review Focus 1. The shared id 7 simulates an FNV hash collision. Mutations: key = { it.id } or
    // key = { it.name.lowercase() } → duplicate key → crash → RED.
    @Test
    fun `artists differing only in case both show and open their own names`() {
        val opened = mutableListOf<String>()
        setContent(
            artists = listOf(artist(7L, "Queen"), artist(7L, "queen")),
            onArtistClick = { opened += it },
        )
        composeRule.onNodeWithText("queen").performClick()
        composeRule.onNodeWithText("Queen").performClick()
        assertEquals(listOf("queen", "Queen"), opened)
    }

    // Review Focus 4. Mutation: treat null as empty → RED.
    @Test
    fun `nothing renders before the artists load`() {
        setContent(artists = null)
        composeRule.onNodeWithText("No artists found").assertDoesNotExist()
        composeRule.onNodeWithTag(ARTIST_LIST_TEST_TAG).assertDoesNotExist()
    }

    // Mutation: drop the Scanning branch in ArtistsTabContent → the empty message shows mid-scan → RED.
    @Test
    fun `scanning shows progress over an empty list`() {
        setContent(artists = emptyList(), scanState = ScanState.Scanning)
        composeRule.onNodeWithContentDescription("Scanning library").assertExists()
        composeRule.onNodeWithText("No artists found").assertDoesNotExist()
    }

    @Test
    fun `an empty library says no artists found`() {
        setContent(artists = emptyList(), scanState = ScanState.Done(0, 0))
        composeRule.onNodeWithText("No artists found").assertIsDisplayed()
    }

    // Mutation: drop the bottom contentPadding → RED (same probe as the albums test, on the last row).
    @Test
    fun `the last row scrolls fully above the mini-player`() {
        val artists = List(20) { artist(it.toLong(), "Artist $it") }
        setContent(artists = artists, miniPlayerInset = 60.dp)
        composeRule.onNodeWithTag(ARTIST_LIST_TEST_TAG).performScrollToIndex(artists.lastIndex)
        val last = composeRule.onNodeWithText("Artist 19").getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertTrue(last.bottom <= root.bottom - 60.dp)
    }

    private fun setContent(
        artists: List<Artist>?,
        scanState: ScanState = ScanState.Done(1, 0),
        onArtistClick: (String) -> Unit = {},
        miniPlayerInset: Dp = 0.dp,
    ) {
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                CompositionLocalProvider(LocalMiniPlayerInset provides miniPlayerInset) {
                    ArtistsTabContent(artists = artists, scanState = scanState, onArtistClick = onArtistClick)
                }
            }
        }
    }

    private companion object {
        fun artist(id: Long, name: String) = Artist(id = id, name = name, albumCount = 1, songCount = 1)
    }
}
