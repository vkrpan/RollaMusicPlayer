---
name: implement-navigation-graph
description: "Comprehensive guide for setting up and managing Compose Navigation with type-safe arguments, deep linking, nested graphs, and proper back stack handling."
---

# Skill: Implement Navigation Graph

## Overview
Comprehensive guide for setting up and managing Compose Navigation with type-safe arguments, deep linking, nested graphs, and proper back stack handling.

## When to Use
- Setting up navigation in a new Compose app
- Adding new screens to navigation flow
- Implementing deep linking
- Creating nested navigation structures
- Refactoring navigation architecture

## Prerequisites
- Jetpack Compose setup
- Basic understanding of Compose
- Hilt dependency injection configured
- Understanding of Android navigation concepts

## Workflow Steps

### Step 1: Add Navigation Dependencies
**Goal**: Configure project for Compose Navigation

**Actions**:
1. Add navigation dependencies
2. Add serialization for type-safe navigation
3. Sync project

**Implementation**:
```kotlin
// build.gradle.kts (app module)
plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.20"
}

dependencies {
    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.6")
    
    // Hilt Navigation Compose
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    
    // Kotlinx Serialization for type-safe navigation
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    
    // Testing
    androidTestImplementation("androidx.navigation:navigation-testing:2.7.6")
}
```

### Step 2: Define Navigation Routes
**Goal**: Create type-safe navigation destinations

**Actions**:
1. Define route objects using @Serializable
2. Create sealed class for all routes
3. Organize routes by feature

**Implementation**:
```kotlin
// navigation/Routes.kt
import kotlinx.serialization.Serializable

sealed interface Route {
    
    // Main destinations
    @Serializable
    data object Library : Route
    
    @Serializable
    data object Player : Route
    
    @Serializable
    data object Playlists : Route
    
    @Serializable
    data object Settings : Route
    
    // Destinations with arguments
    @Serializable
    data class PlaylistDetail(
        val playlistId: String
    ) : Route
    
    @Serializable
    data class AlbumDetail(
        val albumId: String,
        val albumName: String = ""
    ) : Route
    
    @Serializable
    data class ArtistDetail(
        val artistId: String
    ) : Route
    
    @Serializable
    data class SongDetail(
        val songId: String
    ) : Route
    
    @Serializable
    data class Equalizer(
        val presetId: String? = null
    ) : Route
    
    @Serializable
    data class TagEditor(
        val songIds: List<String>
    ) : Route
}
```

### Step 3: Create NavHost Setup
**Goal**: Setup main navigation container

**Actions**:
1. Create NavHost in main activity
2. Define start destination
3. Setup navigation controller

**Implementation**:
```kotlin
// MainActivity.kt
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MusicPlayerTheme {
                MusicPlayerApp()
            }
        }
    }
}

// MusicPlayerApp.kt
@Composable
fun MusicPlayerApp() {
    val navController = rememberNavController()
    
    Scaffold(
        bottomBar = {
            // Bottom navigation bar if needed
            MusicPlayerBottomBar(navController)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Route.Library,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Define navigation graph
            libraryGraph(navController)
            playerGraph(navController)
            playlistGraph(navController)
            settingsGraph(navController)
        }
    }
}
```

### Step 4: Define Navigation Graph Extensions
**Goal**: Organize navigation by feature

**Actions**:
1. Create extension functions for NavGraphBuilder
2. Group related screens
3. Handle navigation actions

