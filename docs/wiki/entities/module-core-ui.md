---
type: entity
summary: "Model-aware shared composables: song rows, album cards, list rows, the song options sheet."
aliases: [":core:ui"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase2-plan]]", "[[sources/custom-tags-phase3-plan]]", "[[sources/custom-tags-phase4-plan]]"]
updated: 2026-11-12
---
# :core:ui

Model-aware shared composables: song rows, album cards, list rows, the song options sheet.

Composables that know the models: `SongListItem`, `AlbumRow`/`ArtistRow`/`FolderRow`/`PlaylistRow`, `AlbumCard`,
`FeatureCard`, `DetailArtworkHeader`, `SongOptionsHost`, `SongSelectionState`. Built from [[module-core-designsystem]].

- **Owner:** ui-builder ([[agent-module-ownership]]).
- Groups (albums, artists, folders) draw the placeholder; song rows draw the track's own picture
  ([[per-track-artwork]]).
- **Custom tags** (`customtags/`): the stateless `CustomTagPickerSheet` (Tags | Subtags tabs, search, "New tag",
  checklist), `CustomTagRows` (chips that wrap; disabled rows announce why) and `CustomTagNameDialog`; the picker's pure
  state and accent-insensitive search ([[custom-tags]]).
- **Custom tags phase 3:** `CustomTagPickerSheet.onManageClick` shows a "Manage tags" link; the name dialog takes a
  title and confirm label for rename ([[custom-tags-phase3-plan]]).
- **Custom tags phase 4:** `SongListItem` shows tag chips from `LocalSongCustomTags`; `SongCustomTagChips` ([[custom-tags-phase4-plan]]).

## Related
[[claude-md]], [[ownership-map]], [[module-core-designsystem]], [[agent-module-ownership]], [[per-track-artwork]]
