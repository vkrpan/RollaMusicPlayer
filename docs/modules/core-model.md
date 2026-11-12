---
type: module
status: active
gradle: ":core:model"
owners: ["[[code-reviewer]]"]
tags: [module]
related: ["[[Modules]]", "[[Per-track artwork]]", "[[Song in code, Tracks on screen]]", "[[Model vocabulary]]"]
---

# core-model

`:core:model` — Domain models and enums, pure Kotlin: `Song`, `Album`, `Artist`, `Folder`, `Playlist`, `EqualizerPreset`, `SavedPlayback`, `RepeatMode`, `ShuffleMode`, `artistDisplayName`, and (since 2026-11-09, [[Custom tags]])
`CustomTagKind`, `CustomTag`, `CustomTagIds`, `MAX_CUSTOM_TAG_ID`, and (phase 3) `CUSTOM_TAG_NAME_MAX_LENGTH` (40 code
points; the one definition the repository, the list-file codec and the editors share) and `CustomTagListFile` (the
exported/imported lists). A shared contract: changes follow the model vocabulary and need code-reviewer and consumer review.

- **Owner agent(s):** [[code-reviewer]]
- **Features:** [[Per-track artwork]]
- **Decisions and rules:** [[Song in code, Tracks on screen]], [[Model vocabulary]],
  [[Artwork comes from the track's own file]]

## Changes
- 2026-11-08 (per-track artwork Task 1): `SongArtwork.kt` adds `SongArtworkRef(songId, version)`,
  `songArtworkUri(songId, version)` → `rolla-artwork://song/{id}?v={version}` and `parseSongArtworkUri(uri)` (strict:
  null for blank, MediaStore uris, a missing version or trailing text). `Song.artworkUri` now carries this per-track
  value.
- 2026-11-08 (per-track artwork Task 5): `Album`, `Artist` and `Folder` lose `artworkUri` (groups have no picture; [[Albums, artists and folders show the placeholder]]).
- 2026-11-12 (custom tags phase 4, Task 1): `SongCustomTags(tags, subTags)` and `SongFilter(title, artist, album,
  genre, year, tagIds, subTagIds)` with `isEmpty` (vocabulary rows in `.claude/rules/model-vocabulary.md`).
