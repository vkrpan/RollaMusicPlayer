---
type: entity
summary: "Model-aware shared composables: song rows, album cards, list rows, the song options sheet."
aliases: [":core:ui"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:ui

Model-aware shared composables: song rows, album cards, list rows, the song options sheet.

Composables that know the models: `SongListItem`, `AlbumRow`/`ArtistRow`/`FolderRow`/`PlaylistRow`, `AlbumCard`,
`FeatureCard`, `DetailArtworkHeader`, `SongOptionsHost`, `SongSelectionState`. Built from [[module-core-designsystem]].

- **Owner:** ui-builder ([[agent-module-ownership]]).
- Groups (albums, artists, folders) draw the placeholder; song rows draw the track's own picture
  ([[per-track-artwork]]).

## Related
[[claude-md]], [[ownership-map]], [[module-core-designsystem]], [[agent-module-ownership]], [[per-track-artwork]]
