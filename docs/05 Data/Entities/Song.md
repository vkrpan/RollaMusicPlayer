---
type: entity
status: active
date: 2026-11-13
table: songs
model: "core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Song.kt"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/SongEntity.kt"
tags: [data, entity]
related: ["[[Database Schema]]", "[[Glossary]]", "[[Model vocabulary]]", "[[Data Flow]]"]
---

# Song

One audio file in the library. Code says `Song`, the UI says "track" ([[Song in code, Tracks on screen]]).

## Fields (`songs`)
| Column | Type | Null | Meaning |
|---|---|---|---|
| `id` | TEXT | no | MediaStore `_ID` as text; the song's identity everywhere (routes take it as `Long`) |
| `media_store_id` | INTEGER | no | the same id as a number, indexed for the scan's diff |
| `title`, `artist`, `album` | TEXT | no | from MediaStore; a missing artist is the literal `<unknown>` ([[Unknown artists show as Unknown]]) |
| `album_id` | INTEGER | no | MediaStore album id ([[Album]]) |
| `duration_ms` | INTEGER | no | |
| `track_number` | INTEGER | yes | MediaStore `TRACK` (disc × 1000 + track) |
| `year` | INTEGER | yes | |
| `content_uri` | TEXT | no | the `content://` uri playback uses |
| `artwork_uri` | TEXT | no | written by the scan but **never read**: the model derives the artwork value |
| `date_modified` | INTEGER | no | change detection for the incremental scan; also the artwork version |
| `date_added` | INTEGER | no | first indexed; backs "Recently added" |
| `is_favorite`, `play_count`, `last_played` | INTEGER | no/no/yes | Favourites and the smart playlists ([[Playlists]]) |
| `folder_path` | TEXT | yes | folder relative to its volume, `""` = root; null until scanned after v4 ([[Folder]]) |
| `genre` | TEXT | yes | the file's genre once the file pass read it, else MediaStore's (API 30+); not in the model yet |
| `file_tags_read` | INTEGER | no | the file pass has read this file's `ROLLA_TAGS` and genre |
| `file_tags_generation` | INTEGER | no | bumped on each reset; a file-pass read applies only if it still matches |

## Model (`Song`)
`id`, `title`, `artist`, `album`, `albumId`, `durationMs`, `trackNumber`, `year`, `contentUri`, `artworkUri`.
`artworkUri` is `rolla-artwork://song/{id}?v={dateModified}`, derived in `toDomain` ([[Per-track artwork]]).

## Invariants
- `id` never changes for a file; a file MediaStore no longer lists is deleted from `songs` on the next sync, and its
  playlist and custom-tag links cascade away.
- `file_tags_read` is reset (and the generation bumped) whenever the file changes, so the file pass reads it again.

## Read and written by
- Written: `LibraryIndexer`, `FileTagPass` ([[core-data]]); play stats by playback ([[core-media]]).
- Read: every list and detail screen, Search, Find tracks, the queue ([[Home and library]], [[Search]], [[Custom tags]]).
- Custom tags it carries: [[CustomTag]]. Queue entries: [[SavedPlayback]].
