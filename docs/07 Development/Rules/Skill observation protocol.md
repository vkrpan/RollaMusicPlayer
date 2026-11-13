---
type: rule
status: active
date: 2026-10-17
source: "CLAUDE.md task-observer block (the binding text lives there)"
tags: [workflow, skills]
related: ["[[Rules]]", "[[Subagents run on Opus]]"]
---

# Skill observation protocol

**Rule (summary; CLAUDE.md's top block is the binding text):**
- Before the first tool call of a session, load the task-observer skill **and** run its Session Start Protocol.
- At every skill load, grep the observation log for open observations naming that skill and apply them to the task.
- After each task, list the observation records written this session (from the listing, not from memory).
- Create an observation file only through the helper:
  `bash "<skill dir>/scripts/new-observation.sh" <slug> "/c/Users/VK/.claude/task-observer-workspace"` (the `/c/`
  form; the helper rejects `C:/`).

**Where things live:** the workspace is `C:/Users/VK/.claude/task-observer-workspace` (log, reviews, staged skill
updates under `skill-updates/`). It is about improving skills, not the app, so it stays outside this vault.

**Reviews so far:** 2026-10-17 (installed) and 2026-11-04 (approved and staged 2026-11-08; superpowers-extras update
awaiting install — see [[Backlog]]).
