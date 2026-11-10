---
type: entity
summary: "Media3 playback: PlaybackService, MediaSession, the queue, queue restore and the equaliser effect."
aliases: [":core:media"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:media

Media3 playback: PlaybackService, MediaSession, the queue, queue restore and the equaliser effect.

Media3 1.11.1: `PlaybackService` (a `MediaLibraryService`), `MediaSession`, `PlaybackController`, the queue
(`QueueState`/`QueuePublisher`), queue save/restore ([[playback-queue-restore]]), the audio session and the
equaliser effect. `SongArtworkBitmapLoader` gives the notification, lock screen and media card each track's own
picture ([[per-track-artwork]]).

- **Owners:** audio-engineer (playback), equalizer-agent (`equalizer/**`) ([[agent-module-ownership]]).
- Never depends on [[module-feature-widget]]; the widget is updated through the `PlaybackUpdateHook` seam.

## Related
[[claude-md]], [[ownership-map]], [[playback-queue-restore]], [[per-track-artwork]], [[agent-module-ownership]], [[module-feature-widget]]
