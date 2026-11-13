---
type: feature
status: active
date: 2026-07-05
source: "v1 Phase 3 (`524f6e1`…`58b22e9`); One UI Phase 3"
tags: [equaliser, audio, feature]
related: ["[[Features]]", "[[Equaliser visualizer rejected]]", "[[Touch-target exceptions]]", "[[feature-equalizer]]", "[[core-media]]", "[[equalizer-agent]]"]
---

# Equaliser

- **Engine:** `EqualizerController` wraps `android.media.audiofx.Equalizer` on the ExoPlayer audio session. It maps
  the target frequencies to the nearest device band, clamps to `bandLevelRange`, is thread-safe and contains its own
  exceptions.
- **Lifecycle:** it re-applies the persisted state when the session id changes and is always released on destroy.
- **State:** the active state lives in DataStore; named presets live in Room (v3).
- **UI:** an enable card, vertical spring sliders with value and frequency labels, a preset chip grid and its
  caption, and a gains-driven response curve ([[Equaliser visualizer rejected]]).
- **UX calls:** "Custom" is a no-op chip; long-press deletes a user preset with no confirm
  ([[One UI redesign decisions]]).
- **Copy:** "Equaliser" in the UI, `Equalizer*` in code.
- **Modules:** [[feature-equalizer]] (UI), [[core-media]] (engine). **Owner:** [[equalizer-agent]].
  **Skill:** `implement-equalizer`.
