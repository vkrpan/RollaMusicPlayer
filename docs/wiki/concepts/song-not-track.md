---
type: concept
summary: "The audio item is `Song` in code; UI copy says Tracks."
aliases: ["Song vs Track", "Model vocabulary", "Song in code, Tracks on screen"]
tags: [vocabulary]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# Song in code, Tracks on screen

The audio item is `Song` in code; UI copy says Tracks.

- **Code:** the audio item model is `Song` (`songs`, `songId`, `SongListItem`, routes, tests, logs), never `Track`
  ([[core-model-governance]]).
- **UI copy:** says "Tracks" (the Home tab, counts like "12 tracks", "Shuffle all tracks"), never "Songs".
- **Not renamed:** `trackNumber`, Media3's `Tracks` / `TRACK_TYPE_*`, `AudioTrack`, `MediaStore.Audio.Media.TRACK`.
- Other copy rules: British spelling, "Rolla Music", "Equaliser" in the UI ([[module-feature-equalizer]]).

## Related
[[claude-md]], [[ownership-map]], [[core-model-governance]], [[module-feature-equalizer]]
