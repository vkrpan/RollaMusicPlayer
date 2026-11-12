---
type: source
summary: "Custom tags phase 3 plan: the Manage tags screen, rename, delete with file clean-up, export/import of the tag lists, and editors that stay correct afterwards."
aliases: ["2026-11-11-custom-tags-phase3-managing"]
tags: [source, custom-tags, plan]
sources: []
updated: 2026-11-12
---
# Custom tags — phase 3 plan (managing)

Custom tags phase 3 plan: the Manage tags screen, rename, delete with file clean-up, export/import of the tag lists, and
editors that stay correct afterwards.

Raw source: `docs/superpowers/plans/2026-11-11-custom-tags-phase3-managing.md`; device-walked by the user 2026-11-12.

- Task 1: repository and DAO calls for delete, link counts, export, import preview and apply, `reserveIds`; the
  list-file codec `CustomTagListJson`; `insertNew` returns null when the list is full ([[module-core-data]],
  [[module-core-database]], [[module-core-tagio]], [[module-core-model]]).
- Task 2: the Manage tags ViewModel: rename, delete with write consent and per-file clean-up, "Updated X of N tracks."
  ([[module-feature-tageditor]]).
- Task 3: the Manage tags screen (Tags | Subtags, search, rows with track counts, dialogs) ([[module-core-ui]] reuses
  the name dialog).
- Task 4: export and import through the system file picker, with a clash warning before an import replaces the lists.
- Task 5: route `ManageCustomTags`, Home ⋮ "Manage tags", the picker's "Manage tags" link, tick pruning, ID
  reservation at the editor's load ([[module-app]]).
- Final review fix: the single editor reads `ROLLA_TAGS` from a fresh copy of the original at save and applies only the
  user's own tick changes, so an import never strips a ticked ID and a Manage delete is never written back.

## Mentions
[[custom-tags]], [[module-core-data]], [[module-core-database]], [[module-core-tagio]], [[module-core-model]],
[[module-core-ui]], [[module-feature-tageditor]], [[module-app]], [[song-not-track]]

## Related
[[custom-tags-roadmap]], [[custom-tags-phase2-plan]], [[custom-tags-spec]]
