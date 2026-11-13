---
type: module
status: active
gradle: ":core:media"
code: "core/media"
owners: ["[[audio-engineer]]", "[[equalizer-agent]]"]
tags: [module]
related: ["[[Modules]]", "[[Playback and queue]]", "[[Equaliser]]", "[[Playback queue restore]]", "[[Media3 1.11 upgrade]]", "[[Panel resume targets stock Android]]", "[[Media3 playback]]"]
---

# core-media

`:core:media` — Media3 `PlaybackService` (a `MediaLibraryService`), `MediaSession`, `PlaybackController`, `PlaybackStateHolder`, the queue (`QueueState`/`QueuePublisher`), queue save/restore (`RestoreCoordinator`, `QueuePersister`), the audio session, the equaliser effect, `PlaybackSettingsBinder`, the widget update hook.

- **Owner agent(s):** [[audio-engineer]], [[equalizer-agent]]
- **Features:** [[Playback and queue]], [[Equaliser]]
- **Decisions and rules:** [[Playback queue restore]], [[Media3 1.11 upgrade]], [[Panel resume targets stock Android]], [[Media3 playback]]

## Changes
- 2026-11-08 (per-track artwork Task 4): `SongArtworkBitmapLoader` (internal) is the session's `BitmapLoader`: a
  `rolla-artwork://` value loads through `SongArtworkCache`; no picture, a cache or decode failure, or any other uri
  gives a 512 px white-note placeholder (`#3B3B3B` background, `#FCFCFE` note at 0.4286 of the side, drawn once,
  immutable). The notification, lock screen and live media card use it. `PlaybackSessionCallback`'s metadata request
  (`isForPlayback = false`) attaches the placeholder PNG to the start item (Media3's legacy browse path decodes only
  `artworkData`), always the white note by user decision so no picture leaves through the recent root (rulings P12–P14).
  `buildPlaybackSession` assembles the loader, callback and session for both the service and its tests. Drawable `artwork_placeholder_note.xml`.
