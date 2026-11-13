---
type: guide
status: active
source: ".claude/rules/kotlin-style.md"
tags: [guide, kotlin]
related: ["[[Guides]]", "[[Offline and privacy first]]"]
---

# Kotlin style

**Source of truth:** `.claude/rules/kotlin-style.md` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- Official Kotlin conventions, 4-space indent, 120-char lines, braces always.
- Prefer `val`, immutable collections, no `!!`; rethrow `CancellationException`.
- Review checklist ends with: no network dependencies, no INTERNET permission, local storage only, airplane-mode safe.
