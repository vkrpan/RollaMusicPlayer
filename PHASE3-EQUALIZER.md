# Phase 3 — Equalizer

Adds the 8-band graphic equalizer: the `audiofx.Equalizer` effect bound to the ExoPlayer **audio session**
(equalizer-agent owns the effect in `:core:media`; audio-engineer provides the session id), a preset
system (built-ins + user presets in Room), **active state persisted via DataStore**, and the sliders +
preset-chip screen per `ui-style-guide.md` §6.

Branch: `git checkout -b feature/equalizer`. One prompt at a time → ✅ checkpoint → commit. Follow the
`implement-equalizer` and `implement-datastore` skills; build UI from `:core:designsystem` tokens.

---

## Step 0 — Re-sync (no code)

```
Starting the equalizer on branch feature/equalizer. Read CLAUDE.md, OWNERSHIP.md, and .claude/rules/ +
the implement-equalizer and implement-datastore skills. Inspect :core:media, :core:model, :core:database,
and :core:datastore and report: (1) does the playback service expose audioSessionId (and emit
onAudioSessionIdChanged)? (2) is there an EqualizerPreset model in :core:model and an EqualizerPresetEntity
/ DAO in :core:database yet? (3) is DataStore set up (from implement-datastore) and where does active
settings live? Also state the device Equalizer reality: it typically exposes ~5 bands, while our target
labels are 40Hz–10kHz — recommend the exact frequency→device-band mapping. Don't write code — give me the
plan and any migration needed.
```
✅ **Checkpoint:** confirmed session-id hook, current preset/DataStore state, and a concrete band-mapping plan.

## Prompt 1 — Effect controller (`:core:media`, equalizer-agent)

```
Use the equalizer-agent (coordinate the session hook with audio-engineer per media3-playback). In
:core:media/equalizer implement EqualizerController wrapping android.media.audiofx.Equalizer:
- attach(audioSessionId): create the effect bound to the session; release the previous one first.
- capabilities(): bandLevelRange (millibel) + numberOfBands.
- setEnabled(Boolean); setGainForFrequency(hz, gainMillibel) mapping each of the 8 target frequencies to
  the nearest device band via getBand(hz * 1000); clamp gains to bandLevelRange.
- release(): mandatory on teardown.
Document the target-frequency → device-band mapping (device usually < 9 bands). Do NOT create a second
player — bind only to the existing session. Unit-test the mapping/clamp logic with a fake. Build.
```
✅ **Checkpoint:** controller attaches to a session, applies clamped gains, releases cleanly; mapping documented.
`git commit -m "feat(core-media): EqualizerController bound to audio session"`

## Prompt 2 — Persistence: active state (DataStore) + presets (Room)

```
Use the equalizer-agent + data-layer-agent with implement-datastore and add-room-database.
- Active state via DataStore (:core:datastore): EqualizerPreferences storing enabled + gains (millibel
  list), Flow-based, IOException-safe. This is what restores the effect on next launch.
- Named presets in Room (:core:database): EqualizerPresetEntity (id, name, is_custom, gains, created_at)
  + DAO; bump MusicDatabase version with a TESTED migration (no data loss). Provide the built-in presets
  (Balanced, Bass boost, Smooth, Dynamic, Clear, Treble boost) as code constants (or seed once); user
  presets are is_custom = true.
- EqualizerRepository: observePresets(): Flow<List<EqualizerPreset>>, savePreset(name, gains),
  deletePreset; currentSettings()/saveSettings() delegating to DataStore. Map entities ↔ :core:model. Build.
```
✅ **Checkpoint:** migration test passes; active state persists via DataStore; presets CRUD works.
`git commit -m "feat: equalizer persistence — active state (DataStore) + presets (Room)"`

## Prompt 3 — Bind to lifecycle + restore (`:core:media`)

```
Use the equalizer-agent + audio-engineer. Wire the effect to the player lifecycle: on
onAudioSessionIdChanged, attach the controller and re-apply the persisted EqualizerPreferences (enabled +
gains) — re-apply is required because the session id can change. Release the effect on service destroy.
Keep the apply work off the player callback thread. Build and verify the effect survives a track change.
```
✅ **Checkpoint:** saved EQ restores on launch and persists across track changes; released on teardown.
`git commit -m "feat(core-media): attach/re-apply/release equalizer over player lifecycle"`

## Prompt 4 — Equalizer screen (`:feature:equalizer`)

