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
- 2026-11-09 (custom tags phase 1, Task 4): **v6** (`MIGRATION_5_6`): tables `custom_tags` (PK kind+id, unique
  `(kind, name_key)`), `custom_tag_counters` (per-kind highest ID ever issued; only rises, via `INSERT OR REPLACE …
  MAX(…)` because API 24's SQLite has no upsert), `song_custom_tags` (FK `song_id` → `songs.id` ON DELETE CASCADE; no
  FK to `custom_tags`, so unknown IDs are kept); `songs` gains `genre` and `file_tags_read`. `CustomTagDao` (interface
  with `@Transaction` defaults: `insertNew`, `applyFileTags`). `FakeCustomTagDao` in [[core-testing]]. The instrumented
  tests (10 DAO + the 5→6 migration) compile; their device run is owed (Motorola not connected). See [[Custom tags]].
- 2026-11-10 (final fix wave): `CustomTagDao.insertNew` refuses (`IllegalStateException`) instead of wrapping past
  `MAX_CUSTOM_TAG_ID`; new instrumented tests `insertNew_atCeiling_throwsAndInsertsNothing` and
  `applyFileTags_onDeletedSong_rollsBackAndKeepsCounter`.
- 2026-11-10: device run on the Motorola: `:core:database:connectedDebugAndroidTest` 117/117, every planned mutation
  caught; the v6 upgrade ran on the user's real database without trouble.
- 2026-11-10 (custom tags phase 2, Task 1): **v7** (`MIGRATION_6_7`): `songs.file_tags_generation` (bumped by the
  merge whenever a file changed or a re-read is forced). `CustomTagDao.applyFileTags` and `markFileTagsRead` take
  `expectedGeneration` and return `Boolean`: they write nothing when the row was reset since it was queried (or is
  gone), so a stale background read never overwrites a fresh save. Device: 121/121. The phase 1 test
  `applyFileTags_onDeletedSong_rollsBackAndKeepsCounter` is now `..._isSkippedAndKeepsCounter` (the check returns
  before any write). The `@Transaction` on `applyFileTags`/`markFileTagsRead` is load-bearing (the generation check and
  the writes must not interleave with the scanner's upsert); their KDoc says so.
- 2026-11-11 (custom tags phase 3, Task 1): `CustomTagDao` gains `deleteCustomTag` (entry and links; the counter
  stays), `replaceAll` (lists replaced, links kept, counters only rise), link-count queries (unknown IDs counted) and
  `insertNew` returning `null` at the ceiling (the last-ID race). No schema change (still v7). Device: 124/124.
- 2026-11-12 (custom tags phase 4, Task 1): `CustomTagDao.observeAllSongCustomTagLinks()` (every link, unknown IDs
  included). No schema change (v7). Device: 125/125.
