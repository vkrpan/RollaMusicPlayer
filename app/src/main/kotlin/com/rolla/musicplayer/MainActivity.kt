package com.rolla.musicplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.pm.PackageInfoCompat
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
import androidx.navigation.navDeepLink
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.ThemeMode
import com.rolla.musicplayer.feature.equalizer.EqualizerRoute
import com.rolla.musicplayer.feature.library.AlbumDetailRoute
import com.rolla.musicplayer.feature.library.LibraryRoute
import com.rolla.musicplayer.feature.player.MiniPlayerRoute
import com.rolla.musicplayer.feature.player.MiniPlayerViewModel
import com.rolla.musicplayer.feature.player.NowPlayingRoute
import com.rolla.musicplayer.feature.playlists.PlaylistDetailRoute
import com.rolla.musicplayer.feature.playlists.PlaylistsRoute
import com.rolla.musicplayer.feature.search.SearchRoute
import com.rolla.musicplayer.feature.settings.AboutRoute
import com.rolla.musicplayer.feature.settings.AppBuildInfo
import com.rolla.musicplayer.feature.settings.LicensesRoute
import com.rolla.musicplayer.feature.settings.PrivacyRoute
import com.rolla.musicplayer.feature.settings.SettingsRoute
import com.rolla.musicplayer.feature.tageditor.BatchTagEditorRoute
import com.rolla.musicplayer.feature.tageditor.TagEditorRoute
import com.rolla.musicplayer.navigation.About
import com.rolla.musicplayer.navigation.AlbumDetail
import com.rolla.musicplayer.navigation.BatchTagEditor
import com.rolla.musicplayer.navigation.Equalizer
import com.rolla.musicplayer.navigation.Library
import com.rolla.musicplayer.navigation.Licenses
import com.rolla.musicplayer.navigation.NowPlaying
import com.rolla.musicplayer.navigation.PlaylistDetail
import com.rolla.musicplayer.navigation.Playlists
import com.rolla.musicplayer.navigation.Privacy
import com.rolla.musicplayer.navigation.Search
import com.rolla.musicplayer.navigation.Settings
import com.rolla.musicplayer.navigation.SmartPlaylist
import com.rolla.musicplayer.navigation.TagEditor
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            RollaAppRoot(activity = this)
        }
    }
}

/**
 * App root: observes the theme settings (via [MainViewModel]) and drives [RollaMusicPlayerTheme]
 * from them, resolving the edge-to-edge system-bar contrast seam described below before every
 * other screen (including [RollaNavHost]) recomposes underneath.
 */
@Composable
private fun RollaAppRoot(activity: ComponentActivity) {
    val mainViewModel: MainViewModel = hiltViewModel()
    // Both StateFlows start at their SettingsRepository-documented defaults (ThemeMode.SYSTEM /
    // false) for one frame before DataStore's first emission lands -- acceptable and standard
    // (every settings-backed StateFlow in this app behaves this way), not worth a blocking read.
    val themeMode by mainViewModel.themeMode.collectAsStateWithLifecycle()
    val useDynamicColor by mainViewModel.useDynamicColor.collectAsStateWithLifecycle()

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    ResolvedEdgeToEdgeEffect(activity = activity, darkTheme = darkTheme)

    RollaMusicPlayerTheme(
        darkTheme = darkTheme,
        dynamicColor = useDynamicColor,
    ) {
        RollaNavHost()
    }
}

/**
 * enableEdgeToEdge()'s own default styles resolve dark/light from the *system* configuration, not
 * the app's resolved [darkTheme] -- which diverges from the system whenever the user forces
 * light/dark in Settings while the device is on the other mode. Re-invoke with an explicit
 * detectDarkMode lambda bound to [darkTheme] so status/nav bar icon contrast always matches what's
 * actually rendered.
 */
@Composable
private fun ResolvedEdgeToEdgeEffect(activity: ComponentActivity, darkTheme: Boolean) {
    DisposableEffect(darkTheme) {
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                Color.TRANSPARENT,
                Color.TRANSPARENT,
            ) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(
                NavigationBarLightScrim,
                NavigationBarDarkScrim,
            ) { darkTheme },
        )
        onDispose {}
    }
}

