---
type: decision
status: active
date: 2026-10-27
source: "One UI Phase 7; `.claude/rules/model-vocabulary.md` 'Display rule: unknown artists'"
tags: [copy, library, model]
related: ["[[Decisions]]", "[[Artists are identified by name]]", "[[UI copy and spelling]]", "[[Model vocabulary]]"]
---

# Unknown artists show as Unknown

**Decision:**
- MediaStore stores a missing artist as the literal `<unknown>`.
- Every place that shows an artist name passes it through `artistDisplayName(raw)` (`:core:model`, `ArtistNames.kt`).
  That returns "Unknown" for a blank name or `<unknown>`, and the raw name otherwise.
- Data, keys and routes keep the raw value (`ArtistDetail(artistName = "<unknown>")`).
- Playback metadata carries the display name; the raw name rides in the media item's extras (`MediaMetadata.rawArtist()`).
- **Exception:** the tag editor shows the file's own tags.

**Open:** "Unknown" still sorts under `#` in the Artists tab, because the raw key sorts (ruling P9 kept it; [[Backlog]]).
