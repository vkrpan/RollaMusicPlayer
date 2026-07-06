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
import com.rolla.musicplayer.feature.equalizer.EqualizerRoute
import com.rolla.musicplayer.feature.library.LibraryRoute
import com.rolla.musicplayer.feature.player.MiniPlayerRoute
import com.rolla.musicplayer.feature.player.MiniPlayerViewModel
import com.rolla.musicplayer.feature.player.NowPlayingRoute
import com.rolla.musicplayer.feature.playlists.PlaylistDetailRoute
import com.rolla.musicplayer.feature.playlists.PlaylistsRoute
import com.rolla.musicplayer.feature.tageditor.BatchTagEditorRoute
import com.rolla.musicplayer.feature.tageditor.TagEditorRoute
import com.rolla.musicplayer.navigation.BatchTagEditor
import com.rolla.musicplayer.navigation.Equalizer
import com.rolla.musicplayer.navigation.Library
import com.rolla.musicplayer.navigation.NowPlaying
import com.rolla.musicplayer.navigation.PlaylistDetail
import com.rolla.musicplayer.navigation.Playlists
import com.rolla.musicplayer.navigation.SmartPlaylist
import com.rolla.musicplayer.navigation.TagEditor
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
            LibraryRoute(
                onEditTagsClick = { song ->
                    // Song.id is the MediaStore row id, a numeric String by construction, so
                    // toLongOrNull() should never actually return null here -- this is defensive
                    // per navigation-conventions.md (never crash on a malformed id), not a path
                    // expected to trigger. A null id silently no-ops rather than crashing.
                    val songId = song.id.toLongOrNull()
                    if (songId != null) {
                        navController.navigate(TagEditor(songId = songId)) {
                            launchSingleTop = true
                        }
                    }
                },
                onEditTagsForSelection = { songs ->
                    // Same defensive toLongOrNull() reasoning as onEditTagsClick above, applied
                    // per-song via mapNotNull; a song whose id doesn't parse is dropped from the
                    // batch rather than crashing. If every id in the selection fails to parse,
                    // don't navigate at all -- an empty-list BatchTagEditor has nothing to edit.
                    val songIds = songs.mapNotNull { it.id.toLongOrNull() }
                    if (songIds.isNotEmpty()) {
                        navController.navigate(BatchTagEditor(songIds = songIds)) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }
        composable<NowPlaying> {
            NowPlayingRoute(
                onNavigateUp = { navController.navigateUp() },
                onEqualizerClick = {
                    navController.navigate(Equalizer) {
                        launchSingleTop = true
                    }
                },
                sharedTransitionScope = sharedTransitionScope,
                animatedContentScope = this,
            )
        }
        // Equalizer is a leaf pushed on top of whichever screen opened it (today: Now Playing;
        // later: Settings too). Back = navigateUp() only — pops Equalizer and returns to the
        // caller. No popUpTo here: that would hard-code "always return to X" and break the
        // future Settings entry point. launchSingleTop above guards double-taps of the icon.
        composable<Equalizer> {
            EqualizerRoute(onNavigateUp = { navController.navigateUp() })
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
        // TagEditor is a leaf editor pushed on top of whichever screen opened it (today: Library;
        // later: possibly Now Playing or Search). Back arrow / Cancel / system back and a
        // successful save all resolve through the same onNavigateUp() -- navigateUp() only, no
        // popUpTo here, so it always pops back to its actual caller rather than a hard-coded
        // destination. launchSingleTop above guards double-taps of the "Edit tags" option.
        composable<TagEditor> {
            TagEditorRoute(onNavigateUp = { navController.navigateUp() })
        }
        // BatchTagEditor is a leaf editor pushed on top of Library (its only entry point today,
        // reached via the multi-select "Edit tags" action). Same back-behavior shape as TagEditor
        // above: Cancel / back arrow / system back / a fully-succeeded save all resolve through
        // this one onNavigateUp() -- navigateUp() only, no popUpTo -- so it always pops back to
        // Library rather than a hard-coded destination. LibraryScreen already clears the
        // multi-select set itself before this navigate() call fires, so Library is never
        // re-entered still in selection mode. launchSingleTop above guards double-taps of the
        // selection toolbar's "Edit tags" action.
        composable<BatchTagEditor> {
            BatchTagEditorRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
}
