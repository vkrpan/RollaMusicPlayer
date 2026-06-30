---
name: implement-home-widget
description: "Step-by-step workflow for building the RollaMusicPlayer home screen widget with Jetpack Glance — album artwork, progress/timeline bar, play/pause, previous/next, and 15-second skip controls — driven by local playback state and fully offline."
---

# Skill: Implement Home Screen Widget

## Overview
A comprehensive workflow for building RollaMusicPlayer's home screen widget using Jetpack Glance. The widget mirrors the playback service's local state on the launcher: album artwork, track title/artist, a visual timeline/progress bar, a play/pause toggle, previous/next, and 15-second skip back/forward. It is push-updated by the playback service and operates entirely offline (artwork from local bitmaps only).

## When to Use
- Building the home screen widget feature (Phase 6)
- Adding or changing widget controls or layout
- Wiring the playback service to update the widget in real time
- Fixing widget artwork, sizing, or update issues

## Prerequisites
- Playback service with MediaSession is implemented (audio-engineer) and exposes current track, isPlaying, position/duration, and the media action strings (play/pause, next, previous, ±15s)
- Hilt is configured (for `EntryPointAccessors` in the receiver)
- Material 3 theme tokens are available (m3-design-system-agent)
- Local album artwork is accessible (file path / MediaStore)
- Minimum SDK 24; widget targets the same min/target as the app

## Workflow Steps

### Step 1: Add Glance Dependencies
**Goal**: Configure the project for Glance App Widgets

**Actions**:
1. Add the Glance appwidget and Glance Material 3 dependencies
2. Sync project
3. Confirm no networking libraries are pulled in (offline constraint)

**Implementation**:
```kotlin
// build.gradle.kts (app module)
dependencies {
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
```

### Step 2: Define the Widget State Model
**Goal**: Represent exactly what the widget renders

**Actions**:
1. Create an immutable state class for the widget
2. Decide how the widget reads it — a service-pushed singleton holder is simplest for a single active player

**Implementation**:
```kotlin
// widget/WidgetState.kt
@Immutable
data class MusicWidgetState(
    val title: String = "",
    val artist: String = "",
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val artworkPath: String? = null   // local path / MediaStore uri string — never a URL
) {
    val progress: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

// A lightweight holder the service updates and the widget reads.
interface WidgetStateProvider {
    fun current(): MusicWidgetState
}
```

### Step 3: Load and Downscale Local Artwork
**Goal**: Get a memory-safe bitmap for the widget (no network, no oversized bitmaps)

**Actions**:
1. Decode the local artwork with `inSampleSize` to a small target size
2. Return a themed placeholder when artwork is missing

**Implementation**:
```kotlin
// widget/WidgetArtworkLoader.kt
object WidgetArtworkLoader {
    // Widgets have a tight RemoteViews memory budget — keep art small.
    private const val TARGET_PX = 256

    fun load(path: String?): Bitmap? {
        if (path.isNullOrBlank()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            val sample = calculateInSampleSize(bounds, TARGET_PX, TARGET_PX)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeFile(path, opts)
        } catch (e: Exception) {
            null // fall back to placeholder; never crash the widget
        }
    }

    private fun calculateInSampleSize(o: BitmapFactory.Options, reqW: Int, reqH: Int): Int {
        var sample = 1
        var (h, w) = o.outHeight to o.outWidth
        while (h / sample > reqH || w / sample > reqW) sample *= 2
        return sample
    }
}
```

### Step 4: Build the Glance Widget UI
**Goal**: Render artwork, metadata, progress bar, and controls with Material 3

**Actions**:
1. Implement `GlanceAppWidget.provideGlance`
2. Read current state, load artwork, lay out controls
3. Use `GlanceTheme` for all colors and content descriptions for accessibility

