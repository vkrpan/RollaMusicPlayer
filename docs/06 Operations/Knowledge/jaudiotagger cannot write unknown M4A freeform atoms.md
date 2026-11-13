---
type: knowledge
status: active
date: 2026-11-09
source: "custom tags phase 1, Task 3 (SDD ledger Ruling 5): contract test plus javap of the Adonai jaudiotagger 2.3.15 fork"
tags: [tag-editor, jaudiotagger, m4a, custom-tags]
related: ["[[Knowledge]]", "[[Custom tags]]", "[[jaudiotagger and ID3v1-only MP3s]]", "[[Tag editor]]", "[[2026-11-09-custom-tags-design]]"]
---

# jaudiotagger cannot write unknown M4A freeform atoms

**Symptom:** writing an M4A freeform field the library does not know (`----:com.apple.iTunes:ROLLA_TAGS`) fails at
`commit()` with `CannotWriteException … NoSuchElementException: No value present`.

**Cause (Adonai fork, 2.3.15):** `Mp4TagCreator.convert` calls `Mp4FieldKey.byFieldName(field.id)` for every MP4
field and takes the atom's issuer and name from that enum entry, so only freeform atoms listed in `Mp4FieldKey` can be
written. The reader has the same limit: it keeps only the reverse-DNS keys `Mp4FieldKey` lists, so unknown freeform
atoms are invisible through jaudiotagger's API. `:core:tagio` therefore reads M4A `ROLLA_TAGS` with the jcodec MP4
parser bundled inside the fork jar (udta → meta → `getRdnsMeta`); those classes are internal to the fork.

**Consequences:**
- [[Custom tags]]: MP3 (`TXXX`) and FLAC (Vorbis comment) read and write `ROLLA_TAGS`; M4A is read-only until a
  decision before phase 2 (own MP4 writer, a fork upgrade or patch, or leaving M4A out).
- **Existing data loss (pinned by `RollaTagsFileIoTest.m4a_unknownFreeformAtom_isDroppedByCommit`):** any jaudiotagger
  commit on an M4A, including the [[Tag editor]]'s text and artwork saves today, silently drops every freeform atom
  `Mp4FieldKey` does not list (other apps' custom fields, and `ROLLA_TAGS`). Backlog item.

**Rule:** test any new tag field through a real `commit()` per format before designing around it.
