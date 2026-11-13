---
type: decision
adr: "ADR-011"
status: active
date: 2026-10-06
source: "user, 2026-10-06 (redesign kickoff), Phase 3 gate 2026-10-11, Phase 4a device check 2026-10-12; spec `docs/superpowers/specs/2026-10-06-oneui-redesign-design.md`"
tags: [redesign, ui, oneui]
related: ["[[Decisions]]", "[[One UI redesign]]", "[[UI copy and spelling]]", "[[Song in code, Tracks on screen]]", "[[Touch-target exceptions]]", "[[Widget design rules]]", "[[Home and library]]"]
---

# One UI redesign decisions

The user asked (2026-10-06) for a screenshot-accurate One UI–style redesign against the Samsung Music screenshots in
`media player example/`. Spec and measurements (`docs/design/oneui-measurements.md`) approved the same day.

**Kickoff (2026-10-06):**
- Build the **centre-weighted tab bar** (replacing the bottom nav), the **Folders** tab and the **A–Z fast-scroll** —
  former v1.0 cuts.
- UI label **"Tracks"**; the model stays `Song` ([[Song in code, Tracks on screen]]).
- The widget keeps its **±15 s** controls and adopts only the look ([[Widget design rules]]).
- **Custom thin-stroke icons** (`RollaIcons`), no Material icons.
- The header never says "Samsung Music"; the app's own name is used ("Rolla Music").
- **Touch-target exceptions** for the EQ columns and the A–Z rail ([[Touch-target exceptions]]).
- UI spelling **"Equaliser"** ([[UI copy and spelling]]).

**Phase 3 gate (2026-10-11):**
- British "colour" in UI copy.
- Tapping a track anywhere, Search results included, **plays the whole visible list from that track**.
- The Equaliser "Custom" chip staying a no-op tap is fine.
- Long-press delete of user presets with **no confirm** is fine.
- The user judges motion on a release-like build ([[Judge motion on a release-like build]]).

**Phase 4a device check (2026-10-12):**
- Compare the installed APK's signing certificate with the build's before installing
  (`apksigner verify --print-certs`).
- Later superseded for the device itself by [[Device tests run on the Motorola]].

**Phase 7 (2026-10-31 to 2026-11-01):**
- Tokens calibrated on the Motorola against the reference screenshots ([[Calibration tolerances]]).
- "Unknown" artists keep sorting under `#` (ruling P9; see [[Backlog]]).