**Implementation**:
```kotlin
// widget/MusicWidget.kt
class MusicWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext, WidgetEntryPoint::class.java
        )
        val state = entryPoint.widgetStateProvider().current()
        val artwork = WidgetArtworkLoader.load(state.artworkPath)

        provideContent {
            GlanceTheme {
                WidgetContent(state, artwork)
            }
        }
    }

    @Composable
    private fun WidgetContent(state: MusicWidgetState, artwork: Bitmap?) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (artwork != null) {
                    Image(
                        provider = ImageProvider(artwork),
                        contentDescription = "Album artwork",
                        modifier = GlanceModifier.size(56.dp).cornerRadius(8.dp)
                    )
                } else {
                    Box(
                        modifier = GlanceModifier.size(56.dp).cornerRadius(8.dp)
                            .background(GlanceTheme.colors.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text("♪") }
                }
                Spacer(GlanceModifier.width(12.dp))
                Column(modifier = GlanceModifier.defaultWeight()) {
                    Text(
                        state.title.ifBlank { "Nothing playing" },
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurface)
                    )
                    Text(
                        state.artist,
                        maxLines = 1,
                        style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant)
                    )
                }
            }

            Spacer(GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = state.progress,
                modifier = GlanceModifier.fillMaxWidth()
            )
            Spacer(GlanceModifier.height(8.dp))

            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ControlButton(R.drawable.ic_skip_previous, "Previous", actionRunCallback<PreviousCallback>())
                ControlButton(R.drawable.ic_replay_15, "Skip back 15 seconds", actionRunCallback<SkipBack15Callback>())
                ControlButton(
                    if (state.isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
                    if (state.isPlaying) "Pause" else "Play",
                    actionRunCallback<PlayPauseCallback>()
                )
                ControlButton(R.drawable.ic_forward_15, "Skip forward 15 seconds", actionRunCallback<SkipForward15Callback>())
                ControlButton(R.drawable.ic_skip_next, "Next", actionRunCallback<NextCallback>())
            }
        }
    }

    @Composable
    private fun ControlButton(resId: Int, desc: String, onClick: Action) {
        Image(
            provider = ImageProvider(resId),
            contentDescription = desc,
            modifier = GlanceModifier.size(40.dp).padding(4.dp).clickable(onClick)
        )
    }
}
```

### Step 5: Implement Control Callbacks
**Goal**: Dispatch widget controls to the existing playback service (no second player)

**Actions**:
1. Create one `ActionCallback` per control
2. Route each to the playback controller / the service's existing media actions
3. Trigger a widget update after the action so UI reflects the new state

**Implementation**:
```kotlin
// widget/WidgetActionCallbacks.kt
class PlayPauseCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context).togglePlayPause()
        MusicWidget().update(context, glanceId)
    }
}

class SkipBack15Callback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context).seekBy(-15_000L)
        MusicWidget().update(context, glanceId)
    }
}

class SkipForward15Callback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context).seekBy(15_000L)
        MusicWidget().update(context, glanceId)
    }
}

class NextCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context).next(); MusicWidget().update(context, glanceId)
    }
}

class PreviousCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        controller(context).previous(); MusicWidget().update(context, glanceId)
    }
}

private fun controller(context: Context): PlaybackController =
    EntryPointAccessors.fromApplication(
        context.applicationContext, WidgetEntryPoint::class.java
    ).playbackController()
```

```kotlin
// widget/WidgetEntryPoint.kt
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun playbackController(): PlaybackController   // owned by audio-engineer
    fun widgetStateProvider(): WidgetStateProvider
}
```

> Note: `PlaybackController.seekBy/togglePlayPause/next/previous` are owned by audio-engineer. If they don't exist yet, request them — don't reach into ExoPlayer from the widget.

### Step 6: Create the Receiver and Provider Metadata
**Goal**: Register the widget with the system

**Actions**:
1. Create the `GlanceAppWidgetReceiver`
2. Add provider XML with `updatePeriodMillis="0"` (push-driven)
3. Declare the receiver in the manifest

**Implementation**:
```kotlin
// widget/MusicWidgetReceiver.kt
class MusicWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MusicWidget()
}
```

