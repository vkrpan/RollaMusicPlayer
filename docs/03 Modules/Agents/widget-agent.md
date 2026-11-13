---
type: agent
status: active
source: ".claude/agents/widget-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[feature-widget]]"]
---

# widget-agent

Exclusive owner of the Glance widget.

- **Definition (source of truth):** `.claude/agents/widget-agent.md`
- **Owns / works in:** [[feature-widget]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Pass `context.applicationContext`; never retain the `Context`.
- Run `connect()` and every control command on the main thread (from the `implement-home-widget` skill).
