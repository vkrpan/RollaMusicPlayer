---
type: hub
status: active
date: 2026-11-13
tags: [project, roadmap]
related: ["[[00 Home]]", "[[Backlog]]", "[[Known Issues]]", "[[Changelog]]"]
---

# Roadmap

Planned work, in order. Each item's detail, sub-tasks and source live in [[Backlog]]; bugs and tech debt in
[[Known Issues]]. Everything here is the user's call to start.

## Next
1. **Merge `feat/oneui-redesign` into `main` and tag the release** (redesign, per-track artwork, custom tags):
   bump the version, regenerate the Baseline Profile, add the release to [[Changelog]] ([[Deployment]]).
2. **Verify the stock-Android resume card** on a stock device; it is designed but never seen
   ([[Panel resume targets stock Android]]).
3. **Investigate scroll performance** of the Tracks list against v1.0.0 (+35.9 % frame P90 on the emulator):
   benchmark `8f4ce3a` against HEAD and take a system trace ([[Known Issues]]).

## Later
- **Custom tags follow-ups**: the phase 4 and phase 3 review minors, batched `FileTagPass` writes if the device shows a
  cost ([[Custom tags]]).
- **M4A writing** of `ROLLA_TAGS` and safe M4A tag saves (blocked by jaudiotagger; [[ROLLA_TAGS field]]).
- **Consolidations**: the shared per-file write loop, the picker pager sync in `:core:ui`, dispatcher qualifiers in
  `:core:common` ([[Known Issues]]).
- **Docs and tooling**: refresh `README.md` (still describes v1.0), the LLM wiki follow-ups, the compaction hooks in the
  committed settings, `Write` for the project agents ([[Backlog]]).

## Not planned
Network features of any kind ([[Offline and privacy first]]); a live visualiser
([[Equaliser visualizer rejected]]).
