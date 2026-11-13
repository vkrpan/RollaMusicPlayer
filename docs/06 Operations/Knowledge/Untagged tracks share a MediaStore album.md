---
type: knowledge
status: active
date: 2026-11-08
source: "read-only probe on the Motorola (API 36), 2026-11-08"
tags: [mediastore, artwork, platform]
related: ["[[Knowledge]]", "[[Artwork comes from the track's own file]]", "[[Per-track artwork]]"]
---

# Untagged tracks share a MediaStore album

**Fact:** MediaStore files every track without an album tag under an album named after its **folder**, with one shared
`album_id`. On the Motorola, "sunrise house mix" and Blackwater are both album "Download"
(`album_id` 1540603544537963427).

**Consequences:**
- **One cover per album:** `content://media/external/audio/albumart/{albumId}` returns one picture per album, taken
  from one of its files. The "albumm" album returned Congo to Cuba's thumbnail byte for byte. A picture saved into one
  untagged file therefore became every untagged track's cover after a rescan.
- **A per-file route exists:** `content://media/external/audio/media/{id}/albumart` returns a different thumbnail per
  file. It is unverified what it returns for a file with no picture, and how long it stays stale after a write. The
  app does not use it.
- **The edit itself worked:** each file held its own single front cover (ID3v2.4 `APIC`, type 3).

**Use:** never derive a track's picture from its album. See [[Artwork comes from the track's own file]].
