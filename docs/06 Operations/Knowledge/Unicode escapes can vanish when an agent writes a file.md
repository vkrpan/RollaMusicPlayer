---
type: knowledge
status: active
date: 2026-11-11
source: "custom tags phase 3, Task 4 implementer report: a Kotlin string escape for the byte order mark came out empty on disk"
tags: [tooling, agents, kotlin]
related: ["[[Knowledge]]", "[[Edit files with Write and Edit, not the shell]]", "[[Custom tags]]"]
---

# Unicode escapes can vanish when an agent writes a file

**Symptom:** a Kotlin source written by an agent contained `"﻿"` (the byte order mark) in the agent's text, but
the file on disk held an empty string `""`; the code compiled and the BOM check silently did nothing.

**Cause:** the escape was turned into the real character on the way to the file, and an invisible zero-width BOM is
easy to lose or strip; the result reads as `""`.

**Fix:** write such characters as code points that cannot be lost, e.g. `0xFEFF.toChar()`, and pin them with a test
that feeds the real character. Visible non-ASCII characters (such as `→` U+2192 in UI copy) survived; check invisible
ones with `grep -P` or a hex dump after writing.
