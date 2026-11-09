---
type: decision
status: active
date: 2026-10-17
source: "One UI Phase 5 (A–Z fast scroll); `:core:common` `text/LibraryOrder.kt`"
tags: [library, sorting]
related: ["[[Decisions]]", "[[Home and library]]", "[[core-common]]", "[[Ledgers]]"]
---

# One library order

**Decision:**
- Every Home list uses one library order: `#` first, then A–Z, ignoring case and accents.
- It is implemented once in `:core:common` (`text/LibraryOrder.kt`: `indexLetter`, `sortedByLibraryName`) and shared by
  `:core:data` and `:core:designsystem`.
- The A–Z rail's sections come from the same order.

**Why:** the rail can only jump correctly if every list sorts the way the rail indexes.

**Known edge:** `buildFastScrollSections` throws on a list that is not in library order instead of degrading
(ruling P36, deferred; [[Backlog]]).
