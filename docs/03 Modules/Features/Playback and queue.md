---
type: feature
status: active
date: 2026-10-17
source: "v1 Phase 1–2; queue restore, Media3 upgrade, panel resume (2026-10-12 to 2026-10-17)"
tags: [playback, feature]
related: ["[[Features]]", "[[Playback queue restore]]", "[[Media3 1.11 upgrade]]", "[[Panel resume targets stock Android]]", "[[Now Playing and mini-player]]", "[[core-media]]", "[[audio-engineer]]"]
---

# Playback and queue

- **Service:** `PlaybackService`, a Media3 `MediaLibraryService` with a `MediaSession`, ExoPlayer, audio focus and
  wake lock. It runs as a foreground service with Media3's default notification.
- **Controls:** shuffle (one exact order is kept), repeat off/all/one, speed and skip silence from Settings
  (`PlaybackSettingsBinder`). A track tap plays the whole visible list from that track.
- **Queue:** `QueueState`/`QueuePublisher` drive the queue sheet.
- **Restore:** the queue is saved and restored paused ([[Playback queue restore]]). A headset or Bluetooth Play
  resumes it.
- **System UI card after process death:** [[Panel resume targets stock Android]]. It is not delivered on One UI.
- **Equaliser:** attached to the audio session ([[Equaliser]]).
- **Module:** [[core-media]]. **Owner:** [[audio-engineer]]. **Rules:** [[Media3 playback]].
- **Specs:** `2026-10-12-queue-restore-design.md`, `2026-10-14-media3-upgrade-design.md`,
  `2026-10-15-panel-resume-design.md`.