// Mirrors androidx.activity.EdgeToEdge's own (internal, unreachable from app code) default
// navigation-bar scrim colors used pre-API-29, where the bar can't be made fully transparent and
// needs a translucent scrim for contrast. Duplicated here because we must pass a custom
// detectDarkMode lambda above, which forces calling SystemBarStyle.auto(...) explicitly instead of
// relying on enableEdgeToEdge()'s no-arg default.
//
// Deliberately NOT a :core:designsystem token (ui-style-guide.md §10 does not apply here): these
// are `android.graphics.Color` Ints consumed by the platform SystemBarStyle API, not app UI --
// they paint an OS-owned compositor scrim behind the 3-button navigation bar on old API levels,
// never anything a composable renders. They are versioned to match AndroidX's own edge-to-edge
// default, not a brand/design decision, so they belong next to the `enableEdgeToEdge()` call that
// needs them (here), not in the design-token system that the rest of the app's visuals draw from.
private val NavigationBarLightScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
private val NavigationBarDarkScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)

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
                // Search is reachable from both Library and Playlists top bars, so it never
                // popUpTo's -- back / system back always pops it and returns to whichever of the
                // two callers opened it. launchSingleTop guards double-taps of the search icon.
                onSearchClick = {
                    navController.navigate(Search()) {
                        launchSingleTop = true
                    }
                },
                // Settings is reachable from both Library and Playlists top-bar overflow menus
                // (wired below too). No popUpTo here: navigateUp() (from Settings itself) always
                // pops back to whichever of the two actually opened it, rather than a hard-coded
                // destination. launchSingleTop guards double-taps of the overflow menu's item.
                onSettingsClick = {
                    navController.navigate(Settings) {
                        launchSingleTop = true
                    }
                },
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
        // Equalizer is a leaf pushed on top of whichever screen opened it (today: Now Playing and
        // Settings, both wired). Back = navigateUp() only — pops Equalizer and returns to the
        // caller. No popUpTo here: that would hard-code "always return to X" and break the second
        // entry point. launchSingleTop at each call site guards double-taps of the icon/row.
        composable<Equalizer> {
            EqualizerRoute(onNavigateUp = { navController.navigateUp() })
        }
        composable<Playlists> {
            PlaylistsRoute(
                onPlaylistClick = { id -> navController.navigate(PlaylistDetail(playlistId = id)) },
                onSmartPlaylistClick = { kind -> navController.navigate(SmartPlaylist(kind = kind.name)) },
                // Same explicit back behavior as Library's search entry point above: no popUpTo,
                // launchSingleTop guards double-taps -- Search always pops back to Playlists here.
                onSearchClick = {
                    navController.navigate(Search()) {
                        launchSingleTop = true
                    }
                },
                // Same explicit back behavior as Library's onSettingsClick above: no popUpTo,
                // launchSingleTop guards double-taps -- Settings always pops back to Playlists here.
                onSettingsClick = {
                    navController.navigate(Settings) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable<PlaylistDetail> {
            PlaylistDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
        composable<SmartPlaylist> {
            PlaylistDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
        // AlbumDetail is a leaf reachable today only from Search (wired below); later also from a
        // library Albums tab that doesn't exist yet. Back = navigateUp() only -- no popUpTo here,
        // same shape as TagEditor below -- so it always pops back to whichever screen opened it
        // rather than a hard-coded destination. launchSingleTop at the call site guards
        // double-taps of a search result row.
        composable<AlbumDetail> {
            AlbumDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
        // Search is reachable from two entry points (Library and Playlists top bars, wired
        // above). No popUpTo here: navigateUp() always pops Search off and returns to whichever
        // of the two actually opened it, rather than a hard-coded destination. launchSingleTop at
        // each call site guards double-taps of the search icon.
        composable<Search>(
            deepLinks = listOf(navDeepLink<Search>(basePath = "rollamusic://search")),
        ) {
            SearchRoute(
                onNavigateUp = { navController.navigateUp() },
                // AlbumDetail has shipped and is wired below -- launchSingleTop guards double-taps
                // of a search result row. ArtistDetail hasn't shipped yet (Phase 3 leftover); its
                // no-op activates once that route lands -- same convention as LibraryRoute's
                // onEditTagsClick default before TagEditor existed.
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetail(albumId = albumId)) {
                        launchSingleTop = true
                    }
                },
                onArtistClick = {},
            )
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
        // Settings is reachable from both Library's and Playlists' top-bar overflow menus (wired
        // above). No popUpTo here: navigateUp() always pops Settings and returns to whichever of
        // the two actually opened it, rather than a hard-coded destination. launchSingleTop at
        // each call site guards double-taps of the overflow menu's "Settings" item.
        composable<Settings> {
            SettingsRoute(
                onNavigateUp = { navController.navigateUp() },
                onEqualizerClick = {
                    navController.navigate(Equalizer) {
                        launchSingleTop = true
                    }
                },
                onPrivacyClick = {
                    navController.navigate(Privacy) {
                        launchSingleTop = true
                    }
                },
                onAboutClick = {
                    navController.navigate(About) {
                        launchSingleTop = true
                    }
                },
                onLicensesClick = {
                    navController.navigate(Licenses) {
                        launchSingleTop = true
                    }
                },
            )
        }
        // About is a leaf reachable only from Settings' "About" row (today). navigateUp()-only,
        // no popUpTo -- always pops back to Settings. launchSingleTop above guards double-taps of
        // the row. Version identity comes from PackageInfo (works whether or not the buildConfig
        // build feature is enabled) -- feature modules can't read the app module's BuildConfig.
        composable<About> {
            val context = LocalContext.current
            val buildInfo = remember(context) {
                val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                AppBuildInfo(
                    versionName = packageInfo.versionName ?: "unknown",
                    versionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
                )
            }
            AboutRoute(buildInfo = buildInfo, onNavigateUp = { navController.navigateUp() })
        }
        // Licenses is a leaf reachable only from Settings' "Open-source licenses" row (today).
        // Same back-behavior shape as About above: navigateUp()-only, no popUpTo -- always pops
        // back to Settings. launchSingleTop above guards double-taps of the row.
        composable<Licenses> {
            LicensesRoute(onNavigateUp = { navController.navigateUp() })
        }
        // Privacy is a leaf reachable only from Settings' "Privacy & permissions" row. Same
        // back-behavior shape as About/Licenses above: navigateUp()-only, no popUpTo.
        composable<Privacy> {
            PrivacyRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
}
