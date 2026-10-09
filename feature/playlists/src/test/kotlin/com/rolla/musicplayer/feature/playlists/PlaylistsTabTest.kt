package com.rolla.musicplayer.feature.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
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
import org.robolectric.annotation.LooperMode

// NATIVE graphics: the card-order test compares the cards' left edges, and the cards' bounds come from their text
// (LEGACY measures every text line at ~36 dp and ~1 px per char). Nothing here captures pixels.
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistsTabTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    // Snapshot state, so a test can flip the hoisted flag after setContent and the tab recomposes.
    private var showCreateDialog by mutableStateOf(false)

    @Test
    fun featureCardsShowInSpecOrderWithTrackCounts() {
        setTab()

        // The merged, clickable card nodes (each card's label and count merge into it). At 360 dp only the first
        // three cards are composed, so the chain is checked in two overlapping steps around a scroll.
        val recentlyAdded = cardLeft("Recently added")
        val mostPlayed = cardLeft("Most played")
        val recentlyPlayedBeforeScroll = cardLeft("Recently played")
        recentlyAdded.assertIsEqualTo(RollaDimens.featureCardRowStart, "first card left", tolerance = TOLERANCE)
        assertTrue("Most played must follow Recently added", mostPlayed > recentlyAdded)
        (mostPlayed - recentlyAdded).assertIsEqualTo(
            RollaDimens.featureCardSize + RollaDimens.featureCardGap,
            "card pitch",
            tolerance = TOLERANCE,
        )
        assertTrue("Recently played must follow Most played", recentlyPlayedBeforeScroll > mostPlayed)

        composeRule.onNodeWithTag(FEATURE_CARD_ROW_TEST_TAG).performScrollToIndex(SUMMARY_COUNT - 1)
        assertTrue(
            "Favourite tracks must follow Recently played",
            cardLeft("Favourite tracks") > cardLeft("Recently played"),
        )
        composeRule.onNodeWithText("4 tracks").assertExists()
    }

    @Test
    fun featureCardCountIsSingularForOneTrack() {
        setTab()

        composeRule.onNodeWithText("1 track").assertExists()
        composeRule.onNodeWithText("2 tracks").assertExists()
    }

    @Test
    fun tappingAFeatureCardReportsItsKind() {
        setTab()

        composeRule.onNodeWithText("Most played").performClick()

        assertEquals(listOf("smart:MOST_PLAYED"), events)
    }

    @Test
    fun emptyUserListShowsTheCreateHint() {
        setTab(userPlaylists = emptyList())

        composeRule.onNodeWithText("No playlists yet. Tap + to create one.").assertIsDisplayed()
    }

    // LEGACY looper for this test only: under the default PAUSED looper, Robolectric never idles while a Dialog with
    // usePlatformDefaultWidth (the default) hosts a text field — AppNotIdleException. Isolated with probes: the same
    // dialog idles under LEGACY, or under PAUSED with usePlatformDefaultWidth = false; focus and the clock are not it.
    @Suppress("DEPRECATION")
    @LooperMode(LooperMode.Mode.LEGACY)
    @Test
    fun createDialogFollowsTheHoistedFlag() {
        setTab(showCreateDialog = false)
        composeRule.onNodeWithText("New playlist").assertDoesNotExist()

        showCreateDialog = true
        composeRule.onNodeWithText("New playlist").assertIsDisplayed()

        composeRule.onNode(hasSetTextAction()).performTextInput("Road")
        composeRule.onNodeWithText("Create").performClick()

        assertEquals("Create must report the name, then dismiss", listOf("create:Road", "dismiss"), events)
    }

    @Test
    fun userPlaylistTapReportsItsId() {
        setTab(userPlaylists = listOf(Playlist(id = 7L, name = "Light", songCount = 3, createdAt = 0L, updatedAt = 0L)))

        composeRule.onNodeWithText("No playlists yet. Tap + to create one.").assertDoesNotExist()
        composeRule.onNodeWithText("Light").performClick()

        assertEquals(listOf("playlist:7"), events)
    }

    private fun setTab(userPlaylists: List<Playlist> = emptyList(), showCreateDialog: Boolean = false) {
        this.showCreateDialog = showCreateDialog
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PlaylistsTabContent(
                    smartPlaylists = summaries,
                    userPlaylists = userPlaylists,
                    showCreateDialog = this.showCreateDialog,
                    onDismissCreateDialog = {
                        events += "dismiss"
                        this.showCreateDialog = false
                    },
                    onCreatePlaylist = { name -> events += "create:$name" },
                    onPlaylistClick = { id -> events += "playlist:$id" },
                    onSmartPlaylistClick = { kind -> events += "smart:${kind.name}" },
                )
            }
        }
    }

    private fun cardLeft(label: String): Dp = composeRule.onNodeWithText(label).getBoundsInRoot().left

    private companion object {
        const val SUMMARY_COUNT = 4
        val TOLERANCE = 0.5.dp

        // Counts 1..4 in spec order; empty art so the cards render placeholders and Coil never loads.
        val summaries = listOf(
            SmartPlaylistKind.RECENTLY_ADDED to "Recently added",
            SmartPlaylistKind.MOST_PLAYED to "Most played",
            SmartPlaylistKind.RECENTLY_PLAYED to "Recently played",
            SmartPlaylistKind.FAVOURITES to "Favourite tracks",
        ).mapIndexed { index, (kind, label) ->
            SmartPlaylistSummary(kind = kind, label = label, count = index + 1, previewArtworkUris = emptyList())
        }
    }
}
