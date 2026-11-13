---
type: agent
status: active
source: ".claude/agents/equalizer-agent.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-media]]", "[[feature-equalizer]]"]
---

# equalizer-agent

The 8-band (9-target) graphic equaliser: `audiofx.Equalizer` on the audio session, band mapping, presets.

- **Definition (source of truth):** `.claude/agents/equalizer-agent.md`
- **Owns / works in:** [[core-media]], [[feature-equalizer]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Always re-apply persisted settings after the audio session id changes.
