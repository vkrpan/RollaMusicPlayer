---
type: interface
status: active
date: 2026-11-13
kind: widget
code: "feature/widget/src/main/kotlin/com/rolla/musicplayer/feature/widget/MusicWidgetActions.kt"
tags: [interface, widget]
related: ["[[Interfaces Overview]]", "[[Home-screen widget]]", "[[feature-widget]]", "[[widget-agent]]", "[[Widget design rules]]"]
---

# Widget actions

The Glance home-screen widget's taps. Each control is its own `ActionCallback`; the widget is a thin mirror of the
playback service, not a second player.

| Tap | Callback | Does |
|---|---|---|
| Previous | `PreviousActionCallback` | `PlaybackController.previous()` |
| −15 s | `SkipBack15ActionCallback` | seek to position − 15 s, clamped to 0 |
| Play / pause | `PlayPauseActionCallback` | `PlaybackController.togglePlayPause()` |
| +15 s | `SkipForward15ActionCallback` | seek to position + 15 s, clamped to the duration |
| Next | `NextActionCallback` | `PlaybackController.next()` |
| The widget body | `actionStartActivity(MainActivity)` | opens the app |

## Implemented in
[[feature-widget]] → `MusicWidgetActions.kt`, `MusicWidgetContent.kt`; state from `PlaybackStateHolder`
([[core-media]]), artwork from `WidgetArtworkLoader` ([[Per-track artwork]]).

## Gotchas
- Glance runs `onAction` on `Dispatchers.Default`, but `MediaController` throws off the main thread and Glance only logs
  it. `dispatchToController` hops to the main thread for `connect()` and the command; without that every tap is dead.
- A tap on a cold process queues the command on the controller future; the first tap works.
- Right after a tap the widget can show the pre-tap state for one frame; the service's next push corrects it.
- The skill `implement-home-widget` and [[Widget design rules]] hold the rest.
