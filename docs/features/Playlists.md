---
type: feature
status: active
date: 2026-07-04
source: "v1 Phase 2 (`c532e58`…`55c4b01`); One UI Phases 4a, 6a"
tags: [playlists, feature]
related: ["[[Features]]", "[[feature-playlists]]", "[[Per-track artwork]]"]
---

# Playlists

- **Smart playlists:** Recently played, Favourites, Most played. They come from play-count tracking and the favourite
  toggle, and show as feature cards with a four-tile art collage.
- **User playlists:**
  - create, rename and delete;
  - add and remove tracks;
  - key-based drag-and-drop reorder (reduced-motion aware), with "Move up"/"Move down" accessibility actions;
  - DAO transactions keep positions intact (append/remove/compact);
  - stored in Room (v2 migration).
- **Tabs:** Favourites and Playlists are Home tabs. PlaylistDetail and SmartPlaylist are pushed screens.
- **Card collages:** the first four tracks' own pictures, unfiltered; a track without one shows the placeholder tile
  ([[Per-track artwork]], Task 5).
- **Module:** [[feature-playlists]].
