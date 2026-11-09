---
type: guide
status: active
source: ".claude/rules/ui-style-guide.md"
tags: [guide, ui, design-system]
related: ["[[Guides]]", "[[Build UI from tokens and the kit]]", "[[Touch-target exceptions]]", "[[core-designsystem]]"]
---

# UI style guide

**Source of truth:** `.claude/rules/ui-style-guide.md` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- Values live in the redesign spec §5–§6 and `:core:designsystem`; the guide names tokens, never hex or dp.
- Blue text is `accentText`; destructive text is `destructiveText`; every pairing meets WCAG AA (`ContrastTest`).
- Use the kit, never the M3 component it wraps; icons are `RollaIcons` only.
- Touch targets ≥ 48 dp except the EQ columns and the A–Z rail.
- Screen blueprints: Home, mini-player, Now Playing, pushed screens, Equaliser, Settings, widget (§8 → spec §13).
- Motion 200–300 ms, springs for interactive elements, reduced motion respected.
