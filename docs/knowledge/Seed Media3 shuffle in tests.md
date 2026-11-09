---
type: knowledge
status: active
date: 2026-10-09
source: "Phase 3 player tests, 2026-10-09"
tags: [testing, media3]
related: ["[[Knowledge]]", "[[Playback and queue]]"]
---

# Seed Media3 shuffle in tests

**Symptom:** a real-ExoPlayer test about shuffle order (e.g. "start item first") passes or kills mutants by chance.

**Cause:** Media3's default shuffle order is unseeded.

**Fix:** `player.setShuffleOrder(DefaultShuffleOrder(0, seed))`, and check that the chosen seed actually kills the
mutant (seeds 0 and 2 didn't; 1 did).
