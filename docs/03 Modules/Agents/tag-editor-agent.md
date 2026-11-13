---
type: agent
status: active
source: ".claude/agents/tag-editor-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[feature-tageditor]]"]
---

# tag-editor-agent

Tag IO: reading and writing ID3/Vorbis/MP4 metadata, scoped-storage consent, artwork embedding, re-indexing after save.

- **Definition (source of truth):** `.claude/agents/tag-editor-agent.md`
- **Owns / works in:** [[feature-tageditor]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Fully local: no online lookups.
