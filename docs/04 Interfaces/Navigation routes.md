---
type: interface
status: active
date: 2026-11-13
kind: navigation
code: "app/src/main/kotlin/com/rolla/musicplayer/navigation/Routes.kt"
tags: [interface, navigation]
related: ["[[Interfaces Overview]]", "[[Navigation conventions]]", "[[app]]", "[[navigation-agent]]"]
---

# Navigation routes

Type-safe `@Serializable` routes in `Routes.kt` (the source of truth), registered in `MainActivity`'s `NavHost`.
Rules for adding one: [[Navigation conventions]]. Owner: [[navigation-agent]].

## Routes
| Route | Arguments | Reached from |
|---|---|---|
| `Home` | — | start destination: six tabs as pager pages ([[Home replaces the bottom navigation]]) |
| `NowPlaying` | — | the mini-player |
| `Equalizer` | — | Now Playing, Settings |
| `PlaylistDetail` | `playlistId: Long` | Playlists tab |
| `SmartPlaylist` | `kind: String` | Playlists tab feature cards |
| `AlbumDetail` | `albumId: Long` (MediaStore `album_id`) | Albums tab, Search, Now Playing ⋮ |
| `ArtistDetail` | `artistName: String` (raw name is the identity) | Artists tab, Search, Now Playing ⋮ ([[Artists are identified by name]]) |
| `FolderDetail` | `folderPath: String` (`""` = volume root) | Folders tab |
| `TagEditor` | `songId: Long` | every "Edit tags" (`openTagEditor`) |
| `BatchTagEditor` | `songIds: List<Long>` | Tracks tab selection bar |
| `ManageCustomTags` | — | Home ⋮, the tag picker ([[Custom tags]]) |
| `Search` | `query: String? = null` | Home header, deep link |
| `FindTracks` | — | Tracks tab sort header |
| `Settings`, `About`, `Licenses`, `Privacy` | — | Home ⋮; the last three from Settings only |

## Deep link
- `rollamusic://search[?query=…]` → `Search` (`navDeepLink<Search>`; declared in `AndroidManifest.xml`). A missing or
  blank query opens Search idle, never crashes.

## Gotchas
- Never pass a full model through a route; the destination loads it by ID.
- A route type must not be a sealed class (kotlinx.serialization cannot cast it to a NavType).
