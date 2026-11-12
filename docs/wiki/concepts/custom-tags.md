---
type: concept
summary: "User-defined tags and subtags whose IDs live in each audio file's ROLLA_TAGS field and are indexed in Room."
aliases: ["custom tags", "subtags", "ROLLA_TAGS"]
tags: [concept, custom-tags]
sources: ["[[sources/custom-tags-spec]]", "[[sources/custom-tags-roadmap]]", "[[sources/custom-tags-phase1-plan]]", "[[sources/custom-tags-phase2-plan]]", "[[sources/custom-tags-phase3-plan]]", "[[sources/claude-md]]"]
updated: 2026-11-12
---
# Custom tags

User-defined tags and subtags whose IDs live in each audio file's ROLLA_TAGS field and are indexed in Room.

- **Model:** `CustomTagKind { TAG, SUBTAG }`, `CustomTag(kind, id, name)`, `CustomTagIds(tagIds, subTagIds)`,
  `MAX_CUSTOM_TAG_ID` ([[module-core-model]]). A custom tag is a user label, not an ID3 field ([[song-not-track]]
  vocabulary rules apply).
- **In the file:** `ROLLA_TAGS` = `{"tags":"2,3","subTags":"1,3,4"}`, written to MP3 and FLAC only; M4A is read
  (the jaudiotagger fork cannot write unknown freeform atoms); other formats are neither ([[module-core-tagio]]).
- **In Room:** the two lists, per-kind counters that only rise (IDs are never reused, and rise to the highest ID found in
  a file), song links that keep unknown IDs, and a per-row read generation (v7) ([[module-core-database]]).
- **Scan:** `FileTagPass` reads tags and genre from unread files in the background, newest first; a read is applied
  only if the row's generation still matches ([[module-core-data]]).
- **Editing:** a picker sheet with Tags/Subtags tabs and search ([[module-core-ui]]); the single editor writes only
  when the ticks change, the batch editor only adds ([[module-feature-tageditor]]).
- **Managing (phase 3):** a Manage tags screen (from Home ⋮ and the picker) renames, deletes a tag and removes its ID
  from every MP3/FLAC carrying it after write consent, and exports or imports both lists as one JSON file; an import
  replaces the lists after a clash warning. Editors stay correct afterwards: ticks are pruned to known tags, and the
  single editor applies only the user's own tick changes to the file's current field ([[custom-tags-phase3-plan]]).
- **Not yet built:** Find tracks, chips in lists and on Now Playing ([[custom-tags-roadmap]]).

## Related
[[custom-tags-spec]], [[custom-tags-roadmap]], [[custom-tags-phase3-plan]], [[module-core-tagio]], [[module-feature-tageditor]],
[[offline-privacy-architecture]], [[development-history]]

## Disagreements & open questions
- Resolved in phase 3: an open editor's stale copy after Manage tags (the save reads the original's field afresh) and
  IDs that unread files carry (`reserveIds`). Open: the deferred minors in the vault's Backlog.
