---
type: agent
status: active
source: ".claude/agents/code-reviewer.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-model]]", "[[core-common]]"]
---

# code-reviewer

Quality gate: MVVM, offline-first, leaks, performance, no network dependencies. Read-only.

- **Definition (source of truth):** `.claude/agents/code-reviewer.md`
- **Owns / works in:** [[core-model]], [[core-common]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- A finding that breaches a stated constraint is at least Important.
