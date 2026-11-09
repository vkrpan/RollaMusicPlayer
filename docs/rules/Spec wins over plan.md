---
type: rule
status: active
date: 2026-10-06
source: "CLAUDE.md Current Status; superpowers executing-plans; every One UI phase"
tags: [workflow, specs]
related: ["[[Rules]]", "[[Keep docs honest]]", "[[Ledgers]]"]
---

# Spec wins over plan

**Rule:** the order of authority is **spec → plan → CLAUDE.md and other docs**. Where a plan and its spec disagree,
the spec wins and the deviation is recorded as a ruling ("what — why — cost if wrong"). Where CLAUDE.md disagrees with
the spec, the spec wins.

**How to apply:** specs live in `docs/superpowers/specs/`, plans in `docs/superpowers/plans/`. Execution rulings go in
the run's ledger and are archived under [[Ledgers]]; decisions that matter to the user also get a note in [[Decisions]].
New work: a user-approved spec first, then a plan, then code.
