---
type: decision
adr: "ADR-025"
status: active
date: 2026-11-09
source: "user, 2026-11-09 brainstorming; spec [[2026-11-09-custom-tags-design]] approved the same day"
tags: [tag-editor, custom-tags, storage]
related: ["[[Decisions]]", "[[Custom tags]]", "[[Custom tag system is cut]]", "[[Tag writes go through a cache copy]]", "[[2026-11-09-custom-tags-design]]"]
---

# Custom tags live in the file's ROLLA_TAGS field

**Decision:**
- Two flat lists, **tags** and **subtags**; a subtag is not a child of a tag. Each entry has a numeric ID and a name.
- A track's tag and subtag **IDs** are written into its own audio file, in a dedicated field `ROLLA_TAGS` (MP3 `TXXX`,
  FLAC Vorbis comment, M4A freeform atom under `com.apple.iTunes`), as `{"tags":"2,3","subTags":"1,3,4"}`. Never the
  comment field.
- The ID-to-name lists travel as their own JSON file (export/import); import replaces the list, with a clash warning
  when an ID would change its name on tracks.
- IDs are never reused on one phone; the counter also rises to the highest ID found in any file.
- Matching is AND everywhere; deleting an entry removes its ID from every file (one consent prompt).

**Amended in phase 1 (2026-11-10):** the app's jaudiotagger fork cannot write the M4A atom
([[jaudiotagger cannot write unknown M4A freeform atoms]]); **the user chose MP3 and FLAC only** for writing (M4A stays
readable, its editor rows are disabled), and left the existing M4A save bug in the backlog. IDs are capped at `MAX_CUSTOM_TAG_ID` = 999 999 999, so a
hand-edited file cannot overflow a list's counter (final review, SDD Ruling 10).

**Why:** the user wants the tags to survive a reinstall and be readable by other systems "with the same mechanism";
a dedicated field leaves other apps' comments alone.

**Rules out:** names inside the file; merging an imported list; OR search; tags on formats other than MP3, FLAC, M4A.

**Links:** design [[2026-11-09-custom-tags-design]], roadmap [[2026-11-09-custom-tags-roadmap]], feature
[[Custom tags]]. Replaces [[Custom tag system is cut]].
