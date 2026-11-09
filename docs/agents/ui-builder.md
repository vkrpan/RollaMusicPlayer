---
type: agent
status: active
source: ".claude/agents/ui-builder.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-ui]]", "[[feature-library]]", "[[feature-search]]", "[[feature-player]]", "[[feature-playlists]]", "[[feature-settings]]", "[[feature-tageditor]]", "[[app]]"]
---

# ui-builder

Compose screens and components in `:core:ui` and `:feature:*`, strictly from tokens and the kit. Has no Write tool: create a stub, then fill it with Edit.

- **Definition (source of truth):** `.claude/agents/ui-builder.md`
- **Owns / works in:** [[core-ui]], [[feature-library]], [[feature-search]], [[feature-player]], [[feature-playlists]], [[feature-settings]], [[feature-tageditor]], [[app]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- No network loading states.
