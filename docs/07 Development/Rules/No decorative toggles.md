---
type: rule
status: active
date: 2026-07-08
source: "user, settings phase: \"a settings screen full of switches that don't do anything is worse than a short one where everything works\""
tags: [product, ui, settings]
related: ["[[Rules]]", "[[Equaliser visualizer rejected]]", "[[Settings toggles rejected]]", "[[Settings]]"]
---

# No decorative toggles

**Rule:** every user-facing control must change real behaviour. A control whose capability doesn't exist, or that is a
no-op, is not built.

**Why (user's words):** "a settings screen full of switches that don't do anything is worse than a short one where
everything works."

**How to apply:** before adding a control, verify the underlying capability exists and the control changes behaviour.
Record each rejection as a decision so it is not relitigated. Precedents: [[Equaliser visualizer rejected]],
[[Settings toggles rejected]], and the honest Privacy screen (no analytics or crash-reporting switches that would imply
data collection).
