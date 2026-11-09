---
type: decision
status: active
date: 2026-11-08
source: "user, 2026-11-08 (request + mockup option A); spec 2026-11-08-per-track-artwork goals 4–5, 9"
tags: [artwork, tag-editor, home, ui]
related: ["[[Decisions]]", "[[Per-track artwork]]", "[[Tag editor]]", "[[Home and library]]"]
---

# Set artwork for many tracks from the selection menu

**Decision:**
- **Where:** the Tracks tab only. Hold a track, select any number, then use the selection bar's **⋮** menu: **Set
  artwork** (photo picker) or **Remove artwork**, which asks to confirm with "Remove artwork from N tracks?".
- **Bar layout:** mockup option A. The bar reads "N selected", with ✕ on the left and "Edit tags" plus ⋮ on the right.
- **Writing:** one permission prompt covers all the selected files. A failed file doesn't stop the others.
- **Result:**
  - "Artwork set on K of N tracks" (or "Artwork removed from…");
  - only the failed tracks stay selected.
- **Edit tags (one track):** gains **Remove artwork** next to **Change artwork**. It is hidden when the track has no
  picture.
- **Batch editor:** the batch tag editor screen stays without pictures.
- **File types:** pictures are written for MP3, FLAC and M4A only. Other formats hide the actions, or count as not
  written in a multi-track save.

**Why:** the user wants one picture on many tracks (e.g. a whole album) in a few taps, without the batch editor.
