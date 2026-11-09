---
type: module
status: active
gradle: ":core:ui"
owners: ["[[ui-builder]]"]
tags: [module]
related: ["[[Modules]]", "[[Home and library]]", "[[Per-track artwork]]", "[[Build UI from tokens and the kit]]"]
---

# core-ui

`:core:ui` — Model-aware shared composables: `SongListItem`, `AlbumRow`, `ArtistRow`, `FolderRow`, `PlaylistRow` on `ListRowScaffold`, `AlbumCard`, `FeatureCard`, `DetailArtworkHeader`, `ArtworkThumbnail`, `SongOptionsHost`, `SongSelectionState`, `tracksCountLabel`.

- **Owner agent(s):** [[ui-builder]]
- **Features:** [[Home and library]], [[Per-track artwork]]
- **Decisions and rules:** [[Build UI from tokens and the kit]]

## Changes
- 2026-11-08 (per-track artwork Task 5): `AlbumCard`, `AlbumRow`, `ArtistRow`, `FolderRow` and `DetailArtworkHeader` take no artwork and always
  draw the placeholder (new shared `PlaceholderThumbnail`); `SongListItem`'s thumbnail gets `SONG_ROW_ARTWORK_TEST_TAG`.
  `SongListItem.trackNumber` / `TrackNumberLabel` now have no production caller (backlog).
