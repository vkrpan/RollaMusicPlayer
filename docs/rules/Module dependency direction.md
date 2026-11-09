---
type: rule
status: active
date: 2026-07-01
source: "CLAUDE.md Project Structure; `setup-modularization` skill"
tags: [architecture, modules]
related: ["[[Rules]]", "[[Multi-module architecture]]", "[[Modules]]", "[[Ownership map]]"]
---

# Module dependency direction

**Rule:** `:feature:* → :core:* → :core:model`. Features never depend on each other; core never depends on a feature;
no cycles. Cross-feature flows go through `:core:data` or navigation. `:app` wires everything.

**How to apply:** code two features both need moves into a `:core:*` module, or the features duplicate it (Phase 4b
ruling P1 accepted duplication rather than a mid-phase extraction). A composable exported by a feature for `:app` to
place (e.g. a selection-bar action) is fine: `:app` depends on every feature.
