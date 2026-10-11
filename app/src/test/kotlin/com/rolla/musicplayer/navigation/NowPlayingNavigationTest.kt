package com.rolla.musicplayer.navigation

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.createGraph
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rolla.musicplayer.appDestinations
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

// Drives the production graph (MainActivity's appDestinations) on a real NavHostController. No NavHost composes it,
// so no destination composes and no Hilt graph is needed. The plain Application keeps Robolectric from booting
// RollaApp.
@RunWith(AndroidJUnit4::class)
@Config(application = Application::class)
class NowPlayingNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    // Now Playing → ⋮ Go to album → tap the pill: exactly one Now Playing, and Album detail is gone.
    // Mutation: openNowPlaying() is the plain navigate(NowPlaying) { launchSingleTop = true } again → the back stack
    // is [Home, NowPlaying, AlbumDetail, NowPlaying] → RED.
    @Test
    fun pillReturnsToTheNowPlayingAlreadyOnTheBackStack() {
        val navController = realGraphNavController()
        composeRule.runOnUiThread {
            navController.navigate(NowPlaying)
            navController.navigate(AlbumDetail(albumId = 7L))
            navController.openNowPlaying()
        }
        assertEquals(listOf("Home", "NowPlaying"), backStackRoutes(navController))
    }

    // Mutation: drop the navigate fallback (pop only) → the tap from Home does nothing → [Home] → RED.
    @Test
    fun pillPushesNowPlayingWhenNoneIsOnTheBackStack() {
        val navController = realGraphNavController()
        composeRule.runOnUiThread {
            navController.navigate(ArtistDetail(artistName = "Artist"))
            navController.openNowPlaying()
        }
        assertEquals(listOf("Home", "ArtistDetail", "NowPlaying"), backStackRoutes(navController))
    }

    @OptIn(ExperimentalSharedTransitionApi::class)
    private fun realGraphNavController(): NavHostController {
        lateinit var navController: NavHostController
        lateinit var graph: NavGraph
        composeRule.setContent {
            navController = rememberNavController()
            SharedTransitionLayout {
                val sharedTransitionScope = this
                // Built directly (no remember): this host composes once (see RouteClassTest).
                graph = navController.createGraph(startDestination = Home) {
                    appDestinations(
                        navController = navController,
                        sharedTransitionScope = sharedTransitionScope,
                        nowPlayingPresence = NowPlayingPresence(),
                    )
                }
            }
        }
        composeRule.waitForIdle()
        // Set outside composition, so a recomposition could never reset the back stack mid-test.
        composeRule.runOnUiThread { navController.graph = graph }
        return navController
    }

    /** The back stack's destinations as route-class simple names, the graph root excluded. */
    private fun backStackRoutes(navController: NavHostController): List<String?> =
        navController.currentBackStack.value
            .map { it.destination }
            .filter { destination -> allRoutes.any { destination.hasRoute(it) } }
            .map { it.routeClass()?.simpleName }
}
