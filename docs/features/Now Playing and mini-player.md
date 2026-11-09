---
type: feature
status: active
date: 2026-10-11
source: "v1 Phase 1 (`286867f`, `066306b`); One UI Phase 3"
tags: [player, ui, feature]
related: ["[[Features]]", "[[Playback and queue]]", "[[Shared element outside the NavHost]]", "[[ModalBottomSheet under the status bar]]", "[[feature-player]]", "[[ui-builder]]", "[[compose-animation-agent]]"]
---

# Now Playing and mini-player

- **Mini-player:** a floating pill over the content, placed by the app shell above the navigation bar. It holds round
  art, title and artist, previous · play/pause · next · queue. A tap opens Now Playing.
- **Now Playing:**
  - a chevron and actions bar (volume, Equaliser, ⋮);
  - the artwork, title and artist;
  - the action row (queue, heart, +);
  - the seek bar with times;
  - shuffle · previous · play · next · play order.
- **Transition:** a shared-element artwork transition (spring physics, reduced-motion aware) between the pill and Now
  Playing. See [[Shared element outside the NavHost]].
- **Queue sheet:** a real queue on `OneUiBottomSheet`.
- **Module:** [[feature-player]].
