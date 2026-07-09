# Phase 6 — Settings

Adds the settings screen: grouped rounded cards (`ui-style-guide.md` §6 Settings) backed by **DataStore**,
wiring preferences that reactively drive the rest of the app — theme, playback options, the equalizer
entry point, a library rescan, and a privacy/about section that reflects the offline-first design.

Branch: `git checkout -b feature/settings`. One prompt at a time → ✅ checkpoint → commit. Follow the
`implement-datastore` skill; build UI from `:core:designsystem` tokens. `:feature:settings` is owned by
ui-builder + viewmodel-architect; DataStore/preferences by data-layer-agent.

---

## Step 0 — Re-sync (no code)

```
Starting settings on branch feature/settings. Read CLAUDE.md, OWNERSHIP.md, ui-style-guide.md §6
(Settings Screens), and the implement-datastore skill. Inspect :core:datastore, :core:media, and the app
root (where RollaMusicPlayerTheme is applied) and report: (1) which preferences already exist in
SettingsDataStore/SettingsRepository (theme mode? equalizer active state?); (2) what playback parameters
:core:media can accept (playback speed, crossfade, skip-silence, gapless); (3) where the root theme reads
themeMode today; (4) confirm the Equalizer route exists so Settings can link to it. Don't write code —
list the settings to add and where each one takes effect.
```
✅ **Checkpoint:** inventory of existing prefs + where each new setting will actually apply.

## Prompt 1 — Settings preferences (`:core:datastore`)

```
Use the data-layer-agent with the implement-datastore skill. CREATE SettingsDataStore / SettingsRepository
(neither exists yet — Step 0 confirmed) reusing the existing shared "settings" Preferences DataStore
instance (the internal Context.dataStore delegate in :core:datastore) — do NOT create a second DataStore
file. All settings as Flows following EqualizerPreferences' pattern (.catch IOException -> emptyPreferences,
transactional edits):
- Appearance: themeMode (system/light/dark), useDynamicColor (default false).
- Playback: playbackSpeed (default 1.0), skipSilence (default false),
  resumeOnHeadsetConnect (default false — OPTIONAL STRETCH, skip to keep the phase lean).
- Library: (no stored value needed for a one-shot rescan action).
Expose typed getters + update functions. Unit-test the DataStore codec. Build.
```
✅ **Checkpoint:** all settings persist and expose as Flows; DataStore test passes.
`git commit -m "feat(core-datastore): app settings preferences"`

## Prompt 2 — Apply playback settings (`:core:media`)

```
Use the audio-engineer agent. In :core:media, observe the relevant SettingsRepository Flows and apply
them to ExoPlayer reactively: playbackSpeed → Player.setPlaybackSpeed (a Player API — works through the
MediaController); skipSilence → exoPlayer.skipSilenceEnabled — an ExoPlayer-ONLY API, so it must be
applied inside PlaybackService observing the preference (the same binding pattern as the equalizer
state), not from the controller. resumeOnHeadsetConnect (only if the optional stretch was kept) →
receiver in the service. All offline, off the main thread. Both persisted settings re-apply on service
start. Build and verify changing a setting takes effect during playback.
```
✅ **Checkpoint:** changing playback speed/skip-silence/etc. affects live playback.
`git commit -m "feat(core-media): apply playback settings from DataStore"`

## Prompt 3 — Apply theme settings (`:app` + `:core:designsystem`)

```
Use the ui-builder and m3-design-system-agent. Make the app root (MainActivity, above the NavHost)
observe themeMode + useDynamicColor from SettingsRepository and pass them into
RollaMusicPlayerTheme(darkTheme = when(mode){ SYSTEM -> isSystemInDarkTheme(); LIGHT -> false;
DARK -> true }, dynamicColor). Changing the theme setting must recompose the whole app live. Build and
verify light/dark/system + dynamic toggle. (Dark background is already true-black #000000 per
ui-style-guide §2 — there is no pureBlack toggle in this phase.)
```
✅ **Checkpoint:** theme + dynamic-color settings change the app appearance immediately.
`git commit -m "feat(app): theme driven by settings"`

## Prompt 4 — Settings screen (`:feature:settings`)

