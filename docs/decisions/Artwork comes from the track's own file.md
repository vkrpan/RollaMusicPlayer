---
type: decision
status: active
date: 2026-11-08
source: "user, 2026-11-08 (\"I want artwork to be saved to the audio file itself, not the album Android links it to\"); spec `docs/superpowers/specs/2026-11-08-per-track-artwork-design.md`"
tags: [artwork, playback, ui]
related: ["[[Decisions]]", "[[Per-track artwork]]", "[[Untagged tracks share a MediaStore album]]", "[[Albums, artists and folders show the placeholder]]", "[[Album artwork disk cache]]"]
---

# Artwork comes from the track's own file

**Decision:** every screen shows a track's **own embedded picture**. That covers track lists, Search, the queue, the
mini-player, Now Playing, the notification, the lock screen, the media card and the widget.
- No picture in the file means the white-note placeholder. The notification, lock screen and media card show it too
  (user, 2026-11-08).
- **Exception:** the stock-Android media **resume** card (shown after the app's process has died) always shows the
  white note, so a gallery photo is never handed to other apps through the recent root (user, 2026-11-08,
  [[Panel resume targets stock Android]]).
- Never the album's cover, a folder image or another track's picture.
- Album pages show each row's picture instead of its track number (user, 2026-11-08).
- Changes made in the app show at once.
- Changes made by other apps show after the next library sync or Settings → Rescan.

**Why:** the app showed MediaStore's per-album cover. Untagged tracks all share one MediaStore album, so one edit
changed every track's picture, and edits could not be undone ([[Untagged tracks share a MediaStore album]]).

**How (plan):** a per-track, versioned artwork value (`rolla-artwork://song/{id}?v={dateModified}`), a per-track
embedded-picture cache, and Coil, widget and Media3 loaders that read only that cache. Details: [[Per-track artwork]].

**Supersedes:** [[Album artwork disk cache]].
