---
type: agent
status: active
source: ".claude/agents/compose-performance-auditor.md"
model: opus
tags: [agent]
related: ["[[Agents]]"]
---

# compose-performance-auditor

Read-only recomposition audits: unstable lambdas, wide scopes, missing keys.

- **Definition (source of truth):** `.claude/agents/compose-performance-auditor.md`
- **Owns / works in:** —
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- (none beyond the project [[Rules]])
