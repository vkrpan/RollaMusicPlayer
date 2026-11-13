---
type: hub
status: active
date: 2026-11-13
tags: [project, changelog]
related: ["[[00 Home]]", "[[Timeline]]", "[[Roadmap]]", "[[v1.0 release]]", "[[One UI redesign]]"]
---

# Changelog

Changes per version, user-visible first. Dated phase-by-phase history: [[Timeline]].

## Unreleased (`feat/oneui-redesign`, pushed, waiting for the user's merge)
### Custom tags (2026-11-09 → 2026-11-12, `dce76dd`, `eb9ebcb`, `7f26949`, `1cb705b`)
- Tags and subtags, stored as IDs in each file's `ROLLA_TAGS` field (MP3 and FLAC written, M4A read)
  ([[Custom tags]], [[ROLLA_TAGS field]]).
- Assign them in the single and batch tag editors (picker sheet with search and "New tag").
- Manage tags: rename, delete (removed from every file), export and import of the lists ([[Tag list file]]).
- Find tracks (title, artist, album, genre, year, tags, subtags; AND) and tag chips in lists and on Now Playing.
- Room v6 and v7 ([[Database Schema]]).

### Per-track artwork (2026-11-08 → 2026-11-09, `e129f3e`)
- Every track shows its own embedded picture everywhere; albums, artists and folders show the placeholder
  ([[Per-track artwork]]).
- Change and Remove artwork in the tag editor; Set or Remove artwork for a selection of tracks.
- The per-album artwork cache is gone ([[Album artwork disk cache]] superseded).

### One UI redesign (2026-10-06 → 2026-11-04, Phases 1–7, last `15821b4`)
- One UI–style dark design system: tokens and component kit ([[One UI redesign decisions]]).
- Home with six tabs and an A–Z rail replaces the bottom navigation; Folders browsing; Album, Artist and Folder detail
  screens ([[Home replaces the bottom navigation]], [[One library order]]).
- Restyled Now Playing, mini-player pill, Equaliser, Settings, Search, tag editors, sheets, dialogs and widget.
- Queue restore across restarts and headset resume ([[Playback queue restore]]); Media3 1.11.1
  ([[Media3 1.11 upgrade]]); a resume card for stock Android ([[Panel resume targets stock Android]]).
- Device-calibrated sizes ([[Calibration tolerances]]).

## 1.0.0 — 2026-07-23 (tag `v1.0.0`)
- Local playback with background service and notification; shuffle and repeat.
- Playlists, smart playlists, reordering.
- 8-band equaliser with presets.
- ID3 tag editor, single and batch, with artwork.
- Search across songs, albums and artists; Album and Artist detail screens.
- Settings (theme, dynamic colour, speed, skip silence); home-screen widget with ±15 s.
- Release build: R8, no `INTERNET` permission gate, unsigned fallback.
Details: [[v1.0 release]].
