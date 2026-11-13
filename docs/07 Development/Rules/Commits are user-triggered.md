---
type: rule
status: active
date: 2026-07-07
source: "user, tag-editor phase (rejected two proactive commits); reconfirmed every phase since"
tags: [git, workflow]
related: ["[[Rules]]", "[[Commit staging excludes local artefacts]]", "[[git commit hook needs a long timeout]]"]
---

# Commits are user-triggered

**Rule:** never run `git commit` (or push) unprompted. When a checkpoint is green, report "ready to commit" with the
prepared message and stop. Commit only when the user asks in that session ("commit", "commit and push").

**Why:** the commit is the user's sign-off on each checkpoint, even when a phase doc or plan contains a commit step.

**How to apply:**
- A plan's "Commit" step means "stage the files and report ready to commit".
- Staging (`git add`, `git mv`) is fine; the commit is not.
- When asked, use the staging command in [[Commit staging excludes local artefacts]] and end the message with
  `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
