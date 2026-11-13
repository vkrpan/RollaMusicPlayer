---
type: guide
status: active
source: ".claude/rules/compose-conventions.md"
tags: [guide, compose]
related: ["[[Guides]]", "[[Build UI from tokens and the kit]]"]
---

# Compose conventions

**Source of truth:** `.claude/rules/compose-conventions.md` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- Screens end in `Screen`, previews start with `Preview`; stateful wrapper + stateless content.
- Parameter order: required, `modifier`, optional, lambdas last.
- `collectAsStateWithLifecycle`; `remember`/`derivedStateOf`; side effects in the right effect.
- Lazy lists with stable keys; no nested scrolling.
- Content descriptions describe purpose; merge semantics; 48 dp targets; test tags as constants.
