---
type: rule
status: active
date: 2026-10-21
source: "6 drift incidents in CLAUDE.md, 2026-07-08 to 2026-10-21 (the last one self-inflicted in Phase 6a)"
tags: [docs, workflow]
related: ["[[Rules]]", "[[Spec wins over plan]]", "[[Record knowledge in the vault]]"]
---

# Keep docs honest

**Rule:** CLAUDE.md, the vault and the agent docs describe what shipped, never what was planned. At every phase merge,
sweep the module descriptions and every claim the phase touched. Before writing "consolidated", "gone everywhere" or
"one helper", grep the old name or body across **all** modules (excluding `build/`) and word the claim to the grep's
scope.

**Why:** module blurbs described unbuilt things six times — `:feature:library` "Genres", `:core:data`
Album/Artist repositories, `:core:datastore` settings, `:core:common` utilities, "persistent playback queue" (fixed later
by [[Playback queue restore]]), and a self-made "consolidated" claim in Phase 6a while a fifth copy still lived in
`PlayerViewModel`.

**How to apply:** during recon, never trust a capability claim in a doc without checking the code.
