---
type: entity
summary: "The 8-band equaliser screen: sliders, presets and the response curve."
aliases: [":feature:equalizer"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :feature:equalizer

The 8-band equaliser screen: sliders, presets and the response curve.

The equaliser screen: band sliders, presets and a gains-driven response curve. There is no live `Visualizer`, which
would need `RECORD_AUDIO` ([[offline-privacy-architecture]]). The effect itself lives in [[module-core-media]]. UI copy says
"Equaliser"; code says `Equalizer*`.

- **Owner:** ui-builder (screen); equalizer-agent owns the effect ([[agent-module-ownership]]).

## Related
[[claude-md]], [[ownership-map]], [[offline-privacy-architecture]], [[module-core-media]], [[agent-module-ownership]]
