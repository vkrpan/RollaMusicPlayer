---
type: agent
status: active
source: ".claude/agents/viewmodel-architect.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[feature-library]]", "[[feature-search]]", "[[feature-player]]", "[[feature-playlists]]", "[[feature-settings]]"]
---

# viewmodel-architect

ViewModels and state: `StateFlow` UI state, events, coroutines.

- **Definition (source of truth):** `.claude/agents/viewmodel-architect.md`
- **Owns / works in:** [[feature-library]], [[feature-search]], [[feature-player]], [[feature-playlists]], [[feature-settings]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Never hold Activity, Fragment or UI references in a ViewModel.
- Expose `StateFlow` read-only.
