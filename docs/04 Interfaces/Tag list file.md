---
type: interface
status: active
date: 2026-11-13
kind: file-format
code: "core/tagio/src/main/kotlin/com/rolla/musicplayer/core/tagio/CustomTagListJson.kt"
tags: [interface, custom-tags, tagio]
related: ["[[Interfaces Overview]]", "[[Custom tags]]", "[[ROLLA_TAGS field]]", "[[CustomTag]]", "[[core-tagio]]"]
---

# Tag list file

The custom tag and subtag lists (ID → name, the "enums"), exported and imported from Manage tags through a file the
user picks. Together with each file's [[ROLLA_TAGS field]] it lets another phone or another system name a song's tags.

## Format
```json
{"format":"rolla-tags","version":1,
 "tags":[{"id":2,"name":"Techno"}],
 "subTags":[{"id":1,"name":"Hard"}]}
```
- `format` must be `rolla-tags`, `version` 1.
- Names: trimmed, 1–40 code points (`CUSTOM_TAG_NAME_MAX_LENGTH`), unique per list ignoring case.
- IDs: 1 to `MAX_CUSTOM_TAG_ID`, unique per list.

## Behaviour
- **Read strictly:** a file that breaks any rule is refused whole. An import replaces both lists, so it is never
  half-applied (unlike `ROLLA_TAGS`, which drops a bad entry).
- **Import replaces** the phone's lists. When an imported ID names something different and tracks carry it, the
  confirmation lists each clash ("2: Techno → Jazz, on 14 tracks") ([[Custom tags]]).
- After an import the ID counters rise to cover every imported ID, so a freed ID is never issued again.

## Implemented in
[[core-tagio]] → `CustomTagListJson`; [[core-data]] → `CustomTagRepository.exportLists`, `previewImport`,
`applyImport`; the Manage tags screen in [[feature-tageditor]].
