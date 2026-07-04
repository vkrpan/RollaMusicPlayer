package com.rolla.musicplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.feature.library.LibraryRoute
import com.rolla.musicplayer.feature.player.MiniPlayerRoute
import com.rolla.musicplayer.feature.player.MiniPlayerViewModel
import com.rolla.musicplayer.feature.player.NowPlayingRoute
import com.rolla.musicplayer.feature.playlists.PlaylistDetailRoute
import com.rolla.musicplayer.feature.playlists.PlaylistsRoute
import com.rolla.musicplayer.navigation.Library
import com.rolla.musicplayer.navigation.NowPlaying
import com.rolla.musicplayer.navigation.PlaylistDetail
import com.rolla.musicplayer.navigation.Playlists
import com.rolla.musicplayer.navigation.SmartPlaylist
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RollaMusicPlayerTheme {
                RollaNavHost()
            }
        }
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RollaNavHost() {
    val navController = rememberNavController()
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val showMiniPlayer by miniPlayerViewModel.hasSong.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isNowPlaying = currentDestination?.hasRoute(NowPlaying::class) == true
    val isTopLevelDestination = currentDestination?.hasRoute(Library::class) == true ||
        currentDestination?.hasRoute(Playlists::class) == true

    SharedTransitionLayout {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                Column {
                    AnimatedVisibility(
                        visible = showMiniPlayer && !isNowPlaying,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        MiniPlayerRoute(
                            sharedTransitionScope = this@SharedTransitionLayout,
                            animatedVisibilityScope = this,
                            viewModel = miniPlayerViewModel,
                            onBodyClick = {
                                navController.navigate(NowPlaying) {
                                    launchSingleTop = true
                                }
                            },
                        )
                    }
                    if (isTopLevelDestination) {
                        RollaBottomNavigationBar(
                            navController = navController,
                            currentDestination = currentDestination,
                        )
                    }
                }
            },
        ) { innerPadding ->
            AppNavGraph(
                navController = navController,
                sharedTransitionScope = this@SharedTransitionLayout,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun RollaBottomNavigationBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
) {
    NavigationBar {
        NavigationBarItem(
            selected = currentDestination?.hasRoute(Library::class) == true,
            onClick = {
                navController.navigate(Library) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(imageVector = Icons.Filled.LibraryMusic, contentDescription = null) },
            label = { Text("Library") },
        )
        NavigationBarItem(
            selected = currentDestination?.hasRoute(Playlists::class) == true,
            onClick = {
                navController.navigate(Playlists) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            icon = { Icon(imageVector = Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
            label = { Text("Playlists") },
        )
    }
}

@Suppress("LongMethod")
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Library,
        modifier = modifier,
    ) {
        composable<Library> {
            LibraryRoute()
        }
        composable<NowPlaying> {
            NowPlayingRoute(
                onNavigateUp = { navController.navigateUp() },
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this,
            )
        }
        composable<Playlists> {
            PlaylistsRoute(
                onPlaylistClick = { id -> navController.navigate(PlaylistDetail(playlistId = id)) },
                onSmartPlaylistClick = { kind -> navController.navigate(SmartPlaylist(kind = kind.name)) },
            )
        }
        composable<PlaylistDetail> {
            PlaylistDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
        composable<SmartPlaylist> {
            PlaylistDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
}
