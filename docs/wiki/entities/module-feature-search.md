---
type: entity
summary: "Local search across songs, albums and artists (substring match over Room)."
aliases: [":feature:search"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase4-plan]]"]
updated: 2026-11-12
---
# :feature:search

Local search across songs, albums and artists (substring match over Room).

Grouped local search (songs, albums, artists), a substring `LIKE` match over Room; recent searches live in
[[module-core-datastore]]. Fully offline ([[offline-privacy-architecture]]).

- **Owners:** ui-builder + viewmodel-architect ([[agent-module-ownership]]).
- **Find tracks (custom tags phase 4):** the filter screen and its ViewModel, separate from the quick search ([[custom-tags-phase4-plan]]).

## Related
[[claude-md]], [[ownership-map]], [[module-core-datastore]], [[offline-privacy-architecture]], [[agent-module-ownership]]
