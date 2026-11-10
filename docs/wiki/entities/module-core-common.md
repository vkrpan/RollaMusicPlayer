---
type: entity
summary: "Shared non-model utilities; today only the Unicode-aware library order."
aliases: [":core:common"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:common

Shared non-model utilities; today only the Unicode-aware library order.

Holds the library order (`text/LibraryOrder.kt`: `indexLetter`, `sortedByLibraryName`), shared by [[module-core-data]] and
[[module-core-designsystem]]. Dispatcher qualifiers are still per module (`CoreDataIoDispatcher`, `TagEditorIoDispatcher`,
`WidgetIoDispatcher`).

- **Owner:** code-reviewer, light governance ([[agent-module-ownership]]).

## Related
[[claude-md]], [[ownership-map]], [[module-core-data]], [[module-core-designsystem]], [[agent-module-ownership]]

## Disagreements & open questions
- An older `CLAUDE.md` described this module as holding "Dispatchers, Result types". The current
  [[claude-md]] says the library order, and the code agrees: `core/common/.../text/LibraryOrder.kt` is its only
  source file. Resolved; no drift today.
- Open: moving the per-module dispatcher qualifiers here is a backlog idea, not done.
