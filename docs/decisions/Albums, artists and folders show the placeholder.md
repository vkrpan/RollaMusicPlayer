---
type: decision
status: active
date: 2026-11-08
source: "user, 2026-11-08 (picker answer \"Always the white note\"); spec 2026-11-08-per-track-artwork §3 goal 3"
tags: [artwork, library, ui]
related: ["[[Decisions]]", "[[Per-track artwork]]", "[[Artwork comes from the track's own file]]", "[[Home and library]]"]
---

# Albums, artists and folders show the placeholder

**Decision:** groups never show a cover. That covers album cards and rows, the album and artist page headers, the
artist page's album strip, artist rows, folder rows, and Search's album and artist results. Each shows the white-note
placeholder.
- The `artworkUri` field leaves `Album`, `Artist` and `Folder`, so this holds by construction.
- The Playlists tab's feature cards stay track-based: four tiles from the collection's first four tracks
  (Claude's call, accepted with the spec).

**Why:** a group has no picture of its own once pictures belong to files; picking one track's picture would bring back
the "wrong cover" problem.

**Overrules:** Phase 4a's "`Artist.artworkUri` is the first album's cover".
