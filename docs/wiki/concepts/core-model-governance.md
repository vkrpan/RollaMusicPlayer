---
type: concept
summary: "`:core:model` has no owner; changes follow model-vocabulary.md and need code-reviewer plus consumer review."
aliases: ["The :core:model governance rule", "Shared-contract governance"]
tags: [governance, models]
sources: ["[[sources/ownership-map]]", "[[sources/claude-md]]"]
updated: 2026-11-09
---
# Shared-contract governance

`:core:model` has no owner; changes follow model-vocabulary.md and need code-reviewer plus consumer review.

[[module-core-model]] is the most cross-cutting code, so it has no single owner ([[ownership-map]]):

- Names follow `.claude/rules/model-vocabulary.md` ([[song-not-track]]).
- Any new model, new field or rename needs code-reviewer plus every consuming agent.
- No module redefines a model locally; everything imports from [[module-core-model]].

## Related
[[ownership-map]], [[claude-md]], [[module-core-model]], [[song-not-track]]
