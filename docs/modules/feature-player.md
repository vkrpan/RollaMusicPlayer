---
type: module
status: active
gradle: ":feature:player"
owners: ["[[ui-builder]]", "[[viewmodel-architect]]", "[[compose-animation-agent]]"]
tags: [module]
related: ["[[Modules]]", "[[Now Playing and mini-player]]"]
---

# feature-player

`:feature:player` — Now Playing, the mini-player pill, the queue sheet.

- **Owner agent(s):** [[ui-builder]], [[viewmodel-architect]], [[compose-animation-agent]]
- **Features:** [[Now Playing and mini-player]]
- **Decisions and rules:** —

## Changes
- 2026-11-08 (per-track artwork Task 3): a `MiniPlayer` comment names `SongArtworkInterceptor` (comment only).
- 2026-11-12 (custom tags phase 4, Task 4): Now Playing shows the track's tag chips under the title and artist,
  centred, at most two lines, fading with the metadata.
