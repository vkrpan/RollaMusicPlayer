---
type: module
status: active
gradle: ":feature:search"
owners: ["[[ui-builder]]", "[[viewmodel-architect]]"]
tags: [module]
related: ["[[Modules]]", "[[Search]]", "[[Search uses LIKE, not FTS4]]"]
---

# feature-search

`:feature:search` — Local search across the library, recent searches.

- **Owner agent(s):** [[ui-builder]], [[viewmodel-architect]]
- **Features:** [[Search]]
- **Decisions and rules:** [[Search uses LIKE, not FTS4]]

## Changes
- 2026-11-08 (per-track artwork Task 5): album and artist results show the placeholder.
- 2026-11-12 (custom tags phase 4, Tasks 5–6): package `findtracks`: `FindTracksViewModel` (filter in
  `SavedStateHandle`, `SongFinder` results, picker state, `listsUnavailable` and `activeCustomTagFilters`, play/shuffle,
  playlist actions for the options sheet), `FindTracksUiState`, `FindTracksScreen`/`FindTracksRoute`, the navigation
  helper; `src/test/resources/robolectric.properties` (`sdk=34`). See [[Custom tags]].
