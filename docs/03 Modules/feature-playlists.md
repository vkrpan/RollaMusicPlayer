---
type: module
status: active
gradle: ":feature:playlists"
code: "feature/playlists"
owners: ["[[ui-builder]]", "[[viewmodel-architect]]"]
tags: [module]
related: ["[[Modules]]", "[[Playlists]]"]
---

# feature-playlists

`:feature:playlists` — Favourites and Playlists tabs, playlist detail, smart playlists, reordering, add-to-playlist.

- **Owner agent(s):** [[ui-builder]], [[viewmodel-architect]]
- **Features:** [[Playlists]]
- **Decisions and rules:** —

## Changes
- 2026-11-08 (per-track artwork Task 5): `PlaylistsViewModel` previews the first four tracks' artwork values unfiltered
  (`songs.take(4).map(Song::artworkUri)`).
