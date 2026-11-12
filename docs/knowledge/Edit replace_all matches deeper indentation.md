---
type: knowledge
status: active
date: 2026-11-11
source: "custom tags phase 3, Task 5 implementer report"
tags: [tooling, agents, kotlin]
related: ["[[Knowledge]]", "[[Edit files with Write and Edit, not the shell]]", "[[Custom tags]]"]
---

# Edit replace_all matches deeper indentation

**Symptom:** inserting a new named argument after `onSettingsClick = {},` with the Edit tool's `replace_all` also
inserted it at every more deeply indented copy of that line, so some call sites got the argument twice.

**Cause:** `replace_all` matches substrings. A line indented by 20 spaces is a suffix of the same line indented by 24,
so the shallow pattern matches both.

**Fix:** replace the deepest indentation first, or anchor the pattern on the line before it (or on a leading newline
plus the exact indent). Grep the call sites before compiling.
