---
type: index
tags: [index, history, sessions]
related: ["[[Timeline]]", "[[Save compaction summaries to the vault]]"]
---

# Sessions

Compaction summaries saved verbatim by the PostCompact hook (`.claude/hooks/compact_to_vault.py`), one note per
compaction, named `<date> <time> compaction.md`. Each starts as `status: to-distill` and becomes `status: distilled`
once its durable content is in the entity notes ([[Save compaction summaries to the vault]]).

- [[2026-11-08 000000 compaction (before the hook)]] — written by hand when the hooks were added.
- [[2026-11-08 144821 compaction]] — the first one saved by the hook (per-track artwork, Task 2 done).
- [[2026-11-11 215746 compaction]] — custom tags phase 3, Task 5 stopped by the spend limit.

Find undistilled ones: `grep -l "^status: to-distill" "docs/08 Project/History/sessions/"*.md`
