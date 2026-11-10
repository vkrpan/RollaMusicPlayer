---
type: concept
summary: "Every track shows its own embedded picture; albums, artists and folders show the placeholder."
aliases: ["Track artwork", "SongArtworkCache"]
tags: [feature, artwork]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-09
---
# Per-track artwork

Every track shows its own embedded picture; albums, artists and folders show the placeholder.

Committed `e129f3e` (2026-11-09) on `feat/oneui-redesign` ([[claude-md]]):

- `Song.artworkUri` is `rolla-artwork://song/{id}?v={dateModified}`; `SongArtworkCache` in [[module-core-data]] decodes
  each file's own embedded picture once.
- Shown everywhere: lists, Search, the queue, the mini-player and Now Playing ([[module-feature-player]]), the notification,
  lock screen and media card ([[module-core-media]]) and the widget ([[module-feature-widget]]). No picture → the white note. The
  stock-Android resume card always shows the white note (user decision).
- Albums, artists and folders always show the placeholder ([[module-core-ui]], [[module-feature-library]]).
- Edit tags has Change and Remove artwork; the Tracks tab's selection ⋮ sets or removes artwork for many tracks
  ([[module-feature-tageditor]]). MP3, FLAC and M4A only.
- Replaces MediaStore's per-album cover, which untagged tracks shared.

## Related
[[claude-md]], [[module-core-data]], [[module-feature-player]], [[module-core-media]], [[module-feature-widget]], [[module-core-ui]], [[module-feature-library]], [[module-feature-tageditor]]
