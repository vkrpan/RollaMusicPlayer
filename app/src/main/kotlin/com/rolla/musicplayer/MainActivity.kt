package com.rolla.musicplayer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.rolla.musicplayer.core.designsystem.theme.RollaMusicPlayerTheme
import com.rolla.musicplayer.core.model.Song
import com.rolla.musicplayer.core.model.ThemeMode
import com.rolla.musicplayer.feature.equalizer.EqualizerRoute
import com.rolla.musicplayer.feature.library.AlbumDetailRoute
import com.rolla.musicplayer.feature.library.AlbumDetailViewModel
import com.rolla.musicplayer.feature.library.ArtistDetailRoute
import com.rolla.musicplayer.feature.library.ArtistDetailViewModel
import com.rolla.musicplayer.feature.library.FolderDetailRoute
import com.rolla.musicplayer.feature.player.MiniPlayerRoute
import com.rolla.musicplayer.feature.player.MiniPlayerViewModel
import com.rolla.musicplayer.feature.player.NowPlayingRoute
import com.rolla.musicplayer.feature.player.QueueSheetRoute
import com.rolla.musicplayer.feature.playlists.PlaylistDetailRoute
import com.rolla.musicplayer.feature.playlists.PlaylistDetailViewModel
import com.rolla.musicplayer.feature.search.SearchRoute
import com.rolla.musicplayer.feature.search.SearchViewModel
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
import com.rolla.musicplayer.navigation.AppShell
import com.rolla.musicplayer.navigation.ArtistDetail
import com.rolla.musicplayer.navigation.BatchTagEditor
import com.rolla.musicplayer.navigation.Equalizer
import com.rolla.musicplayer.navigation.FolderDetail
import com.rolla.musicplayer.navigation.Home
import com.rolla.musicplayer.navigation.Licenses
import com.rolla.musicplayer.navigation.NowPlayingPresence
import com.rolla.musicplayer.navigation.PlaylistDetail
import com.rolla.musicplayer.navigation.Privacy
import com.rolla.musicplayer.navigation.Search
import com.rolla.musicplayer.navigation.Settings
import com.rolla.musicplayer.navigation.SmartPlaylist
import com.rolla.musicplayer.navigation.TagEditor
import com.rolla.musicplayer.navigation.nowPlayingDestination
import com.rolla.musicplayer.navigation.openNowPlaying
import com.rolla.musicplayer.navigation.openTagEditor
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

// LongMethod (kept from the Scaffold version): the overlay wiring (pill, graph, queue sheet) reads best in one place.
// ExperimentalComposeUiApi: testTagsAsResourceId (UiAutomator visibility for :baselineprofile).
@Suppress("LongMethod")
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun RollaNavHost() {
    val navController = rememberNavController()
    val miniPlayerViewModel: MiniPlayerViewModel = hiltViewModel()
    val hasSong by miniPlayerViewModel.hasSong.collectAsStateWithLifecycle()
    var showQueue by rememberSaveable { mutableStateOf(false) }

    AppShell(
        navController = navController,
        hasSong = hasSong,
        // testTagsAsResourceId exposes Compose testTags (e.g. the Tracks list's "song_list") as resource-ids to
        // UiAutomator, which the :baselineprofile generator/benchmarks use to drive the scroll journey. No effect on
        // production behavior or accessibility.
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .semantics { testTagsAsResourceId = true },
        pill = { sharedTransitionScope, animatedVisibilityScope, measure ->
            MiniPlayerRoute(
                sharedTransitionScope = sharedTransitionScope,
                animatedVisibilityScope = animatedVisibilityScope,
                // Pops back to an existing Now Playing instead of stacking a second one (see openNowPlaying).
                onBodyClick = { navController.openNowPlaying() },
                onQueueClick = { showQueue = true },
                modifier = measure,
                viewModel = miniPlayerViewModel,
            )
        },
    ) { sharedTransitionScope, nowPlayingPresence ->
        appDestinations(
            navController = navController,
            sharedTransitionScope = sharedTransitionScope,
            nowPlayingPresence = nowPlayingPresence,
        )
    }
    // The queue sheet lives here, outside the pill's AnimatedVisibility: hiding the pill (for example by opening Now
    // Playing) would otherwise dispose an open sheet.
    if (showQueue) QueueSheetRoute(onDismissRequest = { showQueue = false })
}

