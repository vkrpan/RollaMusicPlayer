---
type: knowledge
status: active
date: 2026-07-13
source: "album-detail commit, 2026-07-13"
tags: [git, tooling]
related: ["[[Knowledge]]", "[[Commits are user-triggered]]", "[[Commit staging excludes local artefacts]]"]
---

# git commit hook needs a long timeout

**Symptom:** `git commit` exits 143 and nothing lands (HEAD unchanged, staging preserved).

**Cause:** the pre-commit hook runs detekt and Spotless over the repo ("Running static analysis pre-commit checks…"),
which takes longer than the Bash tool's default 120 s.

**Fix:** give `git commit` a long timeout (e.g. 600000 ms). When the user pastes an exact `git commit -m "…"`, run that
command plainly.
