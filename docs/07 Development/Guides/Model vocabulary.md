---
type: guide
status: active
source: ".claude/rules/model-vocabulary.md"
tags: [guide, vocabulary, model]
related: ["[[Guides]]", "[[Song in code, Tracks on screen]]", "[[Unknown artists show as Unknown]]", "[[core-model]]"]
---

# Model vocabulary

**Source of truth:** `.claude/rules/model-vocabulary.md` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- `Song`, never `Track`; leave `trackNumber`, `AudioTrack`, `TRACK_TYPE_*`, `currentTracks` alone.
- UI copy says "Tracks"; code says `Song`.
- `artistDisplayName` shows "Unknown"; data keeps the raw `<unknown>`.
- A folder's identity is its path; an artist's is its raw name.