```xml
<!-- res/xml/music_widget_info.xml -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp"
    android:minHeight="110dp"
    android:targetCellWidth="4"
    android:targetCellHeight="2"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:updatePeriodMillis="0"
    android:previewLayout="@layout/music_widget_preview"
    android:initialLayout="@layout/glance_default_loading_layout" />
```

```xml
<!-- AndroidManifest.xml (inside <application>) -->
<receiver
    android:name=".widget.MusicWidgetReceiver"
    android:exported="false">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/music_widget_info" />
</receiver>
```

### Step 7: Push Updates from the Playback Service
**Goal**: Keep the widget in sync in real time, without polling

**Actions**:
1. From the playback service, update the shared `MusicWidgetState` on track change, play/pause, and a throttled progress tick
2. Call `MusicWidget().updateAll(context)` after each update
3. Reuse the player's existing throttled progress cadence (~1s) — never per frame

**Implementation**:
```kotlin
// In the playback service (audio-engineer coordinates this hook)
private fun refreshWidget() {
    widgetStateProvider.set(currentMusicWidgetState())   // update shared holder
    serviceScope.launch { MusicWidget().updateAll(applicationContext) }
}

// Call refreshWidget() from:
//  - onMediaItemTransition (track change)
//  - onIsPlayingChanged (play/pause)
//  - throttled progress tick (the same ~1s cadence used for the in-app seekbar)
```

### Step 8: Verify
**Goal**: Confirm correctness, offline operation, and stability

**Actions**:
1. Add the widget to the launcher; confirm artwork, metadata, progress, controls
2. Exercise every control; confirm it affects playback and the widget reflects it
3. Toggle airplane mode and repeat — must work identically
4. Test large album art (no crash), light/dark themes, and resizing
5. Confirm updates stop churning when paused (no battery drain)

**Checklist**:
- [ ] Widget renders artwork, title/artist, progress bar, all controls
- [ ] Play/pause, prev, next, ±15s all work and update the widget
- [ ] Tapping the body opens the app
- [ ] Real-time, throttled progress updates during playback
- [ ] Works in airplane mode (local artwork only)
- [ ] Large artwork downscaled — no crash
- [ ] Correct in light and dark themes
- [ ] `updatePeriodMillis="0"` — no polling
- [ ] No hardcoded colors; GlanceTheme used throughout
- [ ] No retained Context/Player references

## Related Files
- `widget/MusicWidget.kt` — GlanceAppWidget + UI
- `widget/MusicWidgetReceiver.kt` — receiver
- `widget/WidgetActionCallbacks.kt` — control callbacks
- `widget/WidgetEntryPoint.kt` — Hilt entry point
- `widget/WidgetState.kt` — state model + provider
- `widget/WidgetArtworkLoader.kt` — local bitmap decode/downscale
- `res/xml/music_widget_info.xml` — provider metadata
- `AndroidManifest.xml` — receiver registration

## Notes
- The widget is a thin mirror of the playback service — never give it its own ExoPlayer.
- Artwork must be local and downscaled; widgets have a strict RemoteViews memory budget.
- Push updates from the service; never poll with `updatePeriodMillis`.
- Throttle progress updates to the same cadence as the in-app seekbar to protect battery.
- Use `GlanceTheme` colors only — request a token from m3-design-system-agent if one is missing.
- Glance receivers can't use constructor injection — use Hilt `EntryPointAccessors`.
- All `PendingIntent`/action wiring must use `FLAG_IMMUTABLE`.
- Visual style per `.claude/rules/ui-style-guide.md` §8.

## Common Patterns

### Coalesced progress updates (service side)
```kotlin
// Reuse the existing throttled progress flow rather than a new timer
progressTicker            // emits ~every 1s while playing
    .distinctUntilChanged { a, b -> a.positionSec == b.positionSec }
    .onEach { refreshWidget() }
    .launchIn(serviceScope)
```

### Empty / nothing-playing state
```kotlin
// In WidgetContent, when state.title is blank:
//  - show placeholder artwork
//  - show "Nothing playing"
//  - render a disabled-looking control row (still tappable to open app)
```
