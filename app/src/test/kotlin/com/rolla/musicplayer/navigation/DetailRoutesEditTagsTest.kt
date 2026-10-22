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
import com.rolla.musicplayer.searchDestination
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

    /**
     * Phase 6b ruling 10: Search's result ⋮ → "Edit tags" pushes TagEditor for that track, over Search. Trivial stub:
     * onEditTagsClick = {} leaves Search current, failing "Edit tags pushes the tag editor". Mutation: pass
     * onEditTagsClick = {} in searchDestination → RED.
     */
    @Test
    fun searchsEditTagsOpensTheTagEditorForThatTrack() {
        val songs = DetailRouteFixtures.songs(SONG_COUNT)
        val viewModel = DetailRouteFixtures.searchViewModel(songs)
        setGraph { searchDestination(navController) { viewModel } }
        open(Search())
        // The results arrive after SearchViewModel's debounce.
        DetailRouteFixtures.settleSearchDebounce()
        composeRule.waitForIdle()
        assertEditTagsPushesTheTagEditor<Search>(songs[1])
    }

    /**
     * 6a T8 M3: openTagEditor's launchSingleTop. A second call while the editor is on top (a double tap of "Edit tags")
     * keeps one TagEditor entry. Trivial stub: openTagEditor as a no-op fails the control (TagEditor is current after
     * the first call). Mutation: remove launchSingleTop = true → 2 entries → RED.
     */
    @Test
    fun editTagsTwiceKeepsOneTagEditor() {
        setGraph {}
        composeRule.runOnIdle { navController.openTagEditor(TWICE_SONG_ID) }
        composeRule.runOnIdle {
            assertTrue(
                "control: the first call opens the tag editor",
                navController.currentBackStackEntry?.destination?.hasRoute<TagEditor>() == true,
            )
            assertEquals(
                "control: the first call opens the tag editor for that track",
                TWICE_SONG_ID,
                navController.currentBackStackEntry?.toRoute<TagEditor>()?.songId,
            )
        }
        composeRule.runOnIdle { navController.openTagEditor(TWICE_SONG_ID) }
        composeRule.runOnIdle {
            val tagEditors = navController.currentBackStack.value.count { entry ->
                entry.destination.hasRoute<TagEditor>()
            }
            assertEquals("a second Edit tags keeps one tag editor on the back stack", 1, tagEditors)
        }
    }

    /**
     * T5 M2: openTagEditor(song) with a malformed (non-numeric) id is a no-op, never a crash and never a push. Control:
     * a numeric id pushes TagEditor for it; back pops to Home. Trivial stub: openTagEditor(song) as a no-op fails the
     * control. Mutation: `toLongOrNull() ?: 0L` (pushes TagEditor(0)) → RED on the last assertion.
     */
    @Test
    fun aMalformedSongIdLeavesTheCurrentDestination() {
        setGraph {}
        composeRule.runOnIdle { navController.openTagEditor(songWithId(NUMERIC_SONG_ID.toString())) }
        composeRule.runOnIdle {
            assertEquals(
                "control: a numeric id pushes the tag editor for that track",
                NUMERIC_SONG_ID,
                navController.currentBackStackEntry
                    ?.takeIf { it.destination.hasRoute<TagEditor>() }
                    ?.toRoute<TagEditor>()?.songId,
            )
            navController.popBackStack()
        }
        composeRule.runOnIdle {
            assertTrue("back returns to Home", navController.currentDestination?.hasRoute<Home>() == true)
            navController.openTagEditor(songWithId(MALFORMED_SONG_ID))
        }
        composeRule.runOnIdle {
            assertTrue(
                "a malformed song id leaves Home the current destination (no tag editor is pushed)",
                navController.currentDestination?.hasRoute<Home>() == true,
            )
        }
    }

    private fun songWithId(id: String): Song = DetailRouteFixtures.songs(1).single().copy(id = id)

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
        const val TWICE_SONG_ID = 5L
        const val NUMERIC_SONG_ID = 7L
        const val MALFORMED_SONG_ID = "x"
    }
}
