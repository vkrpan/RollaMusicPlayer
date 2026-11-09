---
type: agent
status: active
source: ".claude/agents/media-scanning-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-data]]"]
---

# media-scanning-agent

The local MediaStore indexer and incremental sync into Room. Fully offline.

- **Definition (source of truth):** `.claude/agents/media-scanning-agent.md`
- **Owns / works in:** [[core-data]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- No online metadata or artwork lookups.
