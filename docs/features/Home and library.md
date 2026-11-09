---
type: feature
status: active
date: 2026-10-17
source: "One UI Phases 2, 4a, 4b, 5, 6a"
tags: [library, home, feature]
related: ["[[Features]]", "[[Home replaces the bottom navigation]]", "[[One library order]]", "[[Artists are identified by name]]", "[[Touch-target exceptions]]", "[[feature-library]]", "[[app]]"]
---

# Home and library

- **Home:**
  - the start destination: [[Home replaces the bottom navigation]];
  - six tabs: Favourites · Playlists · Tracks · Albums · Artists · Folders;
  - Tracks, Favourites, Playlists (user rows), Artists and Folders have the A–Z rail ([[One library order]]).
- **Tracks tab:**
  - **hold to select** tracks: the selection bar shows "N selected", ✕ and "Edit tags" (batch editor);
  - ⋮ next to "Edit tags": **Set artwork** (one picked image on every selected track) and **Remove artwork** (after
    "Remove artwork from N tracks?"), with consent, a result message, and only failed tracks left selected; the
    selection is frozen while the write runs ([[Set artwork for many tracks from the selection menu]], per-track
    artwork Task 8);
  - Back clears the selection.
- **Detail screens:**
  - AlbumDetail (`albumId`), ArtistDetail (`artistName`, [[Artists are identified by name]]) and FolderDetail
    (`folderPath`, `""` = volume root);
  - all reached from the Home tabs, Search and Now Playing's ⋮;
  - albums, artists and folders always show the placeholder; album page rows show each track's picture instead of
    its number ([[Albums, artists and folders show the placeholder]]).
- **Folders:** every folder that directly holds tracks (Room v4 `songs.folder_path`).
- **Data:** scanned from MediaStore into Room by `LibraryIndexer` (incremental sync, a mutex-serialised manual rescan).
- **Modules:** [[feature-library]], [[core-data]]. **Owners:** [[ui-builder]], [[media-scanning-agent]].
