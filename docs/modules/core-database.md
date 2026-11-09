---
type: module
status: active
gradle: ":core:database"
owners: ["[[data-layer-agent]]"]
tags: [module]
related: ["[[Modules]]", "[[Playback and queue]]", "[[Playlists]]", "[[Search]]", "[[Playback queue restore]]", "[[Search uses LIKE, not FTS4]]"]
---

# core-database

`:core:database` — Room: entities, DAOs, migrations, `MusicDatabase` (v5). All migrations are in one `ALL_MIGRATIONS` list that `MigrationTest` replays. v2 playlists, v3 equaliser presets, v4 `songs.folder_path`, v5 the playback queue.

- **Owner agent(s):** [[data-layer-agent]]
- **Features:** [[Playback and queue]], [[Playlists]], [[Search]], [[Per-track artwork]]
- **Decisions and rules:** [[Playback queue restore]], [[Search uses LIKE, not FTS4]],
  [[Artwork comes from the track's own file]]

## Changes
- 2026-11-08 (per-track artwork Task 1): `SongEntity.toDomain()` derives `artworkUri =
  songArtworkUri(id, dateModified)` and ignores the `artwork_uri` column (read-time derivation, plan ruling R1: no
  Room migration, still v5). The group DAOs (albums, artists, folders, search) still select `artwork_uri` until Task 5
  removes those projections.
- 2026-11-08 (per-track artwork Task 5): `AlbumDao`, `ArtistDao`, `FolderDao`, `SearchDao` drop the `artwork_uri` projections and their
  representative-row `MIN(...)` picks; the Artist, Folder and artist-search queries are flattened (same columns, counts,
  grouping and order). Schema unchanged (v5; the column stays). The device DAO run (`connectedDebugAndroidTest`) is owed.