**Implementation**:
```kotlin
// navigation/LibraryNavGraph.kt
fun NavGraphBuilder.libraryGraph(navController: NavController) {
    composable<Route.Library> {
        LibraryScreen(
            onNavigateToAlbum = { albumId, albumName ->
                navController.navigate(
                    Route.AlbumDetail(albumId, albumName)
                )
            },
            onNavigateToArtist = { artistId ->
                navController.navigate(Route.ArtistDetail(artistId))
            },
            onNavigateToPlayer = {
                navController.navigate(Route.Player)
            }
        )
    }
    
    composable<Route.AlbumDetail> { backStackEntry ->
        val args = backStackEntry.toRoute<Route.AlbumDetail>()
        AlbumDetailScreen(
            albumId = args.albumId,
            albumName = args.albumName,
            onNavigateBack = { navController.navigateUp() },
            onNavigateToPlayer = {
                navController.navigate(Route.Player)
            }
        )
    }
    
    composable<Route.ArtistDetail> { backStackEntry ->
        val args = backStackEntry.toRoute<Route.ArtistDetail>()
        ArtistDetailScreen(
            artistId = args.artistId,
            onNavigateBack = { navController.navigateUp() },
            onNavigateToAlbum = { albumId, albumName ->
                navController.navigate(
                    Route.AlbumDetail(albumId, albumName)
                )
            }
        )
    }
}

// navigation/PlayerNavGraph.kt
fun NavGraphBuilder.playerGraph(navController: NavController) {
    composable<Route.Player>(
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Up,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Down,
                animationSpec = tween(300)
            )
        }
    ) {
        PlayerScreen(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToEqualizer = { presetId ->
                navController.navigate(Route.Equalizer(presetId))
            },
            onNavigateToTagEditor = { songId ->
                navController.navigate(Route.TagEditor(listOf(songId)))
            }
        )
    }
    
    composable<Route.Equalizer> { backStackEntry ->
        val args = backStackEntry.toRoute<Route.Equalizer>()
        EqualizerScreen(
            presetId = args.presetId,
            onNavigateBack = { navController.navigateUp() }
        )
    }
}

// navigation/PlaylistNavGraph.kt
fun NavGraphBuilder.playlistGraph(navController: NavController) {
    composable<Route.Playlists> {
        PlaylistsScreen(
            onNavigateToPlaylist = { playlistId ->
                navController.navigate(Route.PlaylistDetail(playlistId))
            },
            onNavigateBack = { navController.navigateUp() }
        )
    }
    
    composable<Route.PlaylistDetail> { backStackEntry ->
        val args = backStackEntry.toRoute<Route.PlaylistDetail>()
        PlaylistDetailScreen(
            playlistId = args.playlistId,
            onNavigateBack = { navController.navigateUp() },
            onNavigateToPlayer = {
                navController.navigate(Route.Player)
            }
        )
    }
}
```

### Step 5: Implement Nested Navigation
**Goal**: Create hierarchical navigation structure

**Actions**:
1. Define nested graphs
2. Setup parent-child relationships
3. Handle nested back stack

**Implementation**:
```kotlin
// Nested graph example
fun NavGraphBuilder.settingsGraph(navController: NavController) {
    navigation<Route.Settings>(
        startDestination = Route.SettingsMain
    ) {
        composable<Route.SettingsMain> {
            SettingsMainScreen(
                onNavigateToAppearance = {
                    navController.navigate(Route.SettingsAppearance)
                },
                onNavigateToPlayback = {
                    navController.navigate(Route.SettingsPlayback)
                },
                onNavigateToLibrary = {
                    navController.navigate(Route.SettingsLibrary)
                },
                onNavigateBack = { navController.navigateUp() }
            )
        }
        
        composable<Route.SettingsAppearance> {
            AppearanceSettingsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        
        composable<Route.SettingsPlayback> {
            PlaybackSettingsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
        
        composable<Route.SettingsLibrary> {
            LibrarySettingsScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}

// Extended Routes for nested navigation
sealed interface Route {
    // ... previous routes ...
    
    @Serializable
    data object Settings : Route
    
    @Serializable
    data object SettingsMain : Route
    
    @Serializable
    data object SettingsAppearance : Route
    
    @Serializable
    data object SettingsPlayback : Route
    
    @Serializable
    data object SettingsLibrary : Route
}
```

### Step 6: Setup Deep Linking
**Goal**: Enable navigation from external sources

**Actions**:
1. Define deep link patterns
2. Add intent filters to manifest
3. Handle deep link navigation

