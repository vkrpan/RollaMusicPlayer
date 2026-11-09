---
type: rule
status: active
date: 2026-10-11
source: "user decisions 2026-10-06 and Phase 3 gate 2026-10-11; spec docs/superpowers/specs/2026-10-06-oneui-redesign-design.md §2"
tags: [ui, copy]
related: ["[[Rules]]", "[[Song in code, Tracks on screen]]", "[[One UI redesign decisions]]", "[[Unknown artists show as Unknown]]"]
---

# UI copy and spelling

**Rule:**
- The app's name on screen is **"Rolla Music"** (launcher label and every user-visible name); never "Samsung Music".
- British spelling in UI copy: **"Equaliser"**, **"colour"** ("Dynamic colour"). Code identifiers stay American
  (`Equalizer*`, `color`).
- The library unit is **"Tracks"** on screen: see [[Song in code, Tracks on screen]].
- A missing artist shows as **"Unknown"**: see [[Unknown artists show as Unknown]].

**How to apply:** match copy verbatim to the spec when it gives copy; counts use `tracksCountLabel(n)` ("1 track",
"N tracks").
