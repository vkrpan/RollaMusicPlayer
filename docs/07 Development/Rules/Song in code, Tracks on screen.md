---
type: rule
status: active
date: 2026-10-06
source: "`.claude/rules/model-vocabulary.md`; user decision 2026-10-06 (spec §2)"
tags: [vocabulary, ui, copy]
related: ["[[Rules]]", "[[Model vocabulary]]", "[[UI copy and spelling]]", "[[core-model]]"]
---

# Song in code, Tracks on screen

**Rule:** the audio-item model is **`Song`**, never `Track` (`song`, `songs`, `songId`, `SongListItem`, routes, tests,
logs). User-facing strings say **"Tracks"** (the Home tab, counts, sheet and button copy) and never "Songs".

**Why:** one shared vocabulary for ~16 agents writing code; the "Tracks" label matches the One UI reference (user,
2026-10-06). It is a copy rule only.

**How to apply:** never rename code to match the label. Leave framework and field names alone: `trackNumber`,
`AudioTrack`, `C.TRACK_TYPE_*`, `player.currentTracks`. Full map: [[Model vocabulary]].
