package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.searchDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 6b rulings 3 and 9 at the graph level: Search pads its own lists by the pill (homeListBottomPadding), so
 * MainActivity's searchDestination must not pad it by LocalMiniPlayerInset as well. It is the one formerly wrapped
 * route where the pill shows, so the inset is not 0 there. The test hosts the real searchDestination in a NavHost
 * under a 120 dp pill, opens Search through its typed route with 30 result tracks, scrolls to the end and measures the
 * last row against the root: it ends 120 dp above the bottom, not 240 dp (the list's padding plus an outer wrapper's)
 * and not 0 dp (no padding at all). Bounds, not "is displayed": Robolectric's root here is 584 dp, where display
 * checks are blind. Robolectric reports no navigation-bar or IME inset, so the keyboard half of ruling 3 is a device
 * check (Task 8).
 *
 * Trivial stubs: a route that renders nothing fails "the results list is on screen"; no padding or double padding
 * fails the gap. Control: before the scroll, the last row is not composed, so the scroll really reaches the end.
 * Mutation run: wrap the SearchRoute call in Box(Modifier.padding(bottom = LocalMiniPlayerInset.current)) → gap 240 dp
 * → RED.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SearchRouteInsetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun theLastResultEndsExactlyOnePillAboveTheBottom() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        openSearchWithResults(songs)

        val resultsList = composeRule.onNode(verticalList)
        resultsList.assertExists("the results list is on screen")
        val lastTitle = songs.last().title
        assertTrue(
            "control: the last result's row is not composed before the scroll",
            composeRule.onAllNodesWithText(lastTitle).fetchSemanticsNodes().isEmpty(),
        )

        // The "Tracks" header is item 0, so the last track is item songs.size.
        resultsList.performScrollToIndex(songs.size)
        val lastRow = composeRule.onNodeWithText(lastTitle)
        lastRow.assertExists("the last result's row is in the tree after the scroll")
        val last = lastRow.getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(
            "gap below the last result scrolled to the end: the list's own pill padding only " +
                "(${PILL_INSET.value * 2} would be the list's plus an outer wrapper's, 0 no padding)",
            PILL_INSET.value,
            (root.bottom - last.bottom).value,
            TOLERANCE_DP,
        )
    }

    /** Hosts searchDestination under the pill, opens Search and lets the results through the debounce. */
    private fun openSearchWithResults(songs: List<Song>) {
        val viewModel = DetailRouteFixtures.searchViewModel(songs)
        composeRule.setContent {
            navController = rememberNavController()
            DetailRouteTestHost(navController = navController, miniPlayerInset = PILL_INSET) {
                searchDestination(navController) { viewModel }
            }
        }
        composeRule.runOnIdle { navController.navigate(Search()) }
        composeRule.waitForIdle()
        DetailRouteFixtures.settleSearchDebounce()
        composeRule.waitForIdle()
    }

    private companion object {
        // Enough 72 dp rows to overflow the 584 dp root, so the list really scrolls to its end.
        const val SONG_COUNT = 30
        const val TOLERANCE_DP = 1f
        val PILL_INSET = 120.dp

        val verticalList = hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    }
}
