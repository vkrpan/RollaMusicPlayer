---
type: module
status: active
gradle: ":core:designsystem"
owners: ["[[m3-design-system-agent]]"]
tags: [module]
related: ["[[Modules]]", "[[Home and library]]", "[[Build UI from tokens and the kit]]", "[[UI style guide]]", "[[Touch-target exceptions]]", "[[Calibration tolerances]]"]
---

# core-designsystem

`:core:designsystem` — One UI tokens (`RollaDimens`, `RollaShapes`, semantic colours and type), `RollaIcons`, the component kit in `component/`, motion helpers. No Material icons.

- **Owner agent(s):** [[m3-design-system-agent]]
- **Features:** [[Home and library]]
- **Decisions and rules:** [[Build UI from tokens and the kit]], [[UI style guide]], [[Touch-target exceptions]], [[Calibration tolerances]]

## Changes
- 2026-11-09 (per-track artwork Task 7): `OneUiPillButton(destructive = false)`: a tonal pill's label in
  `destructiveText` (pairing on `surfaceContainerHigh` already in `ContrastTest`: 4.64:1 dark, 5.28:1 light).
- 2026-11-09 (per-track artwork Task 8): `OneUiMenuItem(destructive)` (keeps the disabled fade);
  `OneUiDetailTopBar(navigationEnabled, titleStyle, compactTitleStyle)` — the title steps down to the compact style only
  when it would not fit on one line (measured; ruling P24).
