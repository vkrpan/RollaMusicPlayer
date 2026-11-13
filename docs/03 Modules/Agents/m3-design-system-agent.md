---
type: agent
status: active
source: ".claude/agents/m3-design-system-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-designsystem]]"]
---

# m3-design-system-agent

Guardian of `:core:designsystem`: tokens, semantic extensions, the kit. Zero tolerance for hardcoded values.

- **Definition (source of truth):** `.claude/agents/m3-design-system-agent.md`
- **Owns / works in:** [[core-designsystem]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Every new size or colour becomes a token with its spec §5 row first.
