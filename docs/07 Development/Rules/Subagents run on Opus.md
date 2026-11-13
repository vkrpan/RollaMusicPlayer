---
type: rule
status: active
date: 2026-10-06
source: "user, 2026-10-06 (One UI redesign kickoff): tried /model fable, then \"continue with opus 5.5\""
tags: [agents, workflow]
related: ["[[Rules]]", "[[Agents]]"]
---

# Subagents run on Opus

**Rule:** every subagent dispatch passes `model: "opus"`. The main session (the orchestrator) is Opus 5.5 by the user's
choice; don't remind them to switch to Fable (it draws on usage credits).

**Why:** the user wants the strongest model doing the work, and the orchestrator gating its quality.

**How to apply:** treat every agent result as a draft: check its claims against the code and the build output before
accepting, and send weak work back. A subagent dispatched by a session that runs the
[[Skill observation protocol]] does not run it again and does not write to the observation log.
