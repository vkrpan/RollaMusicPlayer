---
name: implement-home-widget
description: "Step-by-step workflow for building the RollaMusicPlayer home screen widget with Jetpack Glance — album artwork, progress/timeline bar, play/pause, previous/next, and 15-second skip controls — driven by local playback state and fully offline. Covers the three things that make it actually work: reading the single PlaybackStateHolder (correct song), observing it INSIDE the Glance composition (live progress — a pre-provideContent snapshot freezes), and running connect() + every control command on the main thread (working buttons — MediaController rejects off-thread calls and Glance swallows the error)."
---

# Skill: Implement Home Screen Widget

## Overview
A workflow for building RollaMusicPlayer's home screen widget (the `:feature:widget` module) using Jetpack Glance. The widget mirrors playback on the launcher: album artwork, song title/artist, a visual progress bar, play/pause, previous/next, and 15-second skip back/forward. It is push-updated by the playback service through a Hilt multibinding seam and operates entirely offline (artwork from local bitmaps only).

## The three invariants (read before writing any code)

Three wiring facts determine whether the widget works. Every other step exists to support them. All three were learned the hard way: the v1.0 widget shipped with buttons that did nothing and a progress bar that never moved on real phones, while every unit test passed.

1. **Correct song = read the ONE source of truth.** The widget's `WidgetStateProvider` is a *read-only adapter* over `:core:media`'s `PlaybackStateHolder` — the single object the playback service writes state into. It is **never** a second holder the service pushes into; a widget-owned copy is exactly what goes stale and shows the wrong/blank song.
2. **Live progress = observe the state INSIDE the composition.** `GlanceAppWidget.update()`/`updateAll()` do **not** re-run `provideGlance` while a session is alive. A session lives ~45s after `provideContent` and every update request extends it — and the service fires one every second while music plays, so the session never ends during playback. Any value read *before* `provideContent` is therefore frozen for the whole playback session (progress bar, play/pause icon, title). Read `WidgetStateProvider.current()` only to seed the first frame; inside `provideContent`, `collectAsState` on `WidgetStateProvider.states`.
3. **Working buttons = `connect()` + command on the MAIN thread.** Glance runs `ActionCallback.onAction` on `Dispatchers.Default`. `PlaybackController` drives a Media3 `MediaController`, which throws `IllegalStateException("MediaController method is called from a wrong thread")` for any call off its application (main) thread. Glance's `ActionCallbackBroadcastReceiver` catches that and only logs it, so the tap silently does nothing. It only *looks* fine on a cold first tap (the command queues on the connect future, whose listener happens to run on main). Every callback must go through `dispatchToController`, which does `withContext(Dispatchers.Main.immediate) { connect(); command() }`. Calling `connect()` first is necessary, but it is NOT the fix — the thread is.

## When to Use
- Building the home screen widget feature
- Adding or changing widget controls or layout
- Wiring the playback service to update the widget in real time
- Fixing widget "wrong song" / "buttons do nothing" / "progress bar frozen" / artwork / update issues

## Prerequisites
- `:core:media` exposes `PlaybackStateHolder` (single source of truth for current song, isPlaying, position, duration), `PlaybackController` (with `connect()` + transport commands routed through `withConnectedController` queuing — **main-thread only**, because it calls `MediaController` directly once connected), and the `PlaybackUpdateHook` seam (`fun interface` + `PlaybackUpdateDispatcher` + `@Multibinds` in `PlaybackUpdateModule`)
- Hilt is configured (for `EntryPointAccessors` in the receiver/callbacks)
- `AlbumArtworkCache` (in `:core:data`) is available for downscaled local artwork
- Minimum SDK 24; widget targets the same min/target as the app
- Module direction is fixed: `:feature:widget` depends on `:core:media`; `:core:media` MUST NOT depend on `:feature:widget`

