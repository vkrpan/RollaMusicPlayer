---
type: decision
status: active
date: 2026-07-08
source: "v1 Phase 5 (search); KDoc in `SearchDao`"
tags: [search, database]
related: ["[[Decisions]]", "[[Search]]", "[[core-database]]"]
---

# Search uses LIKE, not FTS4

**Decision:** local search is a `LIKE` substring match over Room, wildcard-escaped, prefix matches ranked first,
grouped into Songs, Albums and Artists. Albums and artists are derived with `GROUP BY` over `songs` (no new tables).
The query is debounced 300 ms and cancellable (`flatMapLatest`).

**Why (from `SearchDao`'s KDoc, "a deliberate, binding architecture decision"):** a personal on-device library
(hundreds to low thousands of songs) doesn't need FTS's scalability; `LIKE` gives the substring semantics users expect
(typing "air" matches "Stairway"); and it needs no entity, no database version bump and no migration.

**Rules out:** introducing FTS for search.
