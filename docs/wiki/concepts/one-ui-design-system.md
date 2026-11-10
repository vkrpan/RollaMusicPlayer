---
type: concept
summary: "OLED-black dark theme, one blue accent, rounded surfaces; tokens and kit in :core:designsystem."
aliases: ["One UI redesign", "Visual style"]
tags: [design, ui]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-09
---
# One UI design system

OLED-black dark theme, one blue accent, rounded surfaces; tokens and kit in :core:designsystem.

One UI–inspired dark: a true-black OLED background, a single blue accent, heavily rounded surfaces, a floating pill
mini-player and a six-tab Home with no bottom navigation ([[claude-md]]). Values live in the redesign spec
§5–§6, rules in `.claude/rules/ui-style-guide.md`, tokens and the kit in [[module-core-designsystem]]. Every screen builds
from tokens and the kit, never from hard-coded values.

## Related
[[claude-md]], [[module-core-designsystem]]

## Disagreements & open questions
- [[claude-md]]'s Notes say "Use Material Design 3 components throughout", while the UI style guide says to
  use the kit and never call the M3 component it wraps (`TopAppBar`, `Button`, `ModalBottomSheet` …). The kit wraps
  M3, so the style guide is the more precise rule; the Notes line reads as outdated.
