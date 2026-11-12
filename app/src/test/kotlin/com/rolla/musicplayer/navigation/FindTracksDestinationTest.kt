package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.core.data.repository.CustomTagRepository
import com.rolla.musicplayer.core.data.repository.SongFinder
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.media.PlaybackController
import com.rolla.musicplayer.core.model.CustomTag
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.SongFilter
import com.rolla.musicplayer.core.testing.FakePlaylistRepository
import com.rolla.musicplayer.core.testing.FakeSongRepository
import com.rolla.musicplayer.core.ui.rememberSongSelectionState
import com.rolla.musicplayer.feature.library.ScanState
import com.rolla.musicplayer.feature.library.TracksTab
import com.rolla.musicplayer.feature.library.TracksViewModel
import com.rolla.musicplayer.feature.search.findtracks.FindTracksViewModel
import com.rolla.musicplayer.findTracksDestination
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Custom tags phase 4, Task 6: the Tracks tab's Search button opens Find tracks. The test hosts the real TracksTab
 * (built from fakes) as Home, wired through [openFindTracks] as MainActivity's Home destination wires it, and the real
 * [findTracksDestination] with a ViewModel built from fakes; Search is in the graph too, so a helper that went there
 * would navigate rather than throw. Tapping "Find tracks" must land on the FindTracks route and show its screen, and
 * Navigate up must return to Home.
 *
 * Trivial stub: openFindTracks navigates nowhere → the route stays Home → RED. Mutation (test-control): navigate to
 * Search() → the route is Search → RED.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FindTracksDestinationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    // LongMethod: the three-destination host, the push and the way back, in one composition.
    @Suppress("LongMethod")
    @Test
    fun tracksHeaderButton_opensFindTracks() {
        val tracksViewModel = TracksViewModel(
            songRepository = FakeSongRepository(),
            playbackController = playbackController(),
            playlistRepository = FakePlaylistRepository(),
        )
        val findTracksViewModel = findTracksViewModel()
        composeRule.setContent {
            navController = rememberNavController()
            RollaMusicPlayerTheme(darkTheme = true) {
                NavHost(navController = navController, startDestination = Home, modifier = Modifier.fillMaxSize()) {
                    composable<Home> {
                        TracksTab(
                            selection = rememberSongSelectionState(),
                            scanState = ScanState.Done(added = 0, removed = 0),
                            onEditTagsClick = {},
                            onFindTracksClick = { navController.openFindTracks() },
                            viewModel = tracksViewModel,
                        )
                    }
                    composable<Search> {}
                    composable<TagEditor> {}
                    findTracksDestination(navController) { findTracksViewModel }
                }
            }
        }
        assertTrue("control: the test starts on Home", currentRouteIs<Home>())

        composeRule.onNodeWithContentDescription("Find tracks").performClick()
        composeRule.waitForIdle()

        assertTrue(
            "the Tracks button opens the FindTracks route (now ${navController.currentDestination?.route})",
            currentRouteIs<FindTracks>(),
        )
        composeRule.onNodeWithText("Find tracks").assertExists("Find tracks' top bar is on screen")
        assertTrue(
            "a plain push: Home stays under Find tracks",
            composeRule.runOnIdle { navController.previousBackStackEntry?.destination?.hasRoute<Home>() == true },
        )

        composeRule.onNodeWithContentDescription("Navigate up").performClick()
        composeRule.waitForIdle()
        assertTrue("Navigate up returns to Home", currentRouteIs<Home>())
    }

    private inline fun <reified T : Any> currentRouteIs(): Boolean =
        composeRule.runOnIdle { navController.currentBackStackEntry?.destination?.hasRoute<T>() == true }

    private fun findTracksViewModel(): FindTracksViewModel {
        val customTags = mockk<CustomTagRepository>(relaxed = true)
        every { customTags.observeCustomTags(any()) } returns flowOf(emptyList<CustomTag>())
        return FindTracksViewModel(
            savedStateHandle = SavedStateHandle(),
            songFinder = object : SongFinder {
                override fun observeMatches(filter: Flow<SongFilter>): Flow<List<Song>> =
                    filter.map { emptyList() }
            },
            customTagRepository = customTags,
            playbackController = playbackController(),
            playlistRepository = FakePlaylistRepository(),
        )
    }

    private fun playbackController(): PlaybackController = mockk(relaxed = true)
}
