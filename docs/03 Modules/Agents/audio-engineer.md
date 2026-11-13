---
type: agent
status: active
source: ".claude/agents/audio-engineer.md"
model: opus
tags: [agent]
related: ["[[Agents]]", "[[core-media]]"]
---

# audio-engineer

Media3 ExoPlayer, the playback service, MediaSession, audio focus, background playback.

- **Definition (source of truth):** `.claude/agents/audio-engineer.md`
- **Owns / works in:** [[core-media]]
- **Dispatch:** `model: opus` ([[Subagents run on Opus]]); its result is a draft the orchestrator verifies.

**Hard rules from its definition:**
- Do not create or configure the `Equalizer` here; expose the audio session id to equalizer-agent.
