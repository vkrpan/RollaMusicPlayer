---
type: agent
status: active
source: ".claude/agents/data-layer-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-database]]", "[[core-datastore]]", "[[core-data]]"]
---

# data-layer-agent

Exclusive owner of Room (entities, DAOs, repositories, migrations) and DataStore.

- **Definition (source of truth):** `.claude/agents/data-layer-agent.md`
- **Owns / works in:** [[core-database]], [[core-datastore]], [[core-data]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Never modify a migration after it shipped.
- Coordinate interface changes with consumers.
