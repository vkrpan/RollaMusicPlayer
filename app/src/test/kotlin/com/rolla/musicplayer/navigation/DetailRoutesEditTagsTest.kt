package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.albumDetailDestination
import com.rolla.musicplayer.artistDetailDestination
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.playlistDetailDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Phase 6a ruling 6 at the graph level (Task 8): a detail row's ⋮ → "Edit tags" pushes TagEditor for that track, the
 * FolderDetail shape. Each test hosts the real destination function from MainActivity.kt, opens it through its typed
 * route and taps Edit tags on the second track (its id is 102, not an index).
 *
 * Each test's control: the detail route is the current destination before the tap, and stays there under the trivial
 * stub (onEditTagsClick = {}), which fails the "pushes the tag editor" assertion. Mutation run for each test: pass
 * onEditTagsClick = {} at that destination's route call → no TagEditor → RED.
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h640dp", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailRoutesEditTagsTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var navController: NavHostController

    @Test
    fun albumDetailsEditTagsOpensTheTagEditorForThatTrack() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.albumViewModel(songs)
        setGraph { albumDetailDestination(navController) { viewModel } }
        open(AlbumDetail(albumId = DetailRouteFixtures.ALBUM_ID))
        assertEditTagsPushesTheTagEditor<AlbumDetail>(songs[1])
    }

    @Test
    fun artistDetailsEditTagsOpensTheTagEditorForThatTrack() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.artistViewModel(songs)
        setGraph { artistDetailDestination(navController) { viewModel } }
        open(ArtistDetail(artistName = DetailRouteFixtures.ARTIST_NAME))
        assertEditTagsPushesTheTagEditor<ArtistDetail>(songs[1])
    }

    @Test
    fun aUserPlaylistsEditTagsOpensTheTagEditorForThatTrack() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.playlistViewModel(songs)
        setGraph { playlistDetailDestinations(navController) { viewModel } }
        open(PlaylistDetail(playlistId = DetailRouteFixtures.PLAYLIST_ID))
        assertEditTagsPushesTheTagEditor<PlaylistDetail>(songs[1])
    }

    @Test
    fun aSmartPlaylistsEditTagsOpensTheTagEditorForThatTrack() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.smartPlaylistViewModel(songs)
        setGraph { playlistDetailDestinations(navController) { viewModel } }
        open(SmartPlaylist(kind = DetailRouteFixtures.SMART_KIND))
        assertEditTagsPushesTheTagEditor<SmartPlaylist>(songs[1])
    }

    private fun setGraph(destinations: NavGraphBuilder.() -> Unit) {
        composeRule.setContent {
            navController = rememberNavController()
            DetailRouteTestHost(navController = navController, miniPlayerInset = 0.dp, destinations = destinations)
        }
    }

    private fun open(route: Any) {
        composeRule.runOnIdle { navController.navigate(route) }
        composeRule.waitForIdle()
    }

    private inline fun <reified T : Any> assertEditTagsPushesTheTagEditor(song: Song) {
        composeRule.runOnIdle {
            assertTrue(
                "the detail route is the current destination before the tap",
                navController.currentDestination?.hasRoute<T>() == true,
            )
        }
        composeRule.onNodeWithContentDescription("More options for ${song.title}").performClick()
        composeRule.onNodeWithText("Edit tags").performClick()
        composeRule.runOnIdle {
            val entry = navController.currentBackStackEntry
            assertTrue("Edit tags pushes the tag editor", entry?.destination?.hasRoute<TagEditor>() == true)
            assertEquals("the tag editor opens the tapped track", song.id.toLong(), entry?.toRoute<TagEditor>()?.songId)
            // A plain push (no popUpTo), so back from the editor returns to the detail screen.
            assertTrue(
                "the detail screen is under the tag editor",
                navController.previousBackStackEntry?.destination?.hasRoute<T>() == true,
            )
        }
    }

    private companion object {
        const val SONG_COUNT = 3
    }
}
