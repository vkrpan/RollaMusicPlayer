---
type: feature
status: active
date: 2026-10-27
source: "v1 Phase 7 (`9fa3a73`…`4e91047`); One UI Phase 6c; spec §13"
tags: [widget, feature]
related: ["[[Features]]", "[[Widget design rules]]", "[[feature-widget]]", "[[widget-agent]]", "[[Per-track artwork]]"]
---

# Home-screen widget

- **Built with:** Jetpack Glance, a dark rounded card.
- **Contents:** artwork, title and artist, a progress bar, and previous · −15 s · play/pause · +15 s · next. A body tap
  opens the app.
- **Design rules:** [[Widget design rules]].
- **Device-checked:** on the Motorola (API 36) on 2026-10-23 and 2026-10-27.
- **Not yet checked:** a real artwork bitmap, a large system font, the light system theme, anything below API 31.
- **Artwork:** from the current track's own file through the per-track cache (since per-track artwork Task 3,
  2026-11-08); no picture shows the placeholder ([[Per-track artwork]]). Not yet checked on a device.
- **Module:** [[feature-widget]]. **Owner:** [[widget-agent]]. **Skill:** `implement-home-widget`.
