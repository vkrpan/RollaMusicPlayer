---
type: agent
status: active
source: ".claude/agents/compose-animation-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[feature-player]]"]
---

# compose-animation-agent

Animations and transitions in Compose (60 fps, reduced motion).

- **Definition (source of truth):** `.claude/agents/compose-animation-agent.md`
- **Owns / works in:** [[feature-player]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Always pass the `label` parameter to animation APIs.
- Respect reduced motion (`rememberReducedMotion()`, springs become snaps).
