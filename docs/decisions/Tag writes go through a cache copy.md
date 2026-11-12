---
type: decision
status: active
date: 2026-07-06
source: "v1 Phase 4 (tag editor); KDoc in `SongFileResolver`, `TagWriter`, `MediaWriteRequester`"
tags: [tag-editor, storage]
related: ["[[Decisions]]", "[[Tag editor]]", "[[feature-tageditor]]", "[[tag-editor-agent]]"]
---

# Tag writes go through a cache copy

**Decision:**
- Tags are read and written with an **offline jaudiotagger fork** (`com.github.Adonai`, local IO only), on a **copy**
  of the file in the app cache. The edited copy is streamed back over the song's `content://` uri with
  `openOutputStream(uri, "wt")`.
- Write consent per SDK:
  - **30+:** `MediaStore.createWriteRequest`;
  - **29:** catch `RecoverableSecurityException` and run the recovery prompt;
  - **≤28:** legacy `WRITE_EXTERNAL_STORAGE` (maxSdk 28), requested at the point of use.
- Nothing is written before consent.
- After a save, `TagSaveFinalizer` runs in order: a MediaStore scan, a targeted Room re-sync that keeps
  favourites and play counts, an artwork-cache invalidation, then a refresh of the now-playing metadata.
- jaudiotagger is set to `isPreserveFileIdentity = true` (commit in place) and, on a device, `isAndroid = true` (FLAC
  artwork decodes through `BitmapFactory`, not `javax.imageio`).

- Since custom tags phase 3 (2026-11-11), the single editor's save also makes a second, short-lived copy of the
  original to read its current `ROLLA_TAGS`, so a change made elsewhere since load (a Manage tags delete) is not undone
  by the load-time copy; that copy is deleted in a `finally`.

**Why:** in scoped storage, jaudiotagger's rename-based commit fails on a `content://`-only file even with consent.

**Rules out:** editing the media file in place; online metadata lookups.
