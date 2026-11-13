---
type: agent
status: active
source: ".claude/agents/test-writer.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-testing]]"]
---

# test-writer

Unit, integration (Room) and Compose UI tests.

- **Definition (source of truth):** `.claude/agents/test-writer.md`
- **Owns / works in:** [[core-testing]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Watch each test fail before trusting it (mutation evidence).
