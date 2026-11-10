---
type: entity
summary: "The Glance home-screen widget: artwork, progress, 15-second skip and playback controls."
aliases: [":feature:widget"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :feature:widget

The Glance home-screen widget: artwork, progress, 15-second skip and playback controls.

The Jetpack Glance widget: the current track's own artwork (or the white note), title and artist, a progress bar, and
previous · −15 s · play/pause · +15 s · next; a body tap opens the app. Updated through [[module-core-media]]'s
`PlaybackUpdateHook`; it keeps its own palette, because Glance cannot read Compose tokens.

- **Owner:** widget-agent ([[agent-module-ownership]]).

## Related
[[claude-md]], [[ownership-map]], [[module-core-media]], [[agent-module-ownership]]
