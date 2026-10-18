package com.rolla.musicplayer.feature.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEqualTo
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.designsystem.component.FAST_SCROLL_RAIL_TEST_TAG
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

    // Spec §10: the rail covers the user rows only, so the jump skips the feature-card row. Mutation: itemOffset = 0 →
    // "Alpha 19" at the top and "Mango 0" one row lower → RED. Control: "Jump to M" exists.
    @Test
    fun `the rail jumps over the feature cards to a playlist`() {
        setTab(userPlaylists = alphaMangoPlaylists())
        jumpTo("M")
        assertAtListTop(PLAYLIST_LIST_TEST_TAG, "Mango 0")
    }

    // Fix round 1, I1 (spec §10: user rows only). With the list at its top, the rail starts exactly at the feature-card
    // row's bottom, so the cards' end stays tappable and swipeable. Mutations: pass railTopPadding = 0.dp → the rail's
    // top is the list's top → RED; an over-inset (featureCardsHeightPx + 50) → 50 dp low → RED (fix round 2, R3).
    // Control: the rail exists (its bounds are read).
    @Test
    fun `the rail starts below the feature cards`() {
        setTab(userPlaylists = alphaMangoPlaylists())
        val cardsBottom = composeRule.onNodeWithTag(FEATURE_CARD_ROW_TEST_TAG).getUnclippedBoundsInRoot().bottom
        val railTop = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).getUnclippedBoundsInRoot().top
        assertEquals("The rail's top must be the feature cards' bottom", cardsBottom.value, railTop.value, 0.5f)
    }

    // Fix round 2, R2. A list restored past item 0 (pager, rotation) keeps the inset although the card row is not
    // composed to be measured again. Mutation: plain remember for the measured height → after the restore the rail's
    // top is the list's top → RED. Controls: the card row really left composition, before and after the restore.
    @Test
    fun `the rail keeps its inset when the list is restored past the feature cards`() {
        val tester = StateRestorationTester(composeRule)
        tester.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                PlaylistsTabContent(
                    smartPlaylists = summaries,
                    userPlaylists = alphaMangoPlaylists(),
                    showCreateDialog = false,
                    onDismissCreateDialog = {},
                    onCreatePlaylist = {},
                    onPlaylistClick = {},
                    onSmartPlaylistClick = {},
                )
            }
        }
        val cardsBottom = composeRule.onNodeWithTag(FEATURE_CARD_ROW_TEST_TAG).getUnclippedBoundsInRoot().bottom
        // Item 0 is the card row, items 1..40 the playlists.
        composeRule.onNodeWithTag(PLAYLIST_LIST_TEST_TAG).performScrollToIndex(40)
        composeRule.onNodeWithTag(FEATURE_CARD_ROW_TEST_TAG).assertDoesNotExist()

        tester.emulateSavedInstanceStateRestore()

        composeRule.onNodeWithTag(FEATURE_CARD_ROW_TEST_TAG).assertDoesNotExist()
        val railTop = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).getUnclippedBoundsInRoot().top
        assertTrue(
            "After the restore the rail's top ($railTop) must still be at or below the cards' bottom ($cardsBottom)",
            railTop.value >= cardsBottom.value - 0.5f,
        )
    }

    // No user playlists: only the cards and the hint, no rail. Mutation: build sections from the smart playlists'
    // labels → a rail appears → RED. Control (ruling P3): with the 40 user playlists the rail appears ("never render"
    // → RED there). The host is short (a landscape phone), so the cards and the hint alone overflow and the list can
    // scroll: at full height they fit, the container's canScroll gate hides any rail, and the mutant survived.
    @Test
    fun `no user playlists means no rail`() {
        var playlists by mutableStateOf(emptyList<Playlist>())
        setTab(userPlaylistsProvider = { playlists }, hostHeight = SHORT_HOST_HEIGHT)
        composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).assertDoesNotExist()
        playlists = alphaMangoPlaylists()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).assertExists()
    }

    private fun jumpTo(label: String) {
        val rail = composeRule.onNodeWithTag(FAST_SCROLL_RAIL_TEST_TAG).fetchSemanticsNode()
        val actions = rail.config[SemanticsActions.CustomActions]
        val matches = actions.filter { it.label == "Jump to $label" }
        assertEquals("One \"Jump to $label\" action among ${actions.map { it.label }}", 1, matches.size)
        composeRule.runOnIdle { matches.single().action() }
        composeRule.waitForIdle()
    }

    private fun assertAtListTop(listTag: String, rowText: String) {
        val listTop = composeRule.onNodeWithTag(listTag).getUnclippedBoundsInRoot().top
        val rowTop = composeRule.onNodeWithText(rowText).getUnclippedBoundsInRoot().top
        assertEquals(listTop.value, rowTop.value, 0.5f)
    }

    private fun setTab(
        userPlaylists: List<Playlist> = emptyList(),
        showCreateDialog: Boolean = false,
        // Read inside the composition, so a test can change the user playlists after setContent.
        userPlaylistsProvider: () -> List<Playlist> = { userPlaylists },
        hostHeight: Dp? = null,
    ) {
        this.showCreateDialog = showCreateDialog
        composeRule.setContent {
            RollaMusicPlayerTheme(darkTheme = true) {
                Box(if (hostHeight != null) Modifier.height(hostHeight) else Modifier) {
                    PlaylistsTabContent(
                        smartPlaylists = summaries,
                        userPlaylists = userPlaylistsProvider(),
                        showCreateDialog = this@PlaylistsTabTest.showCreateDialog,
                        onDismissCreateDialog = {
                            events += "dismiss"
                            this@PlaylistsTabTest.showCreateDialog = false
                        },
                        onCreatePlaylist = { name -> events += "create:$name" },
                        onPlaylistClick = { id -> events += "playlist:$id" },
                        onSmartPlaylistClick = { kind -> events += "smart:${kind.name}" },
                    )
                }
            }
        }
    }

    private fun cardLeft(label: String): Dp = composeRule.onNodeWithText(label).getBoundsInRoot().left

    private companion object {
        const val SUMMARY_COUNT = 4
        val TOLERANCE = 0.5.dp

        // Shorter than the sort header, the feature-card row and the empty hint together, so the empty list scrolls.
        val SHORT_HOST_HEIGHT = 240.dp

        // Counts 1..4 in spec order; empty art so the cards render placeholders and Coil never loads.
        val summaries = listOf(
            SmartPlaylistKind.RECENTLY_ADDED to "Recently added",
            SmartPlaylistKind.MOST_PLAYED to "Most played",
            SmartPlaylistKind.RECENTLY_PLAYED to "Recently played",
            SmartPlaylistKind.FAVOURITES to "Favourite tracks",
        ).mapIndexed { index, (kind, label) ->
            SmartPlaylistSummary(kind = kind, label = label, count = index + 1, previewArtworkUris = emptyList())
        }

        fun playlist(id: Long, name: String) = Playlist(id, name, 0, 0L, 0L)

        /** 20 "Alpha" playlists, then 20 "Mango" playlists, in library order. */
        fun alphaMangoPlaylists(): List<Playlist> =
            List(20) { playlist(it.toLong(), "Alpha $it") } + List(20) { playlist(100L + it, "Mango $it") }
    }
}
