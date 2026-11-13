---
type: module
status: active
gradle: ":feature:widget"
code: "feature/widget"
owners: ["[[widget-agent]]"]
tags: [module]
related: ["[[Modules]]", "[[Home-screen widget]]", "[[Widget design rules]]"]
---

# feature-widget

`:feature:widget` — The Glance home-screen widget, its own palette, action callbacks, artwork loader.

- **Owner agent(s):** [[widget-agent]]
- **Features:** [[Home-screen widget]]
- **Decisions and rules:** [[Widget design rules]]

## Changes
- 2026-11-08 (per-track artwork Task 3): `WidgetArtworkLoader` resolves only `rolla-artwork://` values, through
  `SongArtworkCache.get`, then decodes the cache file; any other path, no picture or a cache failure gives no bitmap.
  It no longer holds a `Context` and never reads a `content://` uri.
