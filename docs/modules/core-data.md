---
type: module
status: active
gradle: ":core:data"
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
- 2026-11-08: `SettingsRepository` KDoc points at `docs/history/v1-phases/PHASE6-SETTINGS.md` (vault move).
- 2026-11-08 (per-track artwork Task 6): the per-album cache is deleted (`AlbumArtworkCache`, its impl and dir
  qualifier, `ArtworkDecoder`, `BitmapArtworkDecoder`, their test and `DataModule` bindings). `SongArtworkCache` is the
  only artwork cache; no Kotlin code reads MediaStore's `audio/albumart` any more.
