---
type: module
status: active
gradle: ":core:tagio"
owners: ["[[tag-editor-agent]]"]
tags: [module, tag-editor, custom-tags]
related: ["[[Modules]]", "[[Custom tags]]", "[[Tag editor]]", "[[jaudiotagger and ID3v1-only MP3s]]", "[[jaudiotagger cannot write unknown M4A freeform atoms]]"]
---

# core-tagio

`:core:tagio` — all jaudiotagger code shared below the feature layer (created by custom tags phase 1, 2026-11-09).
Depends only on `:core:model`; exposes jaudiotagger through `api`.

- **`TagAccess.kt`:** `AudioFile.editableTag()`, `Tag.hasAnyArtwork()`, `Tag.deleteEveryArtwork()`,
  `Tag.setFieldIfNotBlank()` (moved from [[feature-tageditor]]; rulings P16–P19 in their KDoc).
- **`RollaTagsJson`:** the `ROLLA_TAGS` codec, `{"tags":"2,3","subTags":"1,3,4"}`; tolerant decode (null for non-objects
  and malformed JSON; only positive ASCII-digit IDs kept).
- **`RollaTagsFileIo`:** `read(file): FileTags(customTagIds, genre)` (never writes) and `writeCustomTagIds(file, ids)`
  (MP3 `TXXX`, FLAC Vorbis comment; empty ids remove the field). M4A is **read-only**: read through the jcodec parser
  bundled in the fork jar; writing throws ([[jaudiotagger cannot write unknown M4A freeform atoms]]).
- **Format checks:** `isCustomTagExtension` (mp3, flac, m4a: readable), `canWriteCustomTags` (mp3, flac),
  `File.supportsCustomTags()`.
- **Test fixtures:** `com.rolla.musicplayer.core.testing.audio` in [[core-testing]] (`AudioFixtures`, `M4aFixture`,
  which can build freeform atoms).
- **Owner agent(s):** [[tag-editor-agent]]
- **Features:** [[Custom tags]], [[Tag editor]]
- **Decisions and rules:** [[Custom tags live in the file's ROLLA_TAGS field]], [[Tag writes go through a cache copy]]

## Changes
- 2026-11-09 (custom tags phase 1, Tasks 1–3): module created; helpers and fixtures moved; codec and field IO added.
- 2026-11-10 (final fix wave): `decode` drops IDs above `MAX_CUSTOM_TAG_ID` (999 999 999, Ruling 10); a static
  companion sets the `org.jaudiotagger` logger to WARNING once (R8 keep rule still owed, [[Backlog]]); the write refusal
  names the Mp4TagCreator reason only for M4A; `@throws` KDoc on `read` and `writeCustomTagIds`.
