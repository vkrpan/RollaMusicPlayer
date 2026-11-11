---
type: source
summary: "Custom tags phase 1 plan: the ROLLA_TAGS field IO, Room v6, the scan's file pass and the tag repository."
aliases: ["2026-11-09-custom-tags-phase1-foundation"]
tags: [source, custom-tags, plan]
sources: []
updated: 2026-11-11
---
# Custom tags — phase 1 plan (foundation)

Custom tags phase 1 plan: the ROLLA_TAGS field IO, Room v6, the scan's file pass and the tag repository.

Raw source: `docs/superpowers/plans/2026-11-09-custom-tags-phase1-foundation.md`; committed `dce76dd`.

- Task 1: [[module-core-tagio]] created; the shared jaudiotagger helpers and the audio test fixtures moved down from
  [[module-feature-tageditor]].
- Task 2: `CustomTagKind`, `CustomTag`, `CustomTagIds` in [[module-core-model]]; the `RollaTagsJson` codec.
- Task 3: `RollaTagsFileIo` — read/write on MP3 and FLAC; M4A read-only (the jaudiotagger fork cannot write unknown
  freeform atoms).
- Task 4: Room v6 — tag lists, per-kind counters that only rise, song links, `genre`, `file_tags_read`
  ([[module-core-database]]).
- Task 5: `FileTagPass` reads tags and genre from new or changed files in the background, never blocking or crashing
  ([[module-core-data]]).
- Task 6: `CustomTagRepository` (create, rename, observe); final fix: the ID ceiling `MAX_CUSTOM_TAG_ID` = 999 999 999.
- Device: Room 117/117 on the Motorola; first pass read 91 files in 3 s.

## Mentions
[[custom-tags]], [[module-core-tagio]], [[module-core-model]], [[module-core-database]], [[module-core-data]],
[[module-core-testing]], [[module-feature-tageditor]]

## Related
[[custom-tags-roadmap]], [[custom-tags-phase2-plan]]
