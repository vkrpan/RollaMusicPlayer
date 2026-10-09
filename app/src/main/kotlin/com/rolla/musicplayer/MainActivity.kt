package com.rolla.musicplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.core.content.pm.PackageInfoCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
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
import com.rolla.musicplayer.feature.library.ArtistDetailRoute
import com.rolla.musicplayer.feature.player.MiniPlayerRoute
import com.rolla.musicplayer.feature.player.MiniPlayerViewModel
import com.rolla.musicplayer.feature.player.NowPlayingRoute
import com.rolla.musicplayer.feature.playlists.PlaylistDetailRoute
import com.rolla.musicplayer.feature.search.SearchRoute
import com.rolla.musicplayer.feature.settings.AboutRoute
import com.rolla.musicplayer.feature.settings.AppBuildInfo
import com.rolla.musicplayer.feature.settings.LicensesRoute
import com.rolla.musicplayer.feature.settings.PrivacyRoute
import com.rolla.musicplayer.feature.settings.SettingsRoute
import com.rolla.musicplayer.feature.tageditor.BatchTagEditorRoute
import com.rolla.musicplayer.feature.tageditor.TagEditorRoute
import com.rolla.musicplayer.home.HomeRoute
import com.rolla.musicplayer.navigation.About
import com.rolla.musicplayer.navigation.AlbumDetail
import com.rolla.musicplayer.navigation.ArtistDetail
import com.rolla.musicplayer.navigation.BatchTagEditor
import com.rolla.musicplayer.navigation.Equalizer
import com.rolla.musicplayer.navigation.Home
import com.rolla.musicplayer.navigation.Licenses
import com.rolla.musicplayer.navigation.MiniPlayerHost
import com.rolla.musicplayer.navigation.NowPlaying
import com.rolla.musicplayer.navigation.PlaylistDetail
import com.rolla.musicplayer.navigation.Privacy
import com.rolla.musicplayer.navigation.PushedScreen
import com.rolla.musicplayer.navigation.Search
import com.rolla.musicplayer.navigation.Settings
import com.rolla.musicplayer.navigation.SmartPlaylist
import com.rolla.musicplayer.navigation.TagEditor
import com.rolla.musicplayer.navigation.routeClass
import com.rolla.musicplayer.navigation.showsMiniPlayer
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

