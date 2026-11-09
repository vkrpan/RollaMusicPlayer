---
type: decision
status: active
date: 2026-07-05
source: "v1 Phase 3 (equalizer); `docs/history/v1-phases/PHASE3-EQUALIZER.md` Notes; CLAUDE.md Advanced Audio Features"
tags: [equaliser, privacy, product]
related: ["[[Decisions]]", "[[Equaliser]]", "[[No decorative toggles]]", "[[Offline and privacy first]]"]
---

# Equaliser visualizer rejected

**Decision:** no live audio visualisation. The Equaliser shows a **gains-driven response curve** computed from the band
gains while the user adjusts them.

**Why:** a real `android.media.audiofx.Visualizer` needs `RECORD_AUDIO`, which contradicts the privacy-first
positioning ([[Offline and privacy first]]).

**Rules out:** any feature needing a microphone or audio-capture permission.
