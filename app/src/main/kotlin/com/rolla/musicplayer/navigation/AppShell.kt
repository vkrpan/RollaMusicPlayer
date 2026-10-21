package com.rolla.musicplayer.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * The app's navigation shell: the NavHost under the floating pill, in one SharedTransitionLayout so the pill's artwork
 * keeps its shared-element transition into Now Playing. The pill shows when a song is loaded, the route allows it
 * (spec §7.3) and Now Playing is not still the visible target (see [NowPlayingPresence]).
 *
 * Insets, each with a single owner: every screen draws edge to edge under the status bar and owns its top inset
 * through its own top bar; the NavHost pads the horizontal safe-drawing insets once, for every destination including
 * Home; MiniPlayerHost pads the pill above the navigation bar; Home and the detail screens (playlists, albums, artists
 * and folders) pad only their lists (they scroll under the pill), and PushedScreen pads every other destination except
 * Now Playing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun AppShell(
    navController: NavHostController,
    hasSong: Boolean,
    pill: @Composable (SharedTransitionScope, AnimatedVisibilityScope, Modifier) -> Unit,
    modifier: Modifier = Modifier,
    graph: NavGraphBuilder.(SharedTransitionScope, NowPlayingPresence) -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val nowPlayingPresence = remember { NowPlayingPresence() }
    val showPill = hasSong &&
        showsMiniPlayer(navBackStackEntry?.destination.routeClass()) &&
        !nowPlayingPresence.isTarget
    SharedTransitionLayout {
        val sharedTransitionScope = this
        MiniPlayerHost(
            showPill = showPill,
            modifier = modifier,
            pill = { measure -> pill(sharedTransitionScope, this, measure) },
        ) {
            NavHost(
                navController = navController,
                startDestination = Home,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            ) {
                graph(sharedTransitionScope, nowPlayingPresence)
            }
        }
    }
}
