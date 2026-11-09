---
type: guide
status: active
source: ".claude/rules/media3-playback.md"
tags: [guide, playback, media3]
related: ["[[Guides]]", "[[Playback and queue]]", "[[core-media]]", "[[audio-engineer]]"]
---

# Media3 playback

**Source of truth:** `.claude/rules/media3-playback.md` (kept there so the harness loads it). This note carries only the must-obeys; read the
source before work in its area.

- Audio attributes `AUDIO_CONTENT_TYPE_MUSIC` / `USAGE_MEDIA` with audio focus; `WAKE_MODE_LOCAL`.
- Complete `MediaItem`s with metadata; prepare after setting items; handle every player state and error.
- Media3's `DefaultMediaNotificationProvider` owns the notification (the guide's sample is reference only).
- Release player, session and effects in `onDestroy`.
