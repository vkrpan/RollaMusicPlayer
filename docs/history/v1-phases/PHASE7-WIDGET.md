# Phase 7 — Home Screen Widget (Glance)

The final build feature: a Jetpack **Glance** home-screen widget mirroring playback — album artwork,
title/artist, a progress bar, and controls (previous · −15s · play/pause · +15s · next), per
`ui-style-guide.md` §8. It's a thin, offline view over the playback service: it dispatches to
`PlaybackController` (never its own player), pushes updates from the service (no polling), and uses
downscaled local bitmaps for artwork.

Branch: `git checkout -b feature/widget`. One prompt at a time → ✅ checkpoint → commit. Follow the
`implement-home-widget` skill; `:feature:widget` is owned by widget-agent.

> **Theme note (important):** Glance composes outside `RollaMusicPlayerTheme`, so the widget CANNOT read
> the app's `LocalRollaDarkTheme`-based semantic tokens (`miniPlayerContainer`, `sliderInactiveTrack`).
> Map the §8 roles to `GlanceTheme` colors (or widget-local colors matching the §2 palette). Coordinate
> the mapping with m3-design-system-agent so the widget stays on-brand.

---

## Step 0 — Re-sync (no code)

```
Starting the widget on branch feature/widget. Read CLAUDE.md, OWNERSHIP.md, ui-style-guide.md §8, and the
implement-home-widget skill. Inspect :core:media and report: (1) the PlaybackController surface — confirm
togglePlayPause/next/previous and how to do a ±15s skip (seekBy, or seekTo(position ± 15000) computed from
state); (2) does the service expose an observable PlaybackState AND is there a place to push widget
updates from (track change / play-pause / throttled progress tick)? (3) how album artwork is available
locally (path/uri) for a downscaled bitmap. Also confirm Glance isn't a dependency yet. Note: the widget
can't use the app's LocalRollaDarkTheme semantic tokens — it must use GlanceTheme. Don't write code — give
me the plan, the ±15s approach, and the service→widget update hook.
```
✅ **Checkpoint:** confirmed controller/skip approach, the service push hook, artwork source, and the GlanceTheme constraint.

## Prompt 1 — Glance dependency + widget state (`build-tooling` + `:feature:widget`)

```
Use the build-tooling-agent to add androidx.glance:glance-appwidget + androidx.glance:glance-material3 to
gradle/libs.versions.toml — confirm local/offline so checkNoNetwork still passes; no INTERNET permission.
Then, with the widget-agent, in :feature:widget define:
- MusicWidgetState (title, artist, isPlaying, positionMs, durationMs, artworkPath: String?) with a
  progress computed value.
- A WidgetStateProvider the playback service updates and the widget reads (a lightweight holder is fine
  for a single active player).
- WidgetEntryPoint (@EntryPoint @InstallIn(SingletonComponent)) exposing PlaybackController +
  WidgetStateProvider (Glance receivers can't use constructor injection). Build.
```
✅ **Checkpoint:** Glance deps added and vetted; widget state + Hilt entry point compile.
`git commit -m "build+feat(widget): Glance deps + widget state + entry point"`

## Prompt 2 — Artwork loader (`:feature:widget`)

```
Use the widget-agent. Implement WidgetArtworkLoader that decodes the local album art to a DOWNSCALED
Bitmap (BitmapFactory + inSampleSize, target ~256px) — do NOT use Coil here; widgets have a tight
RemoteViews memory budget. Return null → a themed placeholder on missing/failed art (never crash). Build.
```
✅ **Checkpoint:** artwork loads small; missing art falls back to a placeholder; no crash on large files.
`git commit -m "feat(widget): downscaled local artwork loader"`

## Prompt 3 — Glance widget UI (`:feature:widget`)

```
Use the widget-agent. Implement MusicWidget: GlanceAppWidget.provideGlance reads the current
MusicWidgetState (via WidgetEntryPoint) + loads the artwork, then renders per ui-style-guide §8 inside
GlanceTheme: dark rounded card; artwork (rounded 12dp) left; title (1 line) + artist stacked; a
LinearProgressIndicator (primary) for progress; a control row of white line icons —
previous · −15s · play/pause · +15s · next. Tap the body → actionStartActivity to the app / Now-Playing.
Map colors to GlanceTheme (NOT the app's LocalRollaDarkTheme tokens); keep it on-brand per §2. Content
descriptions on every control. Build.
```
✅ **Checkpoint:** widget renders artwork, metadata, progress, and the 5 controls, on-brand.
`git commit -m "feat(widget): Glance widget UI"`

