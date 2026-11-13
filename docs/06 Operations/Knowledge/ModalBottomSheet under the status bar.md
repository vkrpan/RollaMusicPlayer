---
type: knowledge
status: active
date: 2026-10-11
source: "QueueSheet D3-1, 2026-10-11"
tags: [compose, insets, sheets]
related: ["[[Knowledge]]", "[[Now Playing and mini-player]]"]
---

# ModalBottomSheet under the status bar

**Symptom:** M3 1.3.0's `ModalBottomSheet`, fully expanded, runs under the status bar (edge-to-edge dialog window).

**Fix:** pass `modifier.statusBarsPadding()`, not `windowInsetsPadding(WindowInsets.statusBars)`. The latter is read
where it is written (the activity window); the composed modifier is read inside the dialog window.
`OneUiBottomSheet` carries the fix.
