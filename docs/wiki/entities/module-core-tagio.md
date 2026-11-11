---
type: entity
summary: "jaudiotagger IO shared below the feature layer: the tag helpers and the ROLLA_TAGS custom-tag field."
aliases: [":core:tagio", "RollaTagsFileIo", "RollaTagsJson"]
tags: [module, custom-tags]
sources: ["[[sources/claude-md]]", "[[sources/custom-tags-phase1-plan]]", "[[sources/custom-tags-phase2-plan]]"]
updated: 2026-11-11
---
# :core:tagio

jaudiotagger IO shared below the feature layer: the tag helpers and the ROLLA_TAGS custom-tag field.

- **Owner:** tag-editor-agent ([[agent-module-ownership]]). Depends only on [[module-core-model]].
- `TagAccess.kt`: `editableTag()` (builds an ID3v2 tag from an ID3v1-only MP3), `hasAnyArtwork`, `deleteEveryArtwork`,
  `setFieldIfNotBlank` — moved here from [[module-feature-tageditor]].
- `RollaTagsJson`: the codec for `{"tags":"…","subTags":"…"}`; tolerant decode, IDs capped at `MAX_CUSTOM_TAG_ID`.
- `RollaTagsFileIo`: `read(file)` (never writes) and `writeCustomTagIds(file, ids)` (MP3 `TXXX`, FLAC Vorbis comment;
  empty IDs remove the field; M4A refused); jaudiotagger logging set to WARNING.
- Format checks: `isCustomTagExtension` (mp3, flac, m4a), `canWriteCustomTags` (mp3, flac).

## Related
[[custom-tags]], [[module-core-data]], [[module-feature-tageditor]], [[multi-module-architecture]]
