---
type: decision
adr: "ADR-001"
status: active
date: 2026-07-01
source: "CLAUDE.md Project Structure; `setup-modularization` skill; `docs/08 Project/History/v1-phases/PHASE0-BOOTSTRAP.md`"
tags: [architecture, modules]
related: ["[[Decisions]]", "[[Modules]]", "[[Module dependency direction]]", "[[Ownership map]]", "[[Agents]]"]
---

# Multi-module architecture

**Decision:** a hybrid feature + core multi-module build in the "Now in Android" style: `:app` (shell, NavHost, DI
root), `:core:*` for shared concerns, `:feature:*` for one screen area each, `:baselineprofile` for benchmarks, and
`build-logic/` convention plugins with a version catalog. Each module has one owning agent ([[Ownership map]]).

**Why:** clear ownership for ~16 agents working in parallel, enforced dependency direction, faster incremental builds.

**Rules out:** features depending on features ([[Module dependency direction]]); per-module `composeCompiler {}`
blocks (the convention plugins apply the stability config centrally: [[Compose stability config location]]).
