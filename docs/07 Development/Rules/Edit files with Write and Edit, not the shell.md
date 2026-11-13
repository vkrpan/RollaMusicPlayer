---
type: rule
status: active
date: 2026-10-07
source: "user rule after sed -i stripped CRLF (2026-10-07); heredoc failures 2026-07-08/09 and 2026-10-09"
tags: [tooling, workflow]
related: ["[[Rules]]", "[[Spotless and line endings]]", "[[Shell heredocs fail on large files]]"]
---

# Edit files with Write and Edit, not the shell

**Rule:**
- Never `sed -i` a source file.
- Never write Kotlin, Gradle or XML through a shell heredoc.
- Briefs, ledgers and reports are written with the Write/Edit tools too.

**Why:** `sed -i` in Git Bash turns the checkout's CRLF files to LF, and Spotless then fails on every line
([[Spotless and line endings]]). Large or quote-heavy heredocs fail with spurious errors
([[Shell heredocs fail on large files]]).

**How to apply:** use Edit for in-place changes and Write for new files. A bulk mechanical change can be a Python script
that reads bytes, replaces, and writes bytes back (line endings preserved), run with `python -I`. Tell subagents the
same rule in their brief.