## Module layout
```
feature/widget/src/main/kotlin/com/rolla/musicplayer/feature/widget/
  MusicWidget.kt · MusicWidgetReceiver.kt · MusicWidgetContent.kt · MusicWidgetActions.kt
  MusicWidgetState.kt · MusicWidgetTheme.kt · WidgetStateProvider.kt · WidgetArtworkLoader.kt
  WidgetEntryPoint.kt · WidgetModule.kt · WidgetPlaybackUpdateHook.kt · WidgetIoDispatcher.kt
feature/widget/src/main/res/xml/music_widget_info.xml
feature/widget/src/main/AndroidManifest.xml
```

## Workflow Steps

### Step 1: Add Glance Dependencies
**Goal**: Configure `:feature:widget` for Glance App Widgets, and make sure `:app` depends on `:feature:widget` (so the widget's Hilt modules and manifest receiver are merged in).

```kotlin
// feature/widget/build.gradle.kts
dependencies {
    implementation(project(":core:media"))    // PlaybackStateHolder, PlaybackController, PlaybackUpdateHook
    implementation(project(":core:data"))      // AlbumArtworkCache
    implementation(libs.glance.appwidget)      // version catalog: glance = "1.1.0"
    implementation(libs.glance.material3)
}

// app/build.gradle.kts  — REQUIRED, or the widget's @IntoSet hook and receiver never register
dependencies {
    implementation(project(":feature:widget"))
}
```
Confirm no networking libraries are pulled in (offline constraint).

### Step 2: Define the Widget State Model + the read-only adapter
**Goal**: Represent exactly what the widget renders, and read it from the ONE source of truth.

```kotlin
// MusicWidgetState.kt
data class MusicWidgetState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artworkPath: String? = null,   // local content:// uri only — never a URL
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

// WidgetStateProvider.kt  — READ-ONLY adapter over PlaybackStateHolder.
// This is the invariant: one writer (PlaybackService), the widget only reads.
interface WidgetStateProvider {
    fun current(): MusicWidgetState              // seeds the first frame of a Glance session
    val states: Flow<MusicWidgetState>           // what the composition actually observes
}

@Singleton
class WidgetStateProviderImpl @Inject constructor(
    private val playbackStateHolder: PlaybackStateHolder,   // the single source of truth
) : WidgetStateProvider {
    override fun current(): MusicWidgetState = toWidgetState(
        song = playbackStateHolder.currentSong.value,
        isPlaying = playbackStateHolder.isPlaying.value,
        positionMs = playbackStateHolder.positionMs.value,
        durationMs = playbackStateHolder.durationMs.value,
    )

    override val states: Flow<MusicWidgetState> = combine(
        playbackStateHolder.currentSong,
        playbackStateHolder.isPlaying,
        playbackStateHolder.positionMs,
        playbackStateHolder.durationMs,
        ::toWidgetState,
    ).distinctUntilChanged()

    private fun toWidgetState(song: Song?, isPlaying: Boolean, positionMs: Long, durationMs: Long) =
        song?.let {
            MusicWidgetState(it.title, it.artist, isPlaying, positionMs, durationMs, it.artworkUri.ifBlank { null })
        } ?: MusicWidgetState()
}
```
> Anti-pattern to avoid: a `WidgetStateProvider` that stores its own `MusicWidgetState` and exposes a `set(...)` the service calls. That is a second source of state and is the root cause of "widget shows the wrong song." Read the holder; don't mirror it.
>
> `current()` and `states` must share one mapping function (`WidgetStateProviderTest` asserts `states.first() == current()`), and `durationMs` comes from the holder's flow, never `Song.durationMs` (the service builds `Song` with `durationMs = 0` on transition).

### Step 3: Load and Downscale Local Artwork
**Goal**: A memory-safe bitmap for the widget (no network, no oversized bitmaps), routed through the shared `AlbumArtworkCache`.

```kotlin
// WidgetArtworkLoader.kt
@Singleton
class WidgetArtworkLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val albumArtworkCache: AlbumArtworkCache,
    @WidgetIoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    suspend fun load(artworkPath: String?): Bitmap? {
        if (artworkPath.isNullOrBlank()) return null
        return withContext(ioDispatcher) {
            try {
                // Parse albumId off the content://.../albumart/{albumId} uri, ask the shared cache
                // for an already-downscaled local JPEG file, then two-pass decode it with inSampleSize
                // to a ~256px target. Unparseable paths fall back to a direct content:// decode.
                // Memoize the last decode by (path, lastModified) so the ~1s tick doesn't re-decode.
                decodeDownscaled(artworkPath)   // returns null on any failure → placeholder
            } catch (e: CancellationException) {
                throw e
            } catch (ignored: Exception) {
                null
            }
        }
    }
}
```
Return `null` on missing/undecodable art; the Glance UI renders the themed placeholder. Never let a decode throw out of `load`.

### Step 4: Build the Glance Widget UI
**Goal**: Render artwork, metadata, progress bar, and controls with the app's dark card style.

```kotlin
// MusicWidget.kt
class MusicWidget : GlanceAppWidget() {
    // Exact: LocalSize reports the real launcher-allotted size (Single only reports the provider minimum),
    // so the layout can stretch to fill the card.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = WidgetEntryPoint.get(context)
        val stateProvider = entryPoint.widgetStateProvider()
        val artworkLoader = entryPoint.artworkLoader()
        val initialState = stateProvider.current()                      // first frame ONLY
        val initialArtwork = artworkLoader.load(initialState.artworkPath)

        provideContent {
            // Observe INSIDE the composition — update()/updateAll() never re-run provideGlance while
            // the session is alive, so anything read above this line would freeze.
            val state by stateProvider.states.collectAsState(initial = initialState)
            val artworkPath = state.artworkPath
            val artwork by produceState(initialArtwork, artworkPath) {     // re-resolves on album change
                value = artworkLoader.load(artworkPath)                    // memoized: same path is cheap
            }
            GlanceTheme(colors = RollaWidgetColors) {                   // app palette, day == night
                MusicWidgetContent(context, state, artwork)
            }
        }
    }
}
```

```kotlin
// MusicWidgetContent.kt (abridged — full layout per ui-style-guide.md §8)
@Composable
internal fun MusicWidgetContent(context: Context, state: MusicWidgetState, artwork: Bitmap?) {
    val openApp = actionStartActivity(
        ComponentName(context.packageName, "com.rolla.musicplayer.MainActivity")  // string: no :app dep
    )
    val artworkSize = artworkSizeFor(LocalSize.current)   // grows into leftover height, 56..160dp, ≤40% width
    Box(GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_card_background))
        .padding(12.dp).clickable(openApp)) {
        Column(GlanceModifier.fillMaxSize()) {
            // Header (artwork + title/artist) takes ALL height the fixed rows leave → no dead space below.
            WidgetHeaderRow(context, state, artwork, artworkSize, GlanceModifier.fillMaxWidth().defaultWeight())
            WidgetProgressBar(state.progress)   // primary fill on surfaceVariant (#6E6E73) track
            WidgetControlsRow(context, state)   // pinned to the bottom
        }
    }
}

@Composable
private fun WidgetControlsRow(context: Context, state: MusicWidgetState) {
    // Each WidgetIconButton is a RowScope extension sitting in an equal defaultWeight() cell,
    // so the five controls spread evenly across the full card width.
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
        WidgetIconButton(R.drawable.widget_ic_previous, "Previous", actionRunCallback<PreviousActionCallback>())
        WidgetIconButton(R.drawable.widget_ic_skip_back_15, "Skip back 15 seconds", actionRunCallback<SkipBack15ActionCallback>())
        WidgetIconButton(
            if (state.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
            if (state.isPlaying) "Pause" else "Play",
            actionRunCallback<PlayPauseActionCallback>(),
        )
        WidgetIconButton(R.drawable.widget_ic_skip_forward_15, "Skip forward 15 seconds", actionRunCallback<SkipForward15ActionCallback>())
        WidgetIconButton(R.drawable.widget_ic_next, "Next", actionRunCallback<NextActionCallback>())
    }
}

// Touch target: .size(48.dp).clickable(action).padding(12.dp) — clickable on the FULL 48dp node,
// padding insets only the icon. Padding BEFORE clickable would shrink the hit target below 48dp.
```

### Step 5: Implement Control Callbacks — `connect()` + command on the MAIN thread
**Goal**: Dispatch to the existing playback service (no second player), and make the buttons actually do something.

```kotlin
// MusicWidgetActions.kt
private suspend fun withController(context: Context, glanceId: GlanceId, block: (PlaybackController) -> Unit) {
    dispatchToController(WidgetEntryPoint.get(context).playbackController(), command = block)
    MusicWidget().update(context, glanceId)   // starts a session if none is alive; a live one repaints from `states`
}

// Glance calls onAction on Dispatchers.Default. MediaController throws "called from a wrong thread" off
// main, and Glance's receiver swallows it (only a GlanceAppWidget logcat line) → dead buttons.
// connect() also runs here: PlaybackController.controllerFuture is an unsynchronized field.
internal suspend fun dispatchToController(
    controller: PlaybackController,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,   // injectable for DispatchToControllerTest
    command: (PlaybackController) -> Unit,
) {
    withContext(mainDispatcher) {
        controller.connect()    // needed (withConnectedController no-ops with no future) — but NOT sufficient
        command(controller)     // queues on the buildAsync future if not yet connected (cold-tap-safe)
    }
}

class PreviousActionCallback : ActionCallback {
    override suspend fun onAction(c: Context, id: GlanceId, p: ActionParameters) =
        withController(c, id) { it.previous() }
}
class NextActionCallback : ActionCallback {
    override suspend fun onAction(c: Context, id: GlanceId, p: ActionParameters) =
        withController(c, id) { it.next() }
}
class PlayPauseActionCallback : ActionCallback {
    override suspend fun onAction(c: Context, id: GlanceId, p: ActionParameters) =
        withController(c, id) { it.togglePlayPause() }
}
class SkipBack15ActionCallback : ActionCallback {
    override suspend fun onAction(c: Context, id: GlanceId, p: ActionParameters) {
        // Pre-tap position snapshot. Only accurate because PlaybackService also writes the holder on
        // onPositionDiscontinuity (seeks) — the 1s tick alone runs only while playing (see Step 7).
        val s = WidgetEntryPoint.get(c).widgetStateProvider().current()
        val target = clampSeekPosition(s.positionMs, -15_000L, s.durationMs)
        withController(c, id) { it.seekTo(target) }
    }
}
class SkipForward15ActionCallback : ActionCallback {
    override suspend fun onAction(c: Context, id: GlanceId, p: ActionParameters) {
        val s = WidgetEntryPoint.get(c).widgetStateProvider().current()
        val target = clampSeekPosition(s.positionMs, 15_000L, s.durationMs)
        withController(c, id) { it.seekTo(target) }
    }
}

// Pure, unit-testable boundary math (no Context/Controller): clamp position+delta into [0, duration].
internal fun clampSeekPosition(currentPositionMs: Long, deltaMs: Long, durationMs: Long): Long {
    if (durationMs <= 0) return 0L
    return (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
}
```

```kotlin
// WidgetEntryPoint.kt — Glance instantiates callbacks reflectively, so no constructor injection.
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun playbackController(): PlaybackController   // owned by audio-engineer; call it, don't reimplement it
    fun widgetStateProvider(): WidgetStateProvider
    fun artworkLoader(): WidgetArtworkLoader
    companion object {
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}
```
> If a transport command you need isn't on `PlaybackController`, request it from audio-engineer — don't reach into ExoPlayer from the widget.

### Step 6: Create the Receiver and Provider Metadata
```kotlin
// MusicWidgetReceiver.kt
class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
```
```xml
<!-- res/xml/music_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp" android:minHeight="110dp"
    android:targetCellWidth="4" android:targetCellHeight="2"
    android:minResizeWidth="180dp" android:minResizeHeight="90dp"
    android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"                                  <!-- push-driven, never polled -->
    android:description="@string/widget_description"
    android:previewImage="@drawable/widget_preview"
    android:initialLayout="@layout/glance_default_loading_layout" />
```
```xml
<!-- feature/widget/src/main/AndroidManifest.xml (inside <application>) -->
<!-- exported MUST be true: the launcher (a different UID) delivers APPWIDGET_UPDATE. The APPWIDGET_*
     action family is platform-protected, so this is not an attack surface. exported="false" leaves
     the widget un-addable / never updating. -->
<receiver android:name=".MusicWidgetReceiver" android:exported="true" android:label="RollaMusicPlayer">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data android:name="android.appwidget.provider" android:resource="@xml/music_widget_info" />
</receiver>
```

### Step 7: Push Updates via the `PlaybackUpdateHook` seam (NOT a direct service→widget call)
**Goal**: Keep the widget in sync in real time, without `:core:media` ever depending on `:feature:widget`.

**Why not just call `MusicWidget().updateAll(context)` from the service?** Because `MusicWidget` lives in `:feature:widget`, and `:core:media` importing it would invert the module dependency direction (feature → core is the only legal arrow). The seam is how the service refreshes the widget without knowing the widget exists.

**`:core:media` side (owned by audio-engineer — you rely on it, you don't edit it):**
```kotlin
fun interface PlaybackUpdateHook { fun onPlaybackStateChanged() }

@Module @InstallIn(SingletonComponent::class)
abstract class PlaybackUpdateModule {
    @Multibinds abstract fun bindPlaybackUpdateHooks(): Set<PlaybackUpdateHook>   // legally empty if no feature contributes
}

// PlaybackUpdateDispatcher iterates the set, exception-contained. PlaybackService calls it AFTER
// writing PlaybackStateHolder, off the player callback thread:
//   onMediaItemTransition: setCurrentSong(...); setDurationMs(...); serviceScope.launch { dispatcher.dispatch() }
//   onIsPlayingChanged:    setIsPlaying(...);                       serviceScope.launch { dispatcher.dispatch() }
//   ~1s position tick:     setPositionMs(...) [only while playing]; serviceScope.launch { dispatcher.dispatch() }
//   onPositionDiscontinuity (seeks, incl. while paused; auto-transitions):
//                          setPositionMs(newPosition.positionMs);   serviceScope.launch { dispatcher.dispatch() }
// Without the discontinuity write, a paused seek left the holder stale: the bar didn't move and
// repeated widget ±15s taps all computed their target from the same old position.
```

**`:feature:widget` side (yours):**
```kotlin
// WidgetModule.kt — contribute the hook into the set; bind the state provider.
@Module @InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds @Singleton abstract fun bindWidgetStateProvider(impl: WidgetStateProviderImpl): WidgetStateProvider
    @Binds @IntoSet   abstract fun bindPlaybackUpdateHook(impl: WidgetPlaybackUpdateHook): PlaybackUpdateHook
}

// WidgetPlaybackUpdateHook.kt — coalesce fires so a burst → at most one queued render.
@Singleton
class WidgetPlaybackUpdateHook @Inject constructor(
    @ApplicationContext private val context: Context,
    @WidgetIoDispatcher ioDispatcher: CoroutineDispatcher,
) : PlaybackUpdateHook {
    private val refreshRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    init {
        CoroutineScope(SupervisorJob() + ioDispatcher).launch {
            refreshRequests.collect {
                try { MusicWidget().updateAll(context) }
                catch (e: CancellationException) { throw e }
                catch (ignored: Exception) { /* a failed render self-corrects on the next fire */ }
            }
        }
    }
    override fun onPlaybackStateChanged() { refreshRequests.tryEmit(Unit) }  // never blocks the service
}
```
The hook needs no explicit state hand-off: `WidgetStateProvider` reads the holder, and the service writes the holder *before* firing. `replay = 1` retains a fire that races the collector's startup; `DROP_OLDEST` collapses bursts to one queued render (latest-state-wins).

**What the hook actually does now:** `updateAll` *starts* a Glance session when none is alive (e.g. after ~45s of pause) and keeps a live one alive while playing. It does **not** repaint a live session by itself — Glance only re-reads its own `GlanceStateDefinition` on update, never `provideGlance`. The live repaint comes from the composition's `collectAsState` on `WidgetStateProvider.states` (Step 4). Both halves are required.

### Step 8: Verify
**Checklist**:
- [ ] Widget renders artwork, title/artist, progress bar, all controls
- [ ] Widget shows the **actually-playing** song and refreshes on track change / play-pause / progress (it reads `PlaybackStateHolder` and the composition collects `states`)
- [ ] Progress bar **keeps moving for minutes** of continuous playback (a frozen bar = state read outside `provideContent`)
- [ ] Play/pause, prev, next, ±15s all **affect playback with the app already open** (each callback goes through `dispatchToController` → main thread). Test warm, not just the cold first tap — the cold tap works even with the threading bug
- [ ] Repeated ±15s taps **while paused** accumulate and move the bar (service writes position on `onPositionDiscontinuity`)
- [ ] Resizing the widget taller/wider fills the card (artwork grows, controls spread) — no dead space at the bottom
- [ ] Tapping the body opens the app
- [ ] Real-time, throttled, coalesced updates via the `PlaybackUpdateHook` seam (no polling)
- [ ] Works in airplane mode (local artwork only)
- [ ] Large artwork downscaled — no crash
- [ ] Correct dark-card styling via `RollaWidgetColors`
- [ ] `updatePeriodMillis="0"`; receiver `exported="true"`
- [ ] No `:core:media` → `:feature:widget` dependency; no retained Context/Player references

## Troubleshooting (symptom → cause → fix)

**Widget shows the wrong song, an old song, or "Nothing playing" while music plays.**
- Cause: the widget is reading a widget-owned copy of state instead of `PlaybackStateHolder`, or the service writes the holder *after* firing the hook.
- Fix: make `WidgetStateProviderImpl` read `PlaybackStateHolder` directly; ensure `PlaybackService` writes the holder before `dispatcher.dispatch()`. Do NOT add a widget-side state cache.

**Buttons do nothing (the body tap still opens the app).**
- Cause (the real v1.0 bug): the command ran on Glance's `Dispatchers.Default` callback thread. Once `PlaybackController` is connected (any time the app has been opened), `withConnectedController` calls `MediaController` inline, which throws `IllegalStateException("MediaController method is called from a wrong thread")`. Glance catches it and only logs it.
- Confirm: `adb logcat -s GlanceAppWidget` shows `BroadcastReceiver execution failed` / the wrong-thread message on each tap.
- Fix: route every callback through `dispatchToController` (Step 5) so `connect()` and the command run on `Dispatchers.Main.immediate`. Calling `connect()` first is required too (no future → `withConnectedController` no-ops), but on its own it does NOT fix this — the original code already called `connect()` and the buttons were still dead.

**Progress bar / play-pause icon / title frozen while music plays.**
- Cause: state read in `provideGlance` *before* `provideContent`. `update()`/`updateAll()` don't re-run `provideGlance` while the session is alive, and the 1s tick keeps it alive for the whole playback.
- Fix: `collectAsState` on `WidgetStateProvider.states` inside `provideContent` (Step 4). `current()` only seeds the first frame.

**±15s works once while paused, then repeated taps don't move further; the bar doesn't move after a paused seek.**
- Cause: the holder's position is only written by the 1s tick, which runs only while playing.
- Fix: `PlaybackService` writes `setPositionMs` + dispatches on `onPositionDiscontinuity` (Step 7).

**Progress bar looks missing early in a track.**
- Cause: the track color in `MusicWidgetTheme.kt` drifted from `colors.xml` (old `#3A3A3C` ≈ 1.5:1 against the card).
- Fix: keep `WidgetTrack = #6E6E73` and `WidgetPrimary = #4780FF` in sync with `colors.xml` / `:core:designsystem`. Note pre-API-31 Glance ignores progress tint colors entirely (platform default colors render).

**Lots of empty space at the bottom of the widget.**
- Cause: default `SizeMode.Single` (LocalSize = provider minimum) and fixed-height children.
- Fix: `SizeMode.Exact`, size artwork from `LocalSize.current`, give the header `defaultWeight()`, and put each control in an equal `defaultWeight()` cell (Step 4).

**Widget never updates during playback (but shows the right song when re-added).**
- Cause: `WidgetPlaybackUpdateHook` isn't contributed (`@Binds @IntoSet` missing), `:app` doesn't depend on `:feature:widget`, or `PlaybackService` never calls `dispatcher.dispatch()`.
- Fix: verify the `@IntoSet` binding, the app dependency, and the three dispatch call sites. Never add polling.

**Widget crashes when a song has large embedded art.**
- Cause: full-resolution bitmap parceled through RemoteViews (`TransactionTooLargeException`).
- Fix: route through `AlbumArtworkCache` + two-pass `BitmapFactory` decode with `inSampleSize` (~256px target).

**Compile error: `:core:media` can't see `MusicWidget`.**
- Cause: someone tried to call the widget from the service directly.
- Fix: use the `PlaybackUpdateHook` seam (Step 7). `:core:media` must never import `:feature:widget`.

## Related Files
- `feature/widget/.../MusicWidget.kt` — GlanceAppWidget
- `feature/widget/.../MusicWidgetContent.kt` — UI
- `feature/widget/.../MusicWidgetActions.kt` — control callbacks (+ `dispatchToController`, `clampSeekPosition`)
- `feature/widget/src/test/.../DispatchToControllerTest.kt` — proves `connect()` + command run on the main dispatcher
- `feature/widget/src/test/.../WidgetStateProviderTest.kt` — `current()` mapping + live `states` emissions
- `feature/widget/.../MusicWidgetState.kt` — render snapshot
- `feature/widget/.../WidgetStateProvider.kt` — read-only adapter over `PlaybackStateHolder`
- `feature/widget/.../WidgetArtworkLoader.kt` — local bitmap decode/downscale via `AlbumArtworkCache`
- `feature/widget/.../WidgetEntryPoint.kt` — Hilt entry point
- `feature/widget/.../WidgetModule.kt` — `@Binds` provider + `@Binds @IntoSet` hook
- `feature/widget/.../WidgetPlaybackUpdateHook.kt` — coalesced `updateAll`
- `feature/widget/.../MusicWidgetTheme.kt` — `RollaWidgetColors`
- `feature/widget/src/main/res/xml/music_widget_info.xml` — provider metadata
- `feature/widget/src/main/AndroidManifest.xml` — receiver registration
- `:core:media` — `PlaybackStateHolder`, `PlaybackController`, `PlaybackUpdateHook`, `PlaybackUpdateDispatcher`, `PlaybackUpdateModule`

## Notes
- The widget is a thin mirror of the playback service — never give it its own ExoPlayer, and never give it its own writable state store.
- Read `PlaybackStateHolder` and observe it inside the composition; call `PlaybackController` (`connect()` + command) on the main thread via `dispatchToController`; implement `PlaybackUpdateHook`.
- Glance swallows exceptions thrown from `ActionCallback`s — a "dead button" is usually a logged exception, so check `adb logcat -s GlanceAppWidget` before guessing.
- Unit tests can't see Glance session lifetime or `MediaController` threading; always verify the widget on a device, with the app already open, across several minutes of playback.
- Artwork must be local and downscaled; widgets have a strict RemoteViews memory budget.
- Push updates through the hook seam; never poll with `updatePeriodMillis`.
- Coalesce fires (`replay = 1`, `DROP_OLDEST`) to protect battery.
- Use the widget `GlanceTheme(colors = RollaWidgetColors)` only — request a role mapping from m3-design-system-agent if one is missing.
- Glance receivers/callbacks can't use constructor injection — use `WidgetEntryPoint` (`EntryPointAccessors`).
- Visual style per `.claude/rules/ui-style-guide.md` §8.
