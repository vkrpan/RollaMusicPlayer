---
type: decision
adr: "ADR-007"
status: superseded
superseded_by: "[[Artwork comes from the track's own file]]"
date: 2026-07-15
source: "v1 Phase 8 (`7a61bd3`); CLAUDE.md shipped table"
tags: [artwork, cache]
related: ["[[Decisions]]", "[[Per-track artwork]]"]
---

# Album artwork disk cache

**Decision (v1.0):**
- `AlbumArtworkCache` kept one downscaled JPEG per **albumId** in app storage, decoded from MediaStore's
  `content://media/external/audio/albumart/{albumId}`.
- It was bounded at 32 MiB with write-age eviction, and wrote off the main thread under a mutex.
- A Coil interceptor served it; the widget shared it.
- It was invalidated on a manual rescan and when the tag editor embedded a cover.

**Superseded 2026-11-08:** per-album pictures were the cause of the shared-cover bug. The per-track cache in
[[Per-track artwork]] replaces it. It keeps the 32 MiB budget, the rename-to-publish write and the rule that a cache
hit never touches `lastModified`.
