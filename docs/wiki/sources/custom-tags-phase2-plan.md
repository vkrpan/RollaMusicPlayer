---
type: source
summary: "Custom tags phase 2 plan: the Room v7 read guard, the picker sheet, and tags in the single and batch tag editors."
aliases: ["2026-11-10-custom-tags-phase2-assigning"]
tags: [source, custom-tags, plan]
sources: []
updated: 2026-11-11
---
# Custom tags — phase 2 plan (assigning)

Custom tags phase 2 plan: the Room v7 read guard, the picker sheet, and tags in the single and batch tag editors.

Raw source: `docs/superpowers/plans/2026-11-10-custom-tags-phase2-assigning.md`; device-walked by the user
2026-11-11.

- Task 1: Room v7 `songs.file_tags_generation`; `FileTagPass` applies a read only if the row was not reset since it was
  queried, so a stale background read never overwrites a fresh save ([[module-core-database]], [[module-core-data]]).
- Task 2: the picker's pure state and accent-insensitive search ([[module-core-ui]]).
- Task 3: `CustomTagPickerSheet`, `CustomTagRows` and `CustomTagNameDialog` in [[module-core-ui]]; the kit's
  display-only `LabelChip` in [[module-core-designsystem]].
- Task 4: the single editor writes the ticks only when they changed, keeping unknown IDs from the copy
  ([[module-feature-tageditor]]).
- Task 5: the batch editor adds tags to every selected MP3/FLAC; unwritable formats fail before any write.
- Final fix: both editors share `CustomTagEditing.kt`.

## Mentions
[[custom-tags]], [[module-core-database]], [[module-core-data]], [[module-core-ui]], [[module-core-designsystem]],
[[module-feature-tageditor]], [[one-ui-design-system]]

## Related
[[custom-tags-roadmap]], [[custom-tags-phase1-plan]]