```
Use the ui-builder and viewmodel-architect agents. Build EqualizerScreen per ui-style-guide §6
(Equalizer Screen):
- A surfaceContainer card holding the vertical band sliders: value label above each (metadata/labelSmall
  token), frequency label below (the app's 8 target labels), active track = primary, inactive =
  sliderInactiveTrack, circular thumb.
- An enable/disable toggle for the whole EQ (Switch, primary ON).
- A 2-column pill chip grid of presets (built-ins + user + "Custom"): selected = primary filled /
  onPrimary text; unselected = surfaceContainerHigh / onSurface. Caption (bodySmall) describes the
  selected preset.
EqualizerViewModel exposes EqualizerSettings + presets; setEnabled / setBandGain(index, gain) /
selectPreset / saveCurrentAsPreset — each applies to EqualizerController in real time AND persists via the
repository. Tokens only; 48dp targets. Build.
```
✅ **Checkpoint:** moving a band audibly changes output live; presets apply; enable toggles the effect; state persists.
`git commit -m "feat(feature-equalizer): sliders + preset grid screen"`

## Prompt 5 — Navigation + entry points (`:app`)

```
Use the navigation-agent. Add a type-safe Equalizer route (navigation-conventions.md, no args). Wire the
now-playing equalizer (bars) action → Equalizer screen, and add an entry from Settings later. Give it
explicit back behavior. Build and verify navigation both ways.
```
✅ **Checkpoint:** the now-playing EQ button opens the screen; back returns correctly.
`git commit -m "feat(app): equalizer route + now-playing entry"`

## Prompt 6 — Vertical slider polish (+ optional visualization)

```
Use the ui-builder and compose-animation-agent agents. Refine the vertical gain slider: smooth drag,
snap() during drag / spring on release, animated fill, and a larger thumb while dragging (graphicsLayer,
60fps, reduced-motion aware, ui-style-guide §9).
Optional real-time visualization: if adding android.media.audiofx.Visualizer, first confirm with
code-reviewer whether it triggers a RECORD_AUDIO prompt on target devices — if it conflicts with the
privacy positioning, use a decorative animation driven by band gains instead of audio capture. Build.
```
✅ **Checkpoint:** sliders feel good on a real device; visualization decision made and documented.
`git commit -m "feat(feature-equalizer): vertical slider polish + visualization"`

## Prompt 7 — Review gate + tests

```
Run code-reviewer, compose-performance-auditor, and test-writer in parallel. Fix all CRITICAL/HIGH:
offline/no-network, the effect is ALWAYS released on teardown (no leaked AudioEffect), settings re-applied
after session-id change, gains clamped to bandLevelRange, no hardcoded visuals bypassing
:core:designsystem / ui-style-guide.md. Coverage: EqualizerController mapping/clamp (fake), migration
test, EqualizerRepository + DAO tests, DataStore active-state test, EqualizerViewModel tests (Turbine).
Re-run ./gradlew check until green.
```
✅ **Checkpoint:** no CRITICAL/HIGH; `./gradlew check` green; controller + persistence + ViewModel covered.
`git commit -m "test(feature-equalizer): coverage + review fixes"`

Then merge `feature/equalizer` and update CLAUDE.md "Current Status".

---

## Notes & gotchas
- **Session, not player.** The effect binds to `audioSessionId`. Never build a second ExoPlayer. If EQ
  "does nothing", the session id is usually invalid/unattached or the effect isn't `enabled`.
- **Re-apply after `onAudioSessionIdChanged`** — the session can change; without re-apply the EQ appears to reset.
- **Units:** gains are millibel (1 dB = 100 mB); `getBand()` takes milliHz (Hz × 1000); always clamp to `bandLevelRange`.
- **Bands:** the device likely exposes fewer than 9 bands — map the 8 target labels to nearest device bands and
  document it; don't pretend the hardware has 9 independent bands.
- **Active state vs presets:** enabled + current gains live in DataStore (restore on launch); named snapshots
  live in Room. Keep them separate (per implement-equalizer).
- **Release is mandatory** — a leaked `Equalizer`/`AudioEffect` causes glitches and resource warnings; it's in
  the equalizer-agent + code-reviewer definition of done.

## Next after equalizer
`:feature:tageditor` (tag-editor-agent: ID3 read/write with scoped-storage write consent + batch edit, then
re-index) — tell me "equalizer done" and I'll write that phase.
```
