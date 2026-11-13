---
type: knowledge
status: active
date: 2026-10-11
source: "D3-2 investigation, 2026-10-11"
tags: [compose, navigation, motion]
related: ["[[Knowledge]]", "[[Now Playing and mini-player]]", "[[Judge motion on a release-like build]]"]
---

# Shared element outside the NavHost

**Symptom:** a grey-circle flash and a snapping shared element when popping Now Playing back to the mini-player pill.

**Cause:** the pill lives outside the NavHost. Keyed off `currentBackStackEntry`, its visibility flipped one frame
before NavHost 2.8.3 started its transition, so the pill entered first.

**Fix:** gate the pill on the destination's own exit (`NowPlayingPresence` / `AppShell`). Judge transitions on a
release-like build.