```
Use the ui-builder and viewmodel-architect agents. Build SettingsScreen per ui-style-guide §6 (Settings):
grouped rounded `large` cards under sectionHeader-styled headers (Appearance · Playback · Library ·
Privacy · About). Row types:
- Toggle row: label (+ optional sub-label) + M3 Switch (primary ON) — dynamic color (row shown ONLY on
  Android 12+ / API 31+, where it does something), skip-silence, resume-on-connect (if kept).
- Value/navigation row: label + blue value (primary) or chevron — theme mode (opens a picker dialog),
  Equalizer (navigates; value text On/Off from EqualizerPreferences.enabled), Rescan library (action),
  Privacy & permissions, About/Licenses.
- Slider row: label + centered value — playback speed (0.5x–2.0x, e.g. "1.0x"), primary active track.
SettingsViewModel exposes the settings state and update calls that persist via the repository. Tokens only;
48dp targets; content descriptions. Build.
```
✅ **Checkpoint:** every control reflects and updates its stored setting; grouped cards match the style guide.
`git commit -m "feat(feature-settings): settings screen"`

## Prompt 5 — Navigation + entry points (`:app`)

```
Use the navigation-agent. Add type-safe routes: Settings, and sub-routes About and Licenses. Register
:feature:settings' nav entry. Wire the top-bar overflow (⋮) → Settings, and the Settings "Equalizer" row
→ the existing Equalizer route (which pops back to any caller). Explicit back behavior throughout. Build
and verify navigation.
```
✅ **Checkpoint:** overflow opens Settings; the Equalizer row opens the EQ and returns; sub-screens navigate.
`git commit -m "feat(app): settings routes + entry points"`

## Prompt 6 — Privacy, About, Rescan (`:feature:settings`)

```
Use the ui-builder + media-scanning-agent. Add:
- Privacy section: a plain statement that the app is fully offline and collects/transmits nothing, plus a
  read-only summary of the one permission used (audio read). No toggles that imply data collection.
- About: app version/build (from BuildConfig) and an open-source Licenses screen rendered from a LOCAL,
  bundled license list (no network fetch).
- Rescan library: an action row that triggers the media-scanning-agent's LibraryIndexer.sync() with
  progress feedback.
All local/offline. Build.
```
✅ **Checkpoint:** privacy/about read correctly; licenses render offline; rescan updates the library.
`git commit -m "feat(feature-settings): privacy, about, rescan"`

## Prompt 7 — Review gate + tests

```
Run code-reviewer, compose-performance-auditor, and test-writer in parallel. Fix all CRITICAL/HIGH:
offline/no-network (no analytics, no online license/version fetch), DataStore never read on the main
thread, settings actually propagate to playback/theme, no hardcoded visuals bypassing :core:designsystem /
ui-style-guide.md. Coverage: SettingsRepository/DataStore tests, SettingsViewModel tests (Turbine), and a
test proving a setting change emits through to its consumer. Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH; `./gradlew check` green; settings persistence + propagation covered.
`git commit -m "test(feature-settings): coverage + review fixes"`

Then merge `feature/settings` and update CLAUDE.md "Current Status".

---

## Notes & gotchas
- **Rejected (recorded like the Visualizer rejection): crossfade** — ExoPlayer has no crossfade; it would
  require a dual-player mixing architecture, a large risky build for marginal value in an offline player —
  **and the gapless toggle** — gapless is automatic in ExoPlayer with no API to switch, so a switch would
  be a fake control. **pureBlack/AMOLED toggle also dropped** — dark background is already #000000, the
  toggle would be a no-op (revisit only if default dark ever moves to an elevated surface). The principle:
  **no decorative toggles** — a settings screen full of switches that don't do anything is worse than a
  short one where everything works.
- **Settings drive features reactively.** Each preference is a Flow the consumer collects — theme in the
  app root, playback params in `:core:media`. Avoid reading a one-off value; observe it so changes apply live.
- **Dynamic color stays off by default** (ui-style-guide §2) — the brand blue is the default accent; dynamic
  is an opt-in toggle.
- **Privacy section must stay honest.** It states the app collects nothing — so don't add anything that
  would contradict it (no analytics toggle, no crash-reporting opt-in).
- **Licenses offline.** Generate/bundle the OSS license list at build time; never fetch it — that would
  add a network dependency and fail `checkNoNetwork`.
- **Theme change = whole-app recomposition** — read themeMode above the NavHost so it re-themes everything.

## Next after settings
`:feature:widget` (widget-agent, Glance home-screen widget per ui-style-guide §8 with the 15s-skip
controls, driven by PlaybackState), then baseline-profile + accessibility polish. Tell me "settings done"
and I'll write the widget phase.
```
