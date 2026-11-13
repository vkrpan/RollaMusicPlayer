---
type: entity
status: active
date: 2026-11-13
table: "playlists, playlist_songs"
model: "core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Playlist.kt"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/PlaylistEntity.kt"
tags: [data, entity]
related: ["[[Database Schema]]", "[[Playlists]]", "[[feature-playlists]]", "[[Song]]"]
---

# Playlist

A user playlist. Smart playlists (Recently added, Most played, Recently played, Favourites) are computed from
`songs` and are not stored ([[Playlists]]).

## Fields
| Column | Type | Null | Meaning |
|---|---|---|---|
| `playlists.id` | INTEGER | no | auto-generated |
| `playlists.name` | TEXT | no | |
| `playlists.created_at`, `updated_at` | INTEGER | no | epoch ms |
| `playlist_songs.playlist_id` | INTEGER | no | FK → `playlists.id`, cascade |
| `playlist_songs.song_id` | TEXT | no | FK → `songs.id`, cascade |
| `playlist_songs.position` | INTEGER | no | order within the playlist (reorderable) |
| `playlist_songs.added_at` | INTEGER | no | |

Model `Playlist`: `id`, `name`, `songCount`, `createdAt`, `updatedAt`.

## Invariants
- A song appears at most once per playlist (primary key `playlist_id` + `song_id`).
- Deleting a song or a playlist removes its links.

## Read and written by
[[feature-playlists]] through the playlist repository in [[core-data]]; the add-to-playlist sheet in [[core-ui]].
