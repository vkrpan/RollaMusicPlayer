---
type: agent
status: active
source: ".claude/agents/navigation-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[app]]"]
---

# navigation-agent

The Navigation Compose graph: routes, arguments, deep links, back stack.

- **Definition (source of truth):** `.claude/agents/navigation-agent.md`
- **Owns / works in:** [[app]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Never pass full objects through routes; pass ids.
- Routes are `@Serializable`, never a sealed type.
