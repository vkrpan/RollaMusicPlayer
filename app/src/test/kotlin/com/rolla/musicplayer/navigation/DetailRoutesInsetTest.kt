package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.albumDetailDestination
import com.rolla.musicplayer.artistDetailDestination
import com.rolla.musicplayer.playlistDetailDestinations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Review Focus 5 at the graph level (Phase 6a Task 8). The detail screens pad their own lists by the pill, so
 * MainActivity must not wrap their destinations in PushedScreen too. Each test hosts the real destination function from
 * MainActivity.kt (the graph calls the same function), opens it through its typed route, scrolls to the end and
 * measures the last row against the root: with a 60 dp pill it ends 60 dp above the bottom, not 120 dp (the list's
 * padding plus PushedScreen's). Bounds, not "is displayed": Robolectric's root here is 584 dp, where display checks are
 * blind.
 *
 * Each test's control: the track list exists and its last row is in the tree (a destination that renders nothing fails
 * it). Mutation run for each test: wrap that destination's route call in PushedScreen { … } again → the gap is 120 dp
 * → RED.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailRoutesInsetTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun albumDetailsLastTrackEndsOnePillAboveTheBottom() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.albumViewModel(songs)
        setGraph { albumDetailDestination(navController) { viewModel } }
        open(AlbumDetail(albumId = DetailRouteFixtures.ALBUM_ID))
        // The hero and the sort row are items 0 and 1.
        assertTheLastRowEndsOnePillAboveTheBottom(lastIndex = songs.size + 1, lastTitle = songs.last().title)
    }

    @Test
    fun artistDetailsLastTrackEndsOnePillAboveTheBottom() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.artistViewModel(songs)
        setGraph { artistDetailDestination(navController) { viewModel } }
        open(ArtistDetail(artistName = DetailRouteFixtures.ARTIST_NAME))
        // The hero and the sort row are items 0 and 1 (the fixture artist has no album rows, so no albums strip).
        assertTheLastRowEndsOnePillAboveTheBottom(lastIndex = songs.size + 1, lastTitle = songs.last().title)
    }

    @Test
    fun aUserPlaylistsLastTrackEndsOnePillAboveTheBottom() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.playlistViewModel(songs)
        setGraph { playlistDetailDestinations(navController) { viewModel } }
        open(PlaylistDetail(playlistId = DetailRouteFixtures.PLAYLIST_ID))
        // The sort header sits above the list, so the tracks are the list's only items.
        assertTheLastRowEndsOnePillAboveTheBottom(lastIndex = songs.lastIndex, lastTitle = songs.last().title)
    }

    @Test
    fun aSmartPlaylistsLastTrackEndsOnePillAboveTheBottom() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.smartPlaylistViewModel(songs)
        setGraph { playlistDetailDestinations(navController) { viewModel } }
        open(SmartPlaylist(kind = DetailRouteFixtures.SMART_KIND))
        assertTheLastRowEndsOnePillAboveTheBottom(lastIndex = songs.lastIndex, lastTitle = songs.last().title)
    }

    private fun setGraph(destinations: NavGraphBuilder.() -> Unit) {
        composeRule.setContent {
            navController = rememberNavController()
            DetailRouteTestHost(
                navController = navController,
                miniPlayerInset = PILL_INSET,
                destinations = destinations,
            )
        }
    }

    private fun open(route: Any) {
        composeRule.runOnIdle { navController.navigate(route) }
        composeRule.waitForIdle()
    }

    private fun assertTheLastRowEndsOnePillAboveTheBottom(lastIndex: Int, lastTitle: String) {
        val trackList = composeRule.onNode(verticalList)
        trackList.assertExists("the detail screen's track list is on screen")
        trackList.performScrollToIndex(lastIndex)
        val lastRow = composeRule.onNodeWithText(lastTitle)
        lastRow.assertExists("the last track's row is in the tree")
        val last = lastRow.getUnclippedBoundsInRoot()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        assertEquals(
            "gap below the last track scrolled to the end: the list's own pill padding only " +
                "(${PILL_INSET.value * 2} would be the list's plus PushedScreen's)",
            PILL_INSET.value,
            (root.bottom - last.bottom).value,
            TOLERANCE_DP,
        )
    }

    private companion object {
        // Enough 72 dp rows to overflow the 584 dp root, so the list really scrolls to its end.
        const val SONG_COUNT = 20
        const val TOLERANCE_DP = 0.5f
        val PILL_INSET = 60.dp

        // The track list, not ArtistDetail's horizontal albums strip.
        val verticalList = hasScrollToIndexAction() and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    }
}