**Implementation**:
```kotlin
// Define deep links in navigation graph
composable<Route.AlbumDetail>(
    deepLinks = listOf(
        navDeepLink<Route.AlbumDetail>(
            basePath = "musicplayer://album"
        )
    )
) { backStackEntry ->
    val args = backStackEntry.toRoute<Route.AlbumDetail>()
    AlbumDetailScreen(
        albumId = args.albumId,
        albumName = args.albumName,
        onNavigateBack = { navController.navigateUp() }
    )
}

// AndroidManifest.xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
    
    <!-- Deep link for albums -->
    <intent-filter android:autoVerify="true">
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="musicplayer"
            android:host="album" />
    </intent-filter>
    
    <!-- Deep link for playlists -->
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data
            android:scheme="musicplayer"
            android:host="playlist" />
    </intent-filter>
</activity>

// Handle deep link in Activity
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            MusicPlayerTheme {
                val navController = rememberNavController()
                
                // Handle deep link
                LaunchedEffect(intent) {
                    handleDeepLink(intent, navController)
                }
                
                MusicPlayerApp(navController)
            }
        }
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
    
    private fun handleDeepLink(intent: Intent, navController: NavController) {
        val data = intent.data ?: return
        
        when (data.host) {
            "album" -> {
                val albumId = data.getQueryParameter("id") ?: return
                navController.navigate(Route.AlbumDetail(albumId))
            }
            "playlist" -> {
                val playlistId = data.getQueryParameter("id") ?: return
                navController.navigate(Route.PlaylistDetail(playlistId))
            }
        }
    }
}
```

### Step 7: Handle Back Stack
**Goal**: Manage navigation back stack properly

**Actions**:
1. Implement proper back navigation
2. Handle pop behavior
3. Clear back stack when needed

**Implementation**:
```kotlin
// Pop to specific destination
navController.navigate(Route.Library) {
    popUpTo(Route.Library) {
        inclusive = true
    }
}

// Pop to start destination
navController.navigate(Route.Library) {
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}

// Clear entire back stack
navController.navigate(Route.Library) {
    popUpTo(0) {
        inclusive = true
    }
}

// Navigate with single top
navController.navigate(Route.Player) {
    launchSingleTop = true
}

// Handle system back button
@Composable
fun MusicPlayerApp(navController: NavHostController = rememberNavController()) {
    BackHandler(enabled = navController.previousBackStackEntry != null) {
        navController.navigateUp()
    }
    
    // Rest of the app
}
```

### Step 8: Add Navigation Animations
**Goal**: Smooth transitions between screens

**Actions**:
1. Define enter/exit transitions
2. Apply to composable destinations
3. Create custom animations

**Implementation**:
```kotlin
// Standard slide animations
composable<Route.AlbumDetail>(
    enterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(300)
        )
    },
    exitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(300)
        )
    },
    popEnterTransition = {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(300)
        )
    },
    popExitTransition = {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(300)
        )
    }
) { backStackEntry ->
    // Screen content
}

// Fade animation
composable<Route.Settings>(
    enterTransition = { fadeIn(animationSpec = tween(300)) },
    exitTransition = { fadeOut(animationSpec = tween(300)) }
) {
    // Screen content
}

// Scale animation
composable<Route.Player>(
    enterTransition = {
        scaleIn(
            initialScale = 0.9f,
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300))
    },
    exitTransition = {
        scaleOut(
            targetScale = 0.9f,
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    }
) {
    // Screen content
}
```

### Step 9: Test Navigation
**Goal**: Ensure navigation works correctly

**Actions**:
1. Write navigation tests
2. Test deep links
3. Verify back stack behavior

**Implementation**:
```kotlin
@RunWith(AndroidJUnit4::class)
class NavigationTest {
    
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()
    
    private lateinit var navController: TestNavHostController
    
    @Before
    fun setup() {
        composeTestRule.setContent {
            navController = TestNavHostController(LocalContext.current)
            navController.navigatorProvider.addNavigator(
                ComposeNavigator()
            )
            MusicPlayerApp(navController)
        }
    }
    
    @Test
    fun navHost_verifyStartDestination() {
        composeTestRule
            .onNodeWithText("Library")
            .assertIsDisplayed()
    }
    
    @Test
    fun navHost_navigateToPlayer() {
        // Navigate to player
        composeTestRule
            .onNodeWithContentDescription("Play")
            .performClick()
        
        // Verify player screen is displayed
        val route = navController.currentBackStackEntry?.destination?.route
        assertEquals(Route.Player::class.qualifiedName, route)
    }
    
    @Test
    fun navHost_navigateToAlbumAndBack() {
        // Navigate to album
        composeTestRule
            .onNodeWithText("Test Album")
            .performClick()
        
        // Verify album screen
        composeTestRule
            .onNodeWithText("Album Details")
            .assertIsDisplayed()
        
        // Navigate back
        composeTestRule
            .onNodeWithContentDescription("Navigate back")
            .performClick()
        
        // Verify back at library
        composeTestRule
            .onNodeWithText("Library")
            .assertIsDisplayed()
    }
    
    @Test
    fun navHost_deepLinkToAlbum() {
        val deepLinkUri = "musicplayer://album?id=123"
        navController.navigate(deepLinkUri.toUri())
        
        val route = navController.currentBackStackEntry?.destination?.route
        assertTrue(route?.contains("AlbumDetail") == true)
    }
}
```

