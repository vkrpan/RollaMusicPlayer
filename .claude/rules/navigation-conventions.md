# Navigation Conventions

Reference this file when a screen or ViewModel needs to navigate, or when adding a
new destination to the graph. Full ownership of the nav graph itself belongs to
navigation-agent — this file is the shared contract everyone else codes against.

Routing style: *type-safe @Serializable routes* (Navigation Compose 2.8.0+).
Do not introduce string-based routes or sealed class routes — see "Why not sealed
classes" below.

## The graph at a glance

- The start destination is **`Home`**: one screen with a header, a six-tab pager (Favourites · Playlists · Tracks ·
  Albums · Artists · Folders) and the floating mini-player. Tabs are pager pages, not routes, and there is **no bottom
  navigation bar**.
- Everything else is a pushed destination on top of Home: `NowPlaying`, `Equalizer`, `PlaylistDetail`,
  `SmartPlaylist`, `AlbumDetail`, `ArtistDetail`, `FolderDetail`, `TagEditor`, `BatchTagEditor`, `Search`,
  `ManageCustomTags`, `FindTracks` (from the Tracks tab's Search button), `Settings`, `About`, `Licenses`, `Privacy`.
- `app/src/main/kotlin/com/rolla/musicplayer/navigation/Routes.kt` is the source of truth; the examples below are
  taken from it.

## Route definitions

All routes live in a single source of truth: navigation/Routes.kt.

- Routes with no arguments → @Serializable data object
- Routes with arguments → @Serializable data class
- Optional grouping/marker interface (no common sealed type):

```kotlin
interface Route // plain marker interface, NOT sealed

@Serializable data object Home : Route
@Serializable data object NowPlaying : Route
@Serializable data class TagEditor(val songId: Long) : Route          // the audio item is a Song, never a Track
@Serializable data class PlaylistDetail(val playlistId: Long) : Route
@Serializable data class FolderDetail(val folderPath: String) : Route // "" is the volume root
```

- No raw string routes anywhere in the codebase. If you're about to write
  `navController.navigate("player/$id")`, stop — that's the old API.

### Why not sealed classes

kotlinx.serialization cannot cast a sealed type to a NavType without a custom
serializer — it throws `IllegalArgumentException: Cannot cast value of type
kotlinx.serialization.Sealed<...>`. Use a plain (non-sealed) marker interface if you
want a common type for when-style exhaustiveness checks elsewhere, but each route
itself must be a standalone @Serializable object/data class, not a sealed member.

## Arguments

- Arguments are primitives or IDs only: Long, String, Int, Boolean
- Never pass a full data object (Song, Album, Playlist) through a route —
  the destination fetches its own data via its ViewModel, given the ID
- Nullable/optional args get default values in the data class
  (`data class Search(val query: String? = null)`) — Navigation Compose handles
  these as query params automatically
- If a screen "needs" more than 2-3 simple args, that's a signal data should be
  fetched downstream instead of threaded through navigation
- An empty String is a valid non-optional argument: `FolderDetail(folderPath: String)`
  uses `""` for the root folder, and Navigation 2.8 accepts an empty path argument
  (pinned by `FolderDetailRouteTest`). A ViewModel reads route args from its own
  `SavedStateHandle`; `NavBackStackEntry.savedStateHandle` is the result handle and
  does not carry them

## Declaring destinations

Use the typed `composable<T>` builder, not string-based `composable(route = ...)`:

```kotlin
NavHost(navController = navController, startDestination = Home) {
    composable<Home> { HomeRoute(...) }
    composable<PlaylistDetail> { backStackEntry ->
        val args = backStackEntry.toRoute<PlaylistDetail>()
        PlaylistDetailRoute(playlistId = args.playlistId, ...)
    }
}
```

Navigating:

```kotlin
navController.navigate(PlaylistDetail(playlistId = 42L))
navController.navigate(FolderDetail(folderPath = folder.path)) { launchSingleTop = true }
```

Checking the current destination (e.g. whether the mini-player shows on it):

```kotlin
navBackStackEntry?.destination?.hasRoute<NowPlaying>()
```

## Who decides what

- *ui-builder / viewmodel-architect*: trigger navigation by calling
  `navController.navigate(SomeRoute(...))` with the typed route — they do not define
  new routes or change back-stack behavior themselves
- *navigation-agent*: owns route definitions in Routes.kt, the NavHost graph
  structure, back-stack behavior (popUpTo, launchSingleTop, save/restore state),
  and deep links
- *motion-and-interaction-agent*: owns animation curves/timing for transitions;
  navigation-agent owns when a transition fires, motion agent owns how it moves

If you're not navigation-agent and you find yourself editing Routes.kt or the
NavHost composable directly, stop — flag it for navigation-agent instead.

## Shared navigation helpers and testable destinations (redesign Phases 6a–6b)

- A destination reached from many screens gets one helper instead of copied `navigate` bodies. The tag editor is the
  example: every single-track "Edit tags" calls `NavController.openTagEditor(song)` / `openTagEditor(songId)` in
  `app/.../navigation/TagEditorNavigation.kt`, which owns `launchSingleTop = true` and the defensive
  `song.id.toLongOrNull()` (a malformed id no-ops).
- A destination whose wiring needs a JVM test is registered through an `internal fun NavGraphBuilder.xxxDestination(
  navController, provideViewModel: @Composable () -> VM = { hiltViewModel() })` extension
  (`albumDetailDestination`, `artistDetailDestination`, `playlistDetailDestinations`, `searchDestination`), so a test can
  drive the real destination through a real `NavHost` with a fake ViewModel.
- There is no padding wrapper around destinations. A screen that shows the mini-player pads its own list by
  `homeListBottomPadding()`; screens that hide the pill need nothing.

## Back stack behavior

- Any destination reachable from more than one entry point (e.g. NowPlaying
  reachable from the mini-player on every screen that shows it)
  must have explicit popUpTo/back behavior — never rely on defaults for
  multi-entry destinations
- Repeated navigation to the same destination uses `launchSingleTop = true`
- Home's tabs are pager pages, not destinations: the selected tab is saved with the pager state and restored after
  process death; there is no per-tab back stack

## Deep links

- Deep links use `navDeepLink<T>(basePath = "...")` tied to the route type —
  non-optional properties become path params, optional/default-valued properties
  become query params automatically
- Malformed or missing deep link arguments must fall back gracefully, never crash
- New deep link patterns are navigation-agent's responsibility

## Testing

- Check current destination in tests via
  `navController.currentBackStackEntry?.hasRoute<T>()` — don't compare route strings

## Red flags (stop and ask, don't guess)

- Ambiguous "where does back go" UX (e.g. should NowPlaying close to Home,
  or to wherever the user actually came from?) — this is a product decision
- A screen wanting to navigate "around" the typed route system for convenience
- Any attempt to make Route a sealed class/sealed interface — it will break
  serialization; use a plain interface instead
- Circular navigation chains introduced without a stated reason