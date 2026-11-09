---
type: rule
status: active
date: 2026-10-06
source: "`.claude/rules/ui-style-guide.md`; spec `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md` §5–§6"
tags: [ui, design-system]
related: ["[[Rules]]", "[[UI style guide]]", "[[core-designsystem]]", "[[m3-design-system-agent]]", "[[ui-builder]]"]
---

# Build UI from tokens and the kit

**Rule:**
- Every colour, size, radius and type style is a token (`RollaDimens`, `RollaShapes`, `MaterialTheme.colorScheme` + the
  semantic extensions, the semantic type styles). No `dp`, colour or font literal in a screen.
- Use the kit in `:core:designsystem/component/`; never the Material 3 component it wraps (`TopAppBar`,
  `AlertDialog`, `ModalBottomSheet`, `TextButton`, `Switch`, `Checkbox`, `Slider`, `Button`…).
- Icons come from `RollaIcons` only. `androidx.compose.material.icons` is banned, imported or fully qualified (detekt
  `ForbiddenImport` and the `checkNoMaterialIcons` task enforce it).
- Blue text is `accentText`, never `primary`; destructive text and icons are `destructiveText`, never `error`.
- A missing value becomes a token, with its spec §5 row, before a screen uses it.
- Every new colour pairing gets its computed contrast in `ContrastTest` (text 4.5:1, non-text 3:1).

**Why:** the One UI look must hold across every screen, and drift is caught by tokens, not by review.

**How to apply:** read [[UI style guide]] (and its source `.claude/rules/ui-style-guide.md`) before any UI work.
