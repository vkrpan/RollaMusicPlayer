---
type: guide
status: active
source: ".claude/rules/navigation-conventions.md"
tags: [guide, navigation]
related: ["[[Guides]]", "[[Home replaces the bottom navigation]]", "[[navigation-agent]]", "[[app]]"]
---

# Navigation conventions

**Source of truth:** `.claude/rules/navigation-conventions.md` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- Type-safe `@Serializable` routes in `app/.../navigation/Routes.kt`; never string routes or a sealed Route.
- Arguments are ids or primitives; `""` is a valid folder path.
- Shared helpers for multi-entry destinations (`openTagEditor`); `launchSingleTop` on repeats.
- Only navigation-agent edits `Routes.kt` and the NavHost.
