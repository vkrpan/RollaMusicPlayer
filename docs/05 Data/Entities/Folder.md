---
type: entity
status: active
date: 2026-11-13
table: "none (GROUP BY songs.folder_path)"
model: "core/model/src/main/kotlin/com/rolla/musicplayer/core/model/Folder.kt"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/dao/FolderDao.kt"
tags: [data, entity, derived]
related: ["[[Database Schema]]", "[[Song]]", "[[Home and library]]"]
---

# Folder

Every folder that directly holds songs, from `songs.folder_path` (Room v4).

## Fields (`Folder`)
| Field | Meaning |
|---|---|
| `path` | relative to its storage volume, `/`-separated, no leading or trailing slash (`Music/Chill`); `""` is the root. **The identity** |
| `name` | the last segment, or "Internal storage" for the root; display only |
| `songCount` | |

## Invariants and gotchas
- Routes and list keys use `path` (`FolderDetail(folderPath)`; an empty path is a valid argument,
  [[Navigation routes]]).
- Rows scanned before v4 hold null until the next sync fills them.
- A folder always shows the placeholder.

## Read by
Folders tab, `FolderDetail` ([[feature-library]]).
