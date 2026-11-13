---
type: agent
status: active
source: ".claude/agents/permissions-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-permissions]]"]
---

# permissions-agent

The runtime media-permission flow and gate. Storage only.

- **Definition (source of truth):** `.claude/agents/permissions-agent.md`
- **Owns / works in:** [[core-permissions]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Always show a rationale before the system dialog on first ask.
- Never request INTERNET or write permissions (tag writes ask at the point of use).
