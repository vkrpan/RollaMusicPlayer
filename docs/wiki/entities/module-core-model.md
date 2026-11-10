---
type: entity
summary: "Pure-Kotlin domain models and enums: the shared contract every module imports."
aliases: [":core:model"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:model

Pure-Kotlin domain models and enums: the shared contract every module imports.

Pure Kotlin: `Song`, `Album`, `Artist`, `Folder`, `Playlist`, `EqualizerPreset`, `SavedPlayback`, `RepeatMode`,
`ShuffleMode`, and the per-track artwork value helpers.

- **Governed, not owned:** no single editor; any change needs code-reviewer plus the consuming agents
  ([[core-model-governance]]).
- **Vocabulary:** the audio item is `Song`, never `Track` ([[song-not-track]]).
- **Artwork:** only `Song.artworkUri` carries a picture; `Album`, `Artist` and `Folder` have none
  ([[per-track-artwork]]).
- **Depended on by:** every other module ([[multi-module-architecture]]).

## Related
[[claude-md]], [[ownership-map]], [[core-model-governance]], [[song-not-track]], [[per-track-artwork]], [[multi-module-architecture]]