## Prompt 4 — Control callbacks + receiver + manifest (`:feature:widget`)

```
Use the widget-agent. Implement one ActionCallback per control, each resolving PlaybackController via
EntryPointAccessors and then updating the widget:
- PlayPause → togglePlayPause; Previous → previous; Next → next.
- SkipBack15 → seek to (positionMs - 15000).coerceAtLeast(0); SkipForward15 → (positionMs + 15000)
  .coerceAtMost(durationMs). (Use seekBy if it exists.)
Add MusicWidgetReceiver (GlanceAppWidgetReceiver), the provider XML (res/xml, updatePeriodMillis="0" —
push-driven, resizable, preview), and register the receiver in AndroidManifest (exported=false, no
INTERNET). Use FLAG_IMMUTABLE for any PendingIntent. Build and add the widget to the launcher.
```
✅ **Checkpoint:** all five controls dispatch to the service and affect playback; widget added from launcher.
`git commit -m "feat(widget): control callbacks + receiver + provider"`

## Prompt 5 — Service push updates (`:core:media`)

```
Use the audio-engineer + widget-agent. From PlaybackService, update WidgetStateProvider and call
MusicWidget().updateAll(context) on: track change (onMediaItemTransition), play/pause (onIsPlayingChanged),
and a THROTTLED ~1s progress tick (reuse the existing seekbar progress cadence — never per frame). Keep
the push lightweight and off the callback thread. Build and verify the widget updates in real time as
playback progresses and stops churning when paused.
```
✅ **Checkpoint:** widget tracks playback live; no update storm when paused/idle.
`git commit -m "feat(core-media): push playback state to widget (throttled)"`

## Prompt 6 — Verify + review gate + tests

```
First verify ON DEVICE: add the widget; exercise every control; confirm real-time updates; toggle AIRPLANE
MODE and repeat (must work identically); test large album art (no crash); check light + dark; resize.
Then run code-reviewer, compose-performance-auditor, and test-writer in parallel. Fix all CRITICAL/HIGH:
no network / local artwork only, artwork downscaled (RemoteViews budget), no retained Context/Player in the
receiver/callbacks, throttled updates + updatePeriodMillis="0", controls dispatch to the service (no second
player), GlanceTheme used (no hardcoded colors), content descriptions present. Cover the testable logic:
WidgetStateProvider, the ±15s coerce math, and WidgetArtworkLoader downscaling. Re-run ./gradlew check.
```
✅ **Checkpoint:** works on-device in airplane mode; no CRITICAL/HIGH; `./gradlew check` green.
`git commit -m "test(widget): logic coverage + review fixes"`

Then merge `feature/widget` and update CLAUDE.md "Current Status".

---

## Notes & gotchas
- **Thin mirror, not a second player.** Controls dispatch to `PlaybackController`; the widget never holds
  an ExoPlayer. If a control does nothing, trace the callback → service action wiring (and `FLAG_IMMUTABLE`).
- **Downscale artwork.** Full-res bitmaps blow the RemoteViews budget (`TransactionTooLargeException`). Decode
  small with `inSampleSize`; never pass Coil output straight through.
- **Push, don't poll.** `updatePeriodMillis="0"`; the service triggers `updateAll` on state changes + a
  throttled ~1s tick. A polling interval drains battery.
- **GlanceTheme, not the app theme.** The widget can't read `LocalRollaDarkTheme`/the semantic token
  extensions — they're only valid under `RollaMusicPlayerTheme`. Map §8 roles to `GlanceTheme` colors and
  keep the mapping on-brand (coordinate with m3-design-system-agent).
- **No constructor injection in the receiver.** Use Hilt `EntryPointAccessors.fromApplication(...)`.
- **±15s from state.** Compute `seekTo` from the current `positionMs`/`durationMs` and coerce into range
  (or use `seekBy` if the controller exposes it).

## Next — you're feature-complete after this
All Phase 1–7 features done. Remaining is **polish**: `generate-baseline-profile` (startup + library scroll),
confirm `setup-static-analysis` runs in CI, and an accessibility pass (TalkBack, 48dp targets, content
descriptions, contrast). Then release prep via the `release-build` skill. Tell me "widget done" and I'll
write the polish/release phase.
```