## Addons

### Bottom Navigation Integration
```kotlin
@Composable
fun MusicPlayerBottomBar(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    
    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                selected = currentDestination?.hierarchy?.any {
                    it.route == item.route::class.qualifiedName
                } == true,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(
    val route: Route,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Route.Library, Icons.Default.LibraryMusic, "Library"),
    BottomNavItem(Route.Playlists, Icons.Default.QueueMusic, "Playlists"),
    BottomNavItem(Route.Settings, Icons.Default.Settings, "Settings")
)
```

### Navigation ViewModel
```kotlin
@HiltViewModel
class NavigationViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    
    private val _navigationEvent = MutableSharedFlow<NavigationEvent>()
    val navigationEvent = _navigationEvent.asSharedFlow()
    
    fun navigateToAlbum(albumId: String, albumName: String) {
        viewModelScope.launch {
            _navigationEvent.emit(
                NavigationEvent.ToAlbumDetail(albumId, albumName)
            )
        }
    }
    
    fun navigateBack() {
        viewModelScope.launch {
            _navigationEvent.emit(NavigationEvent.Back)
        }
    }
}

sealed interface NavigationEvent {
    data class ToAlbumDetail(val albumId: String, val albumName: String) : NavigationEvent
    data class ToPlayer(val songId: String?) : NavigationEvent
    data object Back : NavigationEvent
}

// Usage in composable
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    navViewModel: NavigationViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) {
        navViewModel.navigationEvent.collect { event ->
            when (event) {
                is NavigationEvent.ToAlbumDetail -> {
                    // Handle navigation
                }
                is NavigationEvent.Back -> {
                    // Handle back
                }
                else -> {}
            }
        }
    }
}
```

## Related Files
- `navigation/Routes.kt` - Route definitions
- `navigation/*NavGraph.kt` - Navigation graph extensions
- `MainActivity.kt` - NavHost setup
- `*Screen.kt` - Screen composables

## Notes
- Use type-safe navigation with kotlinx.serialization
- Organize routes by feature
- Keep navigation logic out of ViewModels when possible
- Use proper scoping for navigation state
- Handle configuration changes properly
- Test navigation flows thoroughly
- Use animations for better UX
- Handle deep links appropriately
- Manage back stack carefully to avoid memory leaks
- Consider using shared ViewModels for data sharing between screens

## Common Patterns

### Passing Complex Objects
```kotlin
// Don't pass complex objects directly
// Instead, pass IDs and fetch data in destination

// Bad
@Serializable
data class AlbumDetail(val album: Album) // Don't serialize complex objects

// Good
@Serializable
data class AlbumDetail(val albumId: String)

// Fetch in destination
@Composable
fun AlbumDetailScreen(
    albumId: String,
    viewModel: AlbumDetailViewModel = hiltViewModel()
) {
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }
}
```

### Shared ViewModel Between Screens
```kotlin
@Composable
fun MusicPlayerApp() {
    val navController = rememberNavController()
    val parentEntry = remember(navController.currentBackStackEntry) {
        navController.getBackStackEntry(Route.Library)
    }
    val sharedViewModel: SharedViewModel = hiltViewModel(parentEntry)
    
    NavHost(navController, startDestination = Route.Library) {
        composable<Route.Library> {
            LibraryScreen(sharedViewModel = sharedViewModel)
        }
        composable<Route.AlbumDetail> {
            AlbumDetailScreen(sharedViewModel = sharedViewModel)
        }
    }
}
```

### Result Handling
```kotlin
// Return result to previous screen
navController.previousBackStackEntry
    ?.savedStateHandle
    ?.set("playlist_created", true)
navController.navigateUp()

// Receive result
val result = navController.currentBackStackEntry
    ?.savedStateHandle
    ?.getStateFlow("playlist_created", false)
    ?.collectAsState()