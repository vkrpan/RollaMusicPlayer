---
type: entity
summary: "One UI theme tokens (colour, type, shape, dimensions) and the model-agnostic component kit."
aliases: [":core:designsystem"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase2-plan]]", "[[sources/custom-tags-phase4-plan]]"]
updated: 2026-11-12
---
# :core:designsystem

One UI theme tokens (colour, type, shape, dimensions) and the model-agnostic component kit.

Tokens (`Color`, `Type`, `Shape`, `RollaDimens`), `RollaIcons` and the One UI component kit (`OneUiTopBar`,
`ContentPanel`, `OneUiPillButton`, `OneUiDialog`, sliders, the A–Z rail …) ([[one-ui-design-system]]).

- **Owner:** m3-design-system-agent ([[agent-module-ownership]]). No hard-coded colours, sizes or type elsewhere.
- `LabelChip` (2026-11-10): a display-only chip sharing `PillChip`'s look, which wraps instead of ellipsizing; used
  for custom tag chips ([[custom-tags]]).
- **Custom tags phase 4:** `TagChip`, `TagChipLine` ("+N"), `RemovableChip` and their tokens ([[custom-tags-phase4-plan]]).

## Related
[[claude-md]], [[ownership-map]], [[one-ui-design-system]], [[agent-module-ownership]]
