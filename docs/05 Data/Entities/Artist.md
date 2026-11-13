---
type: entity
status: active
date: 2026-11-13
table: "none (GROUP BY songs.artist)"
model: "core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Artist.kt"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/ArtistDao.kt"
tags: [data, entity, derived]
related: ["[[Database Schema]]", "[[Song]]", "[[Artists are identified by name]]"]
---

# Artist

Songs grouped by their raw artist name. No table: `songs` has no `artist_id`.

## Fields (`Artist`)
| Field | Meaning |
|---|---|
| `id` | a synthetic FNV-1a hash of the name, display-only |
| `name` | the raw name: **the identity**, used by `ArtistDetail(artistName)` and list keys |
| `albumCount`, `songCount` | |

## Invariants and gotchas
- Identity is the name ([[Artists are identified by name]]); never key on `id`.
- Show names through `artistDisplayName()`: blank or `<unknown>` reads "Unknown", data keeps the raw value
  ([[Unknown artists show as Unknown]]). "Unknown" currently sorts under `#` ([[Known Issues]]).

## Read by
Artists tab, `ArtistDetail`, Search ([[feature-library]], [[feature-search]]).
