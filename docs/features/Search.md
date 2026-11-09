---
type: feature
status: active
date: 2026-07-08
source: "v1 Phase 5 (`8c22705`…`adc4577`); One UI Phase 6b"
tags: [search, feature]
related: ["[[Features]]", "[[Search uses LIKE, not FTS4]]", "[[Artists are identified by name]]", "[[feature-search]]"]
---

# Search

- **Matching:** a local substring search with grouped results (Songs, Albums, Artists). See
  [[Search uses LIKE, not FTS4]].
- **Behaviour:** debounced (300 ms) and cancellable; a blank query goes straight back to idle; state is seeded from
  `SavedStateHandle`.
- **Recent searches:** stored in DataStore, capped at 10 most recent, written best-effort.
- **Navigation:** the route is `Search(query: String? = null)`, with the deep link `rollamusic://search` and entry
  points in Home's header.
- **Module:** [[feature-search]].
