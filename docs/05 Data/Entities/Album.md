---
type: entity
status: active
date: 2026-11-13
table: "none (GROUP BY songs.album_id)"
model: "core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Album.kt"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/AlbumDao.kt"
tags: [data, entity, derived]
related: ["[[Database Schema]]", "[[Song]]", "[[Home and library]]"]
---

# Album

Songs grouped by MediaStore `album_id`. No table of its own: `AlbumDao` queries `songs`.

## Fields (`Album`)
| Field | Meaning |
|---|---|
| `id` | the real MediaStore `album_id`; the `AlbumDetail` route argument ([[Navigation routes]]) |
| `title`, `artist` | from the songs in the group |
| `songCount` | |

## Invariants and gotchas
- An album shows the placeholder, never a picture ([[Albums, artists and folders show the placeholder]]); its rows
  show each track's own picture.
- Untagged files in one folder can share an album id ([[Untagged tracks share a MediaStore album]]).

## Read by
Albums tab, `AlbumDetail`, Search ([[feature-library]], [[feature-search]]).
