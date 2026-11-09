---
type: agent
status: active
source: ".claude/agents/build-tooling-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[build-logic]]", "[[baselineprofile]]", "[[app]]"]
---

# build-tooling-agent

Build system and tooling: Gradle files, the version catalog, convention plugins, R8 rules, baseline profile config, static analysis, CI. Enforces the no-network rule in the build. Writes no feature code.

- **Definition (source of truth):** `.claude/agents/build-tooling-agent.md`
- **Owns / works in:** [[build-logic]], [[baselineprofile]], [[app]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- No network dependency may enter the catalog.
