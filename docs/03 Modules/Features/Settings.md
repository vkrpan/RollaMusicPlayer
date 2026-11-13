---
type: feature
status: active
date: 2026-07-10
source: "v1 Phase 6 (`1b1a4a9`…`36b5366`); One UI Phase 3"
tags: [settings, feature]
related: ["[[Features]]", "[[Settings toggles rejected]]", "[[No decorative toggles]]", "[[feature-settings]]", "[[core-datastore]]"]
---

# Settings

- **Settings:**
  - theme mode, and dynamic colour (API 31+, opt-in; the accent stays brand-stable by default);
  - playback speed, and skip silence (ExoPlayer-only API);
  - manual rescan, with progress and a result message (it also clears the per-track artwork cache, `SongArtworkCache`);
  - Privacy, About and Licenses screens, all local.
- **What is not there:** [[Settings toggles rejected]].
- **Storage:** the shared "settings" DataStore ([[core-datastore]]); `SettingsRepository` clamps speed on both
  sides.
- **Theme:** settings drive the theme through `MainViewModel`, including edge-to-edge bar contrast.
- **Module:** [[feature-settings]]. **History:** `docs/08 Project/History/v1-phases/PHASE6-SETTINGS.md`.
