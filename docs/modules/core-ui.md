---
type: module
status: active
gradle: ":core:ui"
owners: ["[[ui-builder]]"]
tags: [module]
related: ["[[Modules]]", "[[Home and library]]", "[[Per-track artwork]]", "[[Build UI from tokens and the kit]]"]
---

# core-ui

`:core:ui` — Model-aware shared composables: `SongListItem`, `AlbumRow`, `ArtistRow`, `FolderRow`, `PlaylistRow` on `ListRowScaffold`, `AlbumCard`, `FeatureCard`, `DetailArtworkHeader`, `ArtworkThumbnail`, `SongOptionsHost`, `SongSelectionState`, `tracksCountLabel`.

- **Owner agent(s):** [[ui-builder]]
- **Features:** [[Home and library]], [[Per-track artwork]]
- **Decisions and rules:** [[Build UI from tokens and the kit]]

## Changes
- 2026-11-08 (per-track artwork Task 5): `AlbumCard`, `AlbumRow`, `ArtistRow`, `FolderRow` and `DetailArtworkHeader` take no artwork and always
  draw the placeholder (new shared `PlaceholderThumbnail`); `SongListItem`'s thumbnail gets `SONG_ROW_ARTWORK_TEST_TAG`.
  `SongListItem.trackNumber` / `TrackNumberLabel` now have no production caller (backlog).
- 2026-11-10 (custom tags phase 2, Task 2): `customtags/CustomTagPickerState.kt` — the picker's immutable state and
  pure helpers (`filterCustomTags`: contains-match ignoring case and accents via NFD + stripped marks; it does not fold
  Ø, Æ, Ł, Đ, ß or compatibility forms, unlike the library collator; `toggled`, `selectedNames`). See [[Custom tags]].
- 2026-11-10 (custom tags phase 2, Task 3): stateless `CustomTagPickerSheet` (Tags | Subtags pager under `OneUiTabRow`,
  search, "New tag" row, toggle rows, empty states, Done; the owner holds `state.page`), `CustomTagRows` (two rows with
  `LabelChip`s in a `FlowRow`, or "None"; disabled rows show and announce "Tags can be saved to MP3 and FLAC files
  only."), `CustomTagNameDialog` + `CustomTagNameDialogState`.
