# Navigation Conventions

Reference this file when a screen or ViewModel needs to navigate, or when adding a
new destination to the graph. Full ownership of the nav graph itself belongs to
navigation-agent — this file is the shared contract everyone else codes against.

Routing style: *type-safe @Serializable routes* (Navigation Compose 2.8.0+).
Do not introduce string-based routes or sealed class routes — see "Why not sealed
classes" below.

> **The examples below predate the One UI Home shell.** The real start destination
> is `Home` (a pager of six tabs), there is no `Library` route, `LibraryScreen` or
> bottom nav, and the audio-item route names use `Song`, not `Track`. Copy the
> patterns, not the route names; read `app/.../navigation/Routes.kt` for the real
> graph. The redesign's Phase 7 docs pass rewrites these examples (spec §16).

## Route definitions

All routes live in a single source of truth: navigation/Routes.kt.

- Routes with no arguments → @Serializable data object
- Routes with arguments → @Serializable data class
- Optional grouping/marker interface (no common sealed type):

```kotlin
interface Route // plain marker interface, NOT sealed

@Serializable data object Library : Route
@Serializable data object NowPlaying : Route
@Serializable data class TrackDetail(val trackId: Long) : Route
@Serializable data class PlaylistDetail(val playlistId: Long) : Route
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
- Never pass a full data object (Track, Album, Playlist) through a route —
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
NavHost(navController = navController, startDestination = Library) {
    composable<Library> { LibraryScreen(...) }
    composable<TrackDetail> { backStackEntry ->
        val args = backStackEntry.toRoute<TrackDetail>()
        TrackDetailScreen(trackId = args.trackId, ...)
    }
}
```

Navigating:

```kotlin
navController.navigate(TrackDetail(trackId = 42L))
```

Checking current destination (e.g. for bottom nav highlight state):

```kotlin
navBackStackEntry?.destination?.hasRoute<Library>()
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
  reachable from Library, from a persistent mini-player, and from a notification)
  must have explicit popUpTo/back behavior — never rely on defaults for
  multi-entry destinations
- Repeated navigation to the same destination uses `launchSingleTop = true`
- Bottom nav tabs preserve their own back stack across tab switches using
  saveState/restoreState, matched on the route type via `hasRoute<T>()`

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

- Ambiguous "where does back go" UX (e.g. should NowPlaying close to Library,
  or to wherever the user actually came from?) — this is a product decision
- A screen wanting to navigate "around" the typed route system for convenience
- Any attempt to make Route a sealed class/sealed interface — it will break
  serialization; use a plain interface instead
- Circular navigation chains introduced without a stated reason