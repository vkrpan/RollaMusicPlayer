---
type: interface
status: active
date: 2026-11-13
kind: file-format
code: "core/tagio/src/main/kotlin/com/rolla/musicplayer/core/tagio/RollaTagsFileIo.kt"
tags: [interface, custom-tags, tagio]
related: ["[[Interfaces Overview]]", "[[Custom tags]]", "[[Custom tags live in the file's ROLLA_TAGS field]]", "[[core-tagio]]", "[[Tag list file]]"]
---

# ROLLA_TAGS field

The custom tag IDs a song carries, written into the audio file itself so they survive a reinstall and any other
system can read them ([[Custom tags live in the file's ROLLA_TAGS field]]).

## Value
JSON, ASCII, two comma-separated ID lists (`RollaTagsJson`):

```json
{"tags":"2,3","subTags":"1,3,4"}
```

IDs only; the names live in the [[Tag list file]] and in Room ([[CustomTag]]). Each list issues its own IDs, so a tag
and a subtag can share a number. IDs run from 1 to `MAX_CUSTOM_TAG_ID` (999 999 999). A bad entry is dropped and the
rest kept.

## Where it lives per format
| Format | Field | Read | Write |
|---|---|---|---|
| MP3 | ID3v2 user-defined text frame `TXXX` (`TXX` in ID3v2.2), description `ROLLA_TAGS`, ISO-8859-1 | yes | yes |
| FLAC | Vorbis comment `ROLLA_TAGS` | yes | yes |
| M4A | freeform atom `----:com.apple.iTunes:ROLLA_TAGS` (read with the jcodec MP4 parser) | yes | **no** |
| WAV, OGG, AAC | — | no | no |

## Implemented in
[[core-tagio]] → `RollaTagsFileIo` (read/write), `RollaTagsJson` (codec). Read by the scan's file pass
([[Data Flow]]); written by the tag editors and by Manage tags delete ([[Tag editor]], [[Custom tags]]).

## Gotchas
- M4A is read-only: the jaudiotagger fork drops freeform atoms its `Mp4FieldKey` enum does not list
  ([[jaudiotagger cannot write unknown M4A freeform atoms]]).
- A write deletes every existing `ROLLA_TAGS` field first, so a file ends with exactly one.
- An unreadable value and an absent one currently look the same ([[Known Issues]]).
