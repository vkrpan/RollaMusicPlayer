---
type: entity
status: active
date: 2026-11-13
table: "custom_tags, custom_tag_counters, song_custom_tags"
model: "core/model (CustomTag, CustomTagKind, CustomTagIds)"
code: "core/database/src/main/kotlin/com/rolla/musicplayer/core/database/entity/CustomTagEntity.kt"
tags: [data, entity, custom-tags]
related: ["[[Database Schema]]", "[[Custom tags]]", "[[ROLLA_TAGS field]]", "[[Tag list file]]", "[[Song]]"]
---

# CustomTag

A user-defined label, in one of two independent lists: `TAG` and `SUBTAG` (`CustomTagKind`). Not an ID3 field.
Room holds a copy rebuilt from the files: each file's [[ROLLA_TAGS field]] is the record of what a song carries.

## Fields
| Column | Type | Null | Meaning |
|---|---|---|---|
| `custom_tags.kind` | TEXT | no | `TAG` or `SUBTAG` |
| `custom_tags.id` | INTEGER | no | unique within its kind only; key is (`kind`, `id`) |
| `custom_tags.name` | TEXT | no | 1–40 code points, trimmed |
| `custom_tags.name_key` | TEXT | no | normalised name; unique per kind (no duplicates ignoring case) |
| `custom_tag_counters.kind` | TEXT | no | |
| `custom_tag_counters.highest_id` | INTEGER | no | highest ID ever issued or seen in a file; only rises |
| `song_custom_tags.song_id` | TEXT | no | FK → `songs.id`, cascade |
| `song_custom_tags.kind`, `tag_id` | TEXT, INTEGER | no | **no FK**: a file may carry an ID the list does not know |

Models: `CustomTag(kind, id, name)`, `CustomTagIds(tagIds, subTagIds)`, `SongCustomTags`, `SongFilter`
([[Model vocabulary]]).

## Invariants
- An ID freed by a delete is never issued again; imports and file reads raise the counter to the highest ID they
  carry. IDs stay at or below `MAX_CUSTOM_TAG_ID` (999 999 999).
- Deleting a tag removes its ID from every file that carries it (Manage tags, with write consent).

## Read and written by
`CustomTagRepository` and `SongFinder` ([[core-data]]); the file pass; the tag editors and Manage tags
([[feature-tageditor]]); Find tracks and the chips ([[feature-search]], [[core-ui]]).
