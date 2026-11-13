# Phase 1 — Player (Mini-Player + Now-Playing)

Phase 0 is done: the vertical slice (scan → list → play, offline, reviewed) works. This phase adds the
**persistent mini-player pill** and the **full Now-Playing screen**, built from the `:core:designsystem`
tokens and `ui-style-guide.md` (§6 components, §7 blueprint, §9 motion).

Work on a branch: `git checkout -b feature/player`. One prompt at a time → ✅ checkpoint → commit.

---

## Step 0 — Re-sync (no code)

```
We're starting the player feature on branch feature/player. Read CLAUDE.md, OWNERSHIP.md, and
.claude/rules/ (ui-style-guide.md, model-vocabulary.md, media3-playback.md). Inspect :core:media and
report the current PlaybackController API and whether the service already exposes playback STATE
(current Song, isPlaying, position/duration, shuffle/repeat) as an observable Flow. Don't write code —
tell me what exists and the exact gaps the player UI will need.
```
✅ **Checkpoint:** clear picture of the `:core:media` playback API and what state is/ isn't exposed.

## Prompt 1 — Expose playback state (`:core:media`)

```
Use the audio-engineer agent and the media3-playback rules. In :core:media expose an observable
PlaybackState as a Flow/StateFlow: currentSong: Song?, isPlaying, positionMs, durationMs, shuffleMode,
repeatMode. Progress updates must be throttled to ~1s (not per-frame). Add shuffle + repeat controls to
PlaybackController (setShuffle, cycleRepeatMode) and a seekTo(positionMs). Keep everything off the main
thread. Build.
```
✅ **Checkpoint:** `:core:media` builds; PlaybackState Flow + shuffle/repeat/seek available; progress throttled.
`git commit -m "feat(core-media): observable PlaybackState + shuffle/repeat/seek"`

## Prompt 2 — Mini-player pill (`:core:ui` + `:feature:player`)

```
Use the ui-builder and viewmodel-architect agents. Build the persistent mini-player per ui-style-guide
§6 (Mini-Player): a pill using the miniPlayerContainer token, full radius, 8dp side margins, ~64dp tall,
sitting above the navigation-bar inset. Left: circular album art (Coil, implement-image-loading-coil).
Center: title (songTitle token) + artist (artistName token), ellipsized. Right: prev · play/pause · next
· queue icons (24dp, onSurface). A MiniPlayerViewModel exposes PlaybackState and forwards control calls
to PlaybackController. Show it on the library screen when a song is loaded; hide when idle. Tap body is a
no-op stub for now (navigation comes next). Tokens only — no hardcoded visuals. Build.
```
✅ **Checkpoint:** mini-player renders over the library, reflects playback, controls work.
`git commit -m "feat(feature-player): persistent mini-player pill"`

## Prompt 3 — Now-Playing screen (`:feature:player`)

```
Use the ui-builder and viewmodel-architect agents. Build NowPlayingScreen per ui-style-guide §6
(Now-Playing) and §7 blueprint:
- Top bar: collapse chevron (down) left; right actions: volume, equalizer (bars) [stub], overflow.
- Centered large artwork (extraLarge shape, Coil).
- Centered title (nowPlayingTitle token) + artist (artistName token).
- Action row: queue/lyrics · ♥ favorite [stub] · + add [stub].
- Seekbar: primary active track, sliderInactiveTrack inactive, circular thumb, time labels (metadata
  token) both ends; dragging seeks via PlaybackController.seekTo (debounced).
- Transport row: shuffle · prev · Play (large) · next · repeat, bound to PlaybackController.
PlayerViewModel exposes PlaybackState. Wrap in RollaMusicPlayerTheme. Tokens only. Build.
```
✅ **Checkpoint:** Now-Playing renders to spec; seek + transport + shuffle/repeat all work.
`git commit -m "feat(feature-player): Now-Playing screen"`

## Prompt 4 — Navigation wiring (`:app`)

```
Use the navigation-agent. Add a NowPlaying route (navigation-conventions.md: type-safe @Serializable,
no args — the screen reads PlaybackState). Register :feature:player's nav entry in the :app NavHost.
Wire: tapping the mini-player body navigates to NowPlaying; the chevron collapses back. NowPlaying is a
multi-entry destination (mini-player now, notification later) — give it explicit popUpTo/launchSingleTop
behavior per navigation-conventions.md. Build and verify navigation both ways.
```
✅ **Checkpoint:** mini-player → Now-Playing → back works with correct back-stack behavior.
`git commit -m "feat(app): NowPlaying route + mini-player navigation"`

## Prompt 5 — Shared-element transition (motion)

```
Use the compose-animation-agent (ui-style-guide §9). Implement the mini-player → Now-Playing transition:
shared-element on the album artwork + crossfade of the metadata, 200–300ms, spring. Respect the
reduced-motion preference. Profile to confirm 60fps and no dropped frames; use graphicsLayer for
transforms. Coordinate with compose-performance-auditor. Build.
```
✅ **Checkpoint:** smooth shared-element artwork transition, 60fps, reduced-motion respected.
`git commit -m "feat(feature-player): shared-element mini-player to now-playing transition"`

## Prompt 6 — Review gate + tests

```
Run code-reviewer, compose-performance-auditor, and test-writer in parallel over the player feature.
Fix all CRITICAL/HIGH: offline/no-network, memory leaks (no retained player/context), recomposition
scope, missing keys, main-thread work, and any hardcoded color/type/shape bypassing :core:designsystem /
ui-style-guide.md. Add unit tests for PlayerViewModel and MiniPlayerViewModel (Turbine for the
PlaybackState flow). Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH; `./gradlew check` green; player ViewModels tested.
`git commit -m "test(feature-player): player + mini-player coverage; review fixes"`

Then merge `feature/player` and update CLAUDE.md "Current Status".

---

## After the player — remaining feature order

Same loop each time (skill → owning agent → build → review gate → commit), each on its own branch, all
from design tokens + `ui-style-guide.md`:

1. **`:feature:playlists`** — feature cards (Recently played / Favourites) + playlist rows; create/rename/
   reorder (drag-and-drop). Needs PlaylistEntity/DAO (data-layer-agent) + repository.
2. **`:feature:equalizer`** — equalizer-agent effect bound to the audioSessionId in `:core:media` +
   screen (sliders card + 2-col preset chip grid, §6); persist active state via implement-datastore.
3. **`:feature:search`** — local search over the library (Room FTS or filtered query).
4. **`:feature:tageditor`** — tag-editor-agent (scoped-storage write consent, batch edit), then re-index.
5. **`:feature:settings`** — grouped cards (§6 Settings), DataStore-backed (theme, playback options).
6. **`:feature:widget`** — widget-agent, Glance, §8 + 15s-skip controls, driven by PlaybackState.
7. **Polish** — generate-baseline-profile (startup + library scroll), confirm setup-static-analysis runs
   in CI, accessibility pass (TalkBack, 48dp targets, content descriptions).

Suggested order rationale: player first (you have it now), then playlists (core library UX), then the
audio-differentiators (equalizer, tag editor), then search/settings, then widget, then performance polish.
```
