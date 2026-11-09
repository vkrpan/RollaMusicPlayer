---
type: decision
status: active
date: 2026-10-08
source: "One UI Phase 2 (Home shell) and 4a/4b; `.claude/rules/navigation-conventions.md`"
tags: [navigation, home, ui]
related: ["[[Decisions]]", "[[Home and library]]", "[[Navigation conventions]]", "[[One UI redesign decisions]]"]
---

# Home replaces the bottom navigation

**Decision:**
- **`Home` is the start destination:**
  - the "Rolla Music" header;
  - a centre-weighted six-tab pager (Favourites · Playlists · Tracks · Albums · Artists · Folders), with tabs as pager
    pages, not routes;
  - each tab inside a content panel;
  - the floating mini-player pill.
- **Pushed screens:** everything else is pushed on top of Home with type-safe `@Serializable` routes.
- **No bottom navigation bar.** `LibraryScreen` and `PlaylistsScreen` are gone.
- **No Genres tab** (v1.0 had a Genres list; the redesign's tab set has none).
- **Tab state:** the selected tab survives process death through the pager's saved state. There is no per-tab back
  stack.

**Why:** the One UI reference has this structure ([[One UI redesign decisions]]).
