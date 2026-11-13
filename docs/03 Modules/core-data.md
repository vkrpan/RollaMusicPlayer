---
type: module
status: active
gradle: ":core:data"
code: "core/data"
owners: ["[[data-layer-agent]]", "[[media-scanning-agent]]"]
tags: [module]
related: ["[[Modules]]", "[[Home and library]]", "[[Per-track artwork]]", "[[Album artwork disk cache]]", "[[Artwork comes from the track's own file]]"]
---

# core-data

`:core:data` — Repositories and the MediaStore scanner (`MediaScanner`, `LibraryIndexer`: incremental sync, mutex-serialised manual rescan, targeted `syncSongs`), the artwork disk cache (per-album today, per-track after [[Per-track artwork]]).

- **Owner agent(s):** [[data-layer-agent]], [[media-scanning-agent]]
- **Features:** [[Home and library]], [[Per-track artwork]]
- **Decisions and rules:** [[Album artwork disk cache]], [[Artwork comes from the track's own file]]

## Changes
- 2026-11-08 (per-track artwork Task 1): `MediaScanner.readSongs` writes the per-track value
  (`songArtworkUri(id, dateModified)`) into the `artwork_uri` column; `MediaStoreColumns.ALBUM_ART_URI` is deleted.
- 2026-11-08 (per-track artwork Task 2): `artwork/SongArtworkCache` + `SongArtworkCacheImpl` (files
  `{songId}-{version}.jpg`, zero-byte `.none` markers for "no picture", 32 MiB budget, temp-file + rename publish),
  `EmbeddedArtworkDecoder` / `RetrieverArtworkDecoder` (`MediaMetadataRetriever.getEmbeddedPicture()`, 512 px, JPEG
  85; `calculateInSampleSize` moved here from `BitmapArtworkDecoder`). `DataModule` binds it with
  `@SongArtworkCacheDir` = `cacheDir/song_art`, alongside the album cache until Task 6.
- 2026-11-08: `SettingsRepository` KDoc points at `docs/08 Project/History/v1-phases/PHASE6-SETTINGS.md` (vault move).
- 2026-11-08 (per-track artwork Task 6): the per-album cache is deleted (`AlbumArtworkCache`, its impl and dir
  qualifier, `ArtworkDecoder`, `BitmapArtworkDecoder`, their test and `DataModule` bindings). `SongArtworkCache` is the
  only artwork cache; no Kotlin code reads MediaStore's `audio/albumart` any more.
- 2026-11-10 (custom tags phase 1, Task 5): the scan reads `ROLLA_TAGS` and the genre from files. `FileTagPass`
  (batches of 50, newest `date_modified` first) runs on its own long-lived scope (`@FileTagPassScope`); `LibraryIndexer`
  calls the non-suspending `request()` after its upsert and returns at once (requests during a run coalesce into one
  more run; any Throwable per row marks the row read and is counted, never crashes; SDD rulings 7–8).
  `SongFileSource`/`AndroidSongFileSource` reads in place when the `DATA` path is readable, else copies to
  `cacheDir/file_tag_reads/` (always on API 29) and deletes the copy. `ScannedSong.toEntity` keeps `genre` and
  `file_tags_read` only for an unchanged file; `syncSongs` forces a re-read. MediaStore `GENRE` on API 30+ only. Log tag
  `FileTagPass`: `read N files (F failed) in T ms`. Depends on [[core-tagio]].
- 2026-11-10 (custom tags phase 1, Task 6): `CustomTagRepository` (observe lists in library order, create, rename,
  per-song IDs incl. unknown ones; names trimmed, 1–40 code points, unique per list ignoring case).
- 2026-11-10 (custom tags phase 2, Task 1): `ScannedSong.toEntity` bumps `fileTagsGeneration` on a changed file or a
  forced re-read; `FileTagPass` applies with the queried generation, and a skipped apply leaves the row unread for its
  own loop to read again (a row deleted mid-read counts as skipped).
- 2026-11-10 (final fix wave): `CustomTagNameResult.LimitReached` when the list's next ID would pass
  `MAX_CUSTOM_TAG_ID`; lists tie-break by name then ID, as playlists do; `rename` validates the name before the ID
  lookup (KDoc, Ruling 9).
- 2026-11-11 (custom tags phase 3, Task 1): `CustomTagRepository` gains `delete`, `songIdsCarrying`,
  `observeLinkCounts`, `exportLists`, `previewImport` (`ImportClash(kind, id, oldName, newName, songCount)`),
  `applyImport` (validates names and uniqueness with the repository's own rules) and `reserveIds`; `create` maps a full
  list to `LimitReached` (the DAO returns null instead of throwing).
- 2026-11-12 (custom tags phase 4, Task 1): `CustomTagRepository.observeSongCustomTags()` (song ID → known
  `SongCustomTags`, library order; `@Suppress("TooManyFunctions")`), and `SongFinder.observeMatches(Flow<SongFilter>)`
  (in-memory AND filter over the library and the links, library order, live on link changes; bound in `DataModule`).
