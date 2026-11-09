---
type: knowledge
status: active
date: 2026-10-09
source: "agents 2026-07-08/09; controller 2026-10-09"
tags: [tooling, shell]
related: ["[[Knowledge]]", "[[Edit files with Write and Edit, not the shell]]"]
---

# Shell heredocs fail on large files

**Symptom:** a multi-hundred-line `cat > file <<'EOF'` dies with a spurious "unexpected EOF" quoting error; it also
fails on bodies with apostrophes or backticks and on multi-file heredocs.

**Fix:** use the Write/Edit tools ([[Edit files with Write and Edit, not the shell]]). A subagent type without a Write
tool (`ui-builder`) creates a stub file and fills it with Edit.
