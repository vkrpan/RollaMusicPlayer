---
type: entity
summary: "Pure-Kotlin domain models and enums: the shared contract every module imports."
aliases: [":core:model"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase1-plan]]"]
updated: 2026-11-11
---
# :core:model

Pure-Kotlin domain models and enums: the shared contract every module imports.

Pure Kotlin: `Song`, `Album`, `Artist`, `Folder`, `Playlist`, `EqualizerPreset`, `SavedPlayback`, `RepeatMode`,
`ShuffleMode`, the per-track artwork value helpers, and the custom tag names `CustomTagKind`, `CustomTag`,
`CustomTagIds`, `MAX_CUSTOM_TAG_ID` ([[custom-tags]]).

- **Governed, not owned:** no single editor; any change needs code-reviewer plus the consuming agents
  ([[core-model-governance]]).
- **Vocabulary:** the audio item is `Song`, never `Track` ([[song-not-track]]).
- **Artwork:** only `Song.artworkUri` carries a picture; `Album`, `Artist` and `Folder` have none
  ([[per-track-artwork]]).
- **Depended on by:** every other module ([[multi-module-architecture]]).

## Related
[[claude-md]], [[ownership-map]], [[core-model-governance]], [[song-not-track]], [[per-track-artwork]], [[multi-module-architecture]]
