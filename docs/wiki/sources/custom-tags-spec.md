---
type: source
summary: "The approved design for user-defined tags and subtags stored in each audio file's ROLLA_TAGS field."
aliases: ["2026-11-09-custom-tags-design", "custom tags design"]
tags: [source, custom-tags, spec]
sources: []
updated: 2026-11-11
---
# Custom tags — design (spec)

The approved design for user-defined tags and subtags stored in each audio file's ROLLA_TAGS field.

Raw source: `docs/superpowers/specs/2026-11-09-custom-tags-design.md` (approved by the user 2026-11-09).

- **Two flat lists:** tags and subtags; a subtag is not a child of a tag. Each entry has a numeric ID and a name.
- **Storage:** a track's IDs live in its own file, field `ROLLA_TAGS`, as `{"tags":"2,3","subTags":"1,3,4"}` (MP3
  `TXXX`, FLAC Vorbis comment, M4A freeform atom under `com.apple.iTunes`); never the comment field. The ID → name lists
  travel as their own JSON file (export/import; import replaces, with a clash warning).
- **Rules:** AND everywhere; IDs never reused; unknown IDs kept; names trimmed, 1–40 characters, unique per list
  ignoring case; deleting an entry removes its ID from every file (one consent prompt).
- **Screens:** the tag editor's Tags/Subtags rows open one picker sheet (tabs, search, checklist, "New tag"); the batch
  editor only adds; Find tracks (filter chips over live results, whole result as the queue); chips in track lists and
  on Now Playing; a Manage tags screen.
- **Anti-goals:** never write the comment or a standard field, never lose an existing tag on save, never write without
  consent, never a silently wrong tag, never a scan that is slower on every launch, no network.

## Mentions
[[custom-tags]], [[module-core-tagio]], [[module-core-database]], [[module-core-data]], [[module-feature-tageditor]],
[[module-core-ui]], [[song-not-track]], [[offline-privacy-architecture]], [[custom-tags-roadmap]]

## Related
[[custom-tags]], [[custom-tags-roadmap]]