// LongMethod (kept from the Scaffold version): the overlay wiring reads best in one place, three lines over the limit.
// ExperimentalComposeUiApi: testTagsAsResourceId (UiAutomator visibility for :baselineprofile).
@Suppress("LongMethod")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun RollaNavHost() {
    val navController = rememberNavController()
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val hasSong by miniPlayerViewModel.hasSong.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showPill = hasSong && showsMiniPlayer(currentDestination.routeClass())

    // The pill and the graph share one SharedTransitionLayout, so the pill's artwork keeps its shared-element
    // transition into Now Playing. Insets, each with a single owner: every screen draws edge to edge under the status
    // bar and owns its top inset through its own top bar; AppNavGraph pads the horizontal safe-drawing insets once,
    // for every destination including Home; MiniPlayerHost pads the pill above the navigation bar; Home pads only its
    // lists (they scroll under the pill), and PushedScreen pads every other destination except Now Playing.
    SharedTransitionLayout {
        MiniPlayerHost(
            showPill = showPill,
            // testTagsAsResourceId exposes Compose testTags (e.g. the Tracks list's "song_list") as resource-ids to
            // UiAutomator, which the :baselineprofile generator/benchmarks use to drive the scroll journey. No effect
            // on production behavior or accessibility.
            modifier = Modifier
                .background(MaterialTheme.colorScheme.background)
                .semantics { testTagsAsResourceId = true },
            pill = { measure ->
                MiniPlayerRoute(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    animatedVisibilityScope = this,
                    viewModel = miniPlayerViewModel,
                    modifier = measure,
                    onBodyClick = { navController.navigate(NowPlaying) { launchSingleTop = true } },
                )
            },
        ) {
            AppNavGraph(
                navController = navController,
                sharedTransitionScope = this@SharedTransitionLayout,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun AppNavGraph(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Home,
        modifier = modifier,
    ) {
        appDestinations(navController = navController, sharedTransitionScope = sharedTransitionScope)
    }
}

/**
 * Every destination of the app graph. Building the graph composes nothing, so tests can build it without Hilt
 * (RouteClassTest checks routeClass() against each destination).
 *
 * Every pushed destination below is a Scaffold that owns its navigation-bar inset (default contentWindowInsets; Search
 * excludes only the IME), so PushedScreen adds only the mini-player inset. Home and Now Playing are not wrapped: Home
 * pads its lists itself, and Now Playing owns contentWindowInsets = WindowInsets.navigationBars with the pill hidden.
 */
@Suppress("LongMethod") // one flat entry per route (14); splitting it would scatter the graph across files
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun NavGraphBuilder.appDestinations(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<Home> {
        HomeRoute(
            // Search and Settings are pushed on top of Home (Settings from the header overflow menu). No popUpTo:
            // back / navigateUp() always pops them and returns to Home. launchSingleTop guards double-taps of the
            // search icon and the overflow menu's "Settings" item.
            onSearchClick = { navController.navigate(Search()) { launchSingleTop = true } },
            onSettingsClick = { navController.navigate(Settings) { launchSingleTop = true } },
            // Song.id is the MediaStore row id, a numeric String by construction, so toLongOrNull() is
            // defensive per navigation-conventions.md (never crash on a malformed id): a null id silently
            // no-ops. HomeRoute applies the same parsing to the selection before onEditTagsForSelection.
            onEditTagsClick = { song ->
                song.id.toLongOrNull()?.let { id ->
                    navController.navigate(TagEditor(id)) { launchSingleTop = true }
                }
            },
            onEditTagsForSelection = { ids ->
                navController.navigate(BatchTagEditor(ids)) { launchSingleTop = true }
            },
            onPlaylistClick = { id -> navController.navigate(PlaylistDetail(playlistId = id)) },
            onSmartPlaylistClick = { kind -> navController.navigate(SmartPlaylist(kind = kind.name)) },
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
        PushedScreen {
            EqualizerRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    composable<PlaylistDetail> {
        PushedScreen {
            PlaylistDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    composable<SmartPlaylist> {
        PushedScreen {
            PlaylistDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    // AlbumDetail is a leaf reachable today only from Search (wired below); later also from a
    // library Albums tab that doesn't exist yet. Back = navigateUp() only -- no popUpTo here,
    // same shape as TagEditor below -- so it always pops back to whichever screen opened it
    // rather than a hard-coded destination. launchSingleTop at the call site guards
    // double-taps of a search result row.
    composable<AlbumDetail> {
        PushedScreen {
            AlbumDetailRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    // ArtistDetail is a leaf reachable today only from Search (wired below); later also from
    // a library Artists tab that doesn't exist yet. Back = navigateUp() only -- no popUpTo
    // here, same shape as AlbumDetail above -- so it always pops back to whichever screen
    // opened it rather than a hard-coded destination. Album taps inside ArtistDetail push
    // AlbumDetail (a normal forward push, not a back-stack replacement) with its own
    // launchSingleTop guard against double-taps of an album row.
    composable<ArtistDetail> {
        PushedScreen {
            ArtistDetailRoute(
                onNavigateUp = { navController.navigateUp() },
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetail(albumId = albumId)) { launchSingleTop = true }
                },
            )
        }
    }
    // Search is reachable from the Home header (wired above) and the rollamusic://search deep
    // link. No popUpTo here: navigateUp() always pops Search off and returns to whichever
    // screen opened it, rather than a hard-coded destination. launchSingleTop at the call site
    // guards double-taps of the search icon.
    composable<Search>(
        deepLinks = listOf(navDeepLink<Search>(basePath = "rollamusic://search")),
    ) {
        PushedScreen {
            SearchRoute(
                onNavigateUp = { navController.navigateUp() },
                // AlbumDetail and ArtistDetail have both shipped and are wired below --
                // launchSingleTop guards double-taps of a search result row on each.
                onAlbumClick = { albumId ->
                    navController.navigate(AlbumDetail(albumId = albumId)) {
                        launchSingleTop = true
                    }
                },
                // ArtistDetail is routed by name, not id -- see the ArtistDetail route comment in
                // Routes.kt for why (no artist_id column in the songs schema).
                onArtistClick = { artistName ->
                    navController.navigate(ArtistDetail(artistName = artistName)) {
                        launchSingleTop = true
                    }
                },
            )
        }
    }
    // TagEditor is a leaf editor pushed on top of whichever screen opened it (today: Home's
    // Tracks tab; later: possibly Now Playing or Search). Back arrow / Cancel / system back and a
    // successful save all resolve through the same onNavigateUp() -- navigateUp() only, no
    // popUpTo here, so it always pops back to its actual caller rather than a hard-coded
    // destination. launchSingleTop above guards double-taps of the "Edit tags" option.
    composable<TagEditor> {
        PushedScreen {
            TagEditorRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    // BatchTagEditor is a leaf editor pushed on top of Home (its only entry point today,
    // reached via the selection bar's "Edit tags" action). Same back-behavior shape as
    // TagEditor above: Cancel / back arrow / system back / a fully-succeeded save all resolve
    // through this one onNavigateUp() -- navigateUp() only, no popUpTo -- so it always pops
    // back to Home rather than a hard-coded destination. HomeRoute clears the selection right
    // after this navigate() call fires, so Home is never re-entered still in selection mode.
    // launchSingleTop above guards double-taps of the selection bar's "Edit tags" action.
    composable<BatchTagEditor> {
        PushedScreen {
            BatchTagEditorRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    // Settings is reachable from the Home header's overflow menu (wired above). No popUpTo
    // here: navigateUp() always pops Settings and returns to Home, rather than a hard-coded
    // destination. launchSingleTop at the call site guards double-taps of the overflow menu's
    // "Settings" item.
    composable<Settings> {
        PushedScreen {
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
        PushedScreen {
            AboutRoute(buildInfo = buildInfo, onNavigateUp = { navController.navigateUp() })
        }
    }
    // Licenses is a leaf reachable only from Settings' "Open-source licenses" row (today).
    // Same back-behavior shape as About above: navigateUp()-only, no popUpTo -- always pops
    // back to Settings. launchSingleTop above guards double-taps of the row.
    composable<Licenses> {
        PushedScreen {
            LicensesRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
    // Privacy is a leaf reachable only from Settings' "Privacy & permissions" row. Same
    // back-behavior shape as About/Licenses above: navigateUp()-only, no popUpTo.
    composable<Privacy> {
        PushedScreen {
            PrivacyRoute(onNavigateUp = { navController.navigateUp() })
        }
    }
}
