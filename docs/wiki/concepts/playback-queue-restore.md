---
type: concept
summary: "Queue, position, shuffle and repeat are saved in Room and restored paused on the next start."
aliases: ["Persistent playback queue"]
tags: [playback]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-09
---
# Playback queue restore

Queue, position, shuffle and repeat are saved in Room and restored paused on the next start.

The queue, current track, position, shuffle order and repeat mode are saved in Room (v5 `playback_queue` /
`playback_state`, [[module-core-database]]) and restored, paused, on the next start ([[module-core-media]]). Play from a headset or
Bluetooth resumes them without opening the app.

## Related
[[claude-md]], [[module-core-database]], [[module-core-media]]

## Disagreements & open questions
- The System UI resume card after process death is designed for stock Android (AOSP `MediaResumeListener`) and is
  **not yet device-verified**. Samsung One UI never adds new apps to its resume list, so there the card disappears.