/**
 * Every destination of the app graph. Building the graph composes nothing, so tests can build it without Hilt
 * (RouteClassTest checks routeClass() against each destination).
 *
 * No destination is wrapped in an outer bottom padding (Phase 6b ruling 9); each owns its bottom insets:
 * - Home, Search and the detail screens (PlaylistDetail, SmartPlaylist, AlbumDetail, ArtistDetail and FolderDetail)
 *   pad their lists by homeListBottomPadding (the pill plus the navigation bar) and scroll under the pill.
 * - Equaliser, the tag editors, Settings, About, Licenses and Privacy hide the pill
 *   (MiniPlayerVisibility.routesWithoutMiniPlayer), so LocalMiniPlayerInset is 0 there; their Scaffolds own the
 *   navigation-bar inset.
 * - Now Playing owns contentWindowInsets = WindowInsets.navigationBars with the pill hidden.
 *
 * Every "Edit tags" entry point goes through [openTagEditor] (ruling 10).
 */
@Suppress("LongMethod") // one flat entry per route; the detail destinations are NavGraphBuilder extensions below
@OptIn(ExperimentalSharedTransitionApi::class)
internal fun NavGraphBuilder.appDestinations(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    nowPlayingPresence: NowPlayingPresence,
) {
    composable<Home> {
        HomeRoute(
            // Search and Settings are pushed on top of Home (Settings from the header overflow menu). No popUpTo:
            // back / navigateUp() always pops them and returns to Home. launchSingleTop guards double-taps of the
            // search icon and the overflow menu's "Settings" item.
            onSearchClick = { navController.navigate(Search()) { launchSingleTop = true } },
            onSettingsClick = { navController.navigate(Settings) { launchSingleTop = true } },
            // openTagEditor ignores a malformed (non-numeric) id. HomeRoute applies the same parsing to the selection
            // before onEditTagsForSelection.
            onEditTagsClick = { song -> navController.openTagEditor(song) },
            onEditTagsForSelection = { ids ->
                navController.navigate(BatchTagEditor(ids)) { launchSingleTop = true }
            },
            onPlaylistClick = { id -> navController.navigate(PlaylistDetail(playlistId = id)) },
            onSmartPlaylistClick = { kind -> navController.navigate(SmartPlaylist(kind = kind.name)) },
            // No popUpTo on any detail route, so back returns to Home on the same tab (the pager state is saveable).
            onAlbumClick = { id -> navController.navigate(AlbumDetail(albumId = id)) { launchSingleTop = true } },
            onArtistClick = { name ->
                navController.navigate(ArtistDetail(artistName = name)) { launchSingleTop = true }
            },
            onFolderClick = { path ->
                navController.navigate(FolderDetail(folderPath = path)) { launchSingleTop = true }
            },
        )
    }
    // Reports its transition target to the shell, which holds the pill until Now Playing exits (NowPlayingPresence).
    nowPlayingDestination(nowPlayingPresence) {
        NowPlayingRoute(
            onNavigateUp = { navController.navigateUp() },
            onEqualizerClick = {
                navController.navigate(Equalizer) {
                    launchSingleTop = true
                }
            },
            // The ⋮ targets are plain pushes (no popUpTo), so back returns to Now Playing.
            onEditTagsClick = { id -> navController.openTagEditor(id) },
            onAlbumClick = { id -> navController.navigate(AlbumDetail(id)) { launchSingleTop = true } },
            onArtistClick = { name -> navController.navigate(ArtistDetail(name)) { launchSingleTop = true } },
            onSettingsClick = { navController.navigate(Settings) { launchSingleTop = true } },
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
    playlistDetailDestinations(navController)
    albumDetailDestination(navController)
    artistDetailDestination(navController)
    // FolderDetail is reached from Home's Folders tab only. Back = navigateUp(), so it pops back to Home on the same
    // tab (the pager state is saveable). Its list pads by the pill itself and scrolls under it (Phase 4b ruling 7).
    // "Edit tags" pushes TagEditor like Home's options sheet does.
    composable<FolderDetail> {
        FolderDetailRoute(
            onNavigateUp = { navController.navigateUp() },
            onEditTagsClick = { song -> navController.openTagEditor(song) },
        )
    }
    searchDestination(navController)
    // TagEditor is a leaf editor pushed on top of whichever screen opened it (today: Home's
    // Tracks and Favourites tabs, the options sheet on FolderDetail, AlbumDetail, ArtistDetail and
    // PlaylistDetail/SmartPlaylist, Search, and Now Playing's ⋮). Back arrow / Cancel / system back and a
    // successful save all resolve through the same onNavigateUp() -- navigateUp() only, no
    // popUpTo here, so it always pops back to its actual caller rather than a hard-coded
    // destination. openTagEditor's launchSingleTop guards double-taps of the "Edit tags" option.
    composable<TagEditor> {
        TagEditorRoute(onNavigateUp = { navController.navigateUp() })
    }
    // BatchTagEditor is a leaf editor pushed on top of Home (its only entry point today,
    // reached via the selection bar's "Edit tags" action). Same back-behavior shape as
    // TagEditor above: Cancel / back arrow / system back / a fully-succeeded save all resolve
    // through this one onNavigateUp() -- navigateUp() only, no popUpTo -- so it always pops
    // back to Home rather than a hard-coded destination. HomeRoute clears the selection right
    // after this navigate() call fires, so Home is never re-entered still in selection mode.
    // launchSingleTop above guards double-taps of the selection bar's "Edit tags" action.
    composable<BatchTagEditor> {
        BatchTagEditorRoute(onNavigateUp = { navController.navigateUp() })
    }
    // Settings is reachable from the Home header's overflow menu (wired above). No popUpTo
    // here: navigateUp() always pops Settings and returns to Home, rather than a hard-coded
    // destination. launchSingleTop at the call site guards double-taps of the overflow menu's
    // "Settings" item.
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

/**
 * Search is reachable from the Home header (wired above) and the rollamusic://search deep link. No popUpTo:
 * navigateUp() always pops Search and returns to whichever screen opened it, rather than a hard-coded destination.
 * launchSingleTop at the call site guards double-taps of the search icon.
 *
 * Not wrapped in an outer pill padding: Search pads its own lists by homeListBottomPadding, so they scroll under the
 * pill (Phase 6b rulings 3 and 9). Album and artist results push AlbumDetail and ArtistDetail (artists are routed by
 * name: see the ArtistDetail route comment in Routes.kt), each with launchSingleTop against double-taps. A track's ⋮
 * "Edit tags" pushes TagEditor through [openTagEditor], so back returns to Search. [provideViewModel] is the JVM tests'
 * seam, as on [playlistDetailDestinations].
 */
internal fun NavGraphBuilder.searchDestination(
    navController: NavHostController,
    provideViewModel: @Composable () -> SearchViewModel = { hiltViewModel() },
) {
    composable<Search>(
        deepLinks = listOf(navDeepLink<Search>(basePath = "rollamusic://search")),
    ) {
        SearchRoute(
            onNavigateUp = { navController.navigateUp() },
            onAlbumClick = { albumId ->
                navController.navigate(AlbumDetail(albumId = albumId)) { launchSingleTop = true }
            },
            onArtistClick = { artistName ->
                navController.navigate(ArtistDetail(artistName = artistName)) { launchSingleTop = true }
            },
            onEditTagsClick = { song -> navController.openTagEditor(song) },
            viewModel = provideViewModel(),
        )
    }
}

/**
 * PlaylistDetail and SmartPlaylist: one screen for a user playlist and for a smart one (Favourites, Recently played,
 * Most played, Recently added), reached from Home's Playlists tab. Back = navigateUp() only, no popUpTo, so it pops
 * back to Home on the same tab. The list pads by the pill itself and scrolls under it (Phase 6a ruling 5, the
 * FolderDetail precedent). "Edit tags" pushes TagEditor like Home's options sheet does.
 *
 * [provideViewModel] is the JVM tests' seam (DetailRoutesInsetTest, DetailRoutesEditTagsTest): hiltViewModel() needs a
 * Hilt activity, so the tests pass a ViewModel built from fakes. The graph keeps the default.
 */
internal fun NavGraphBuilder.playlistDetailDestinations(
    navController: NavHostController,
    provideViewModel: @Composable () -> PlaylistDetailViewModel = { hiltViewModel() },
) {
    val onEditTagsClick: (Song) -> Unit = { song -> navController.openTagEditor(song) }
    composable<PlaylistDetail> {
        PlaylistDetailRoute(
            onNavigateUp = { navController.navigateUp() },
            onEditTagsClick = onEditTagsClick,
            viewModel = provideViewModel(),
        )
    }
    composable<SmartPlaylist> {
        PlaylistDetailRoute(
            onNavigateUp = { navController.navigateUp() },
            onEditTagsClick = onEditTagsClick,
            viewModel = provideViewModel(),
        )
    }
}

/**
 * AlbumDetail is a leaf reachable from Home's Albums tab, Search, Now Playing's ⋮ "Go to album" and an album card
 * inside ArtistDetail (all wired in this graph). Back = navigateUp() only, with no popUpTo (the TagEditor shape), so it
 * always pops back to whichever screen opened it rather than a hard-coded destination (from Home, the same tab: the
 * pager state is saveable). launchSingleTop at every call site guards double-taps. The list pads by the pill itself
 * and scrolls under it (Phase 6a ruling 5). "Edit tags" pushes TagEditor.
 * [provideViewModel] is the JVM tests' seam, as on [playlistDetailDestinations].
 */
internal fun NavGraphBuilder.albumDetailDestination(
    navController: NavHostController,
    provideViewModel: @Composable () -> AlbumDetailViewModel = { hiltViewModel() },
) {
    composable<AlbumDetail> {
        AlbumDetailRoute(
            onNavigateUp = { navController.navigateUp() },
            onEditTagsClick = { song -> navController.openTagEditor(song) },
            viewModel = provideViewModel(),
        )
    }
}

/**
 * ArtistDetail is reachable from Home's Artists tab, Search and Now Playing's ⋮ "Go to artist" (all wired in this
 * graph). Back = navigateUp() only, with no popUpTo (the AlbumDetail shape), so it always pops back to whichever screen
 * opened it (from Home, the same tab). Album taps inside ArtistDetail push AlbumDetail (a normal forward push, not a
 * back-stack replacement) with their own launchSingleTop guard against double-taps of an album card. The list pads
 * by the pill itself and scrolls under it (Phase 6a ruling 5). "Edit tags" pushes TagEditor. [provideViewModel] is
 * the JVM tests' seam, as on [playlistDetailDestinations].
 */
internal fun NavGraphBuilder.artistDetailDestination(
    navController: NavHostController,
    provideViewModel: @Composable () -> ArtistDetailViewModel = { hiltViewModel() },
) {
    composable<ArtistDetail> {
        ArtistDetailRoute(
            onNavigateUp = { navController.navigateUp() },
            onAlbumClick = { albumId ->
                navController.navigate(AlbumDetail(albumId = albumId)) { launchSingleTop = true }
            },
            onEditTagsClick = { song -> navController.openTagEditor(song) },
            viewModel = provideViewModel(),
        )
    }
}
