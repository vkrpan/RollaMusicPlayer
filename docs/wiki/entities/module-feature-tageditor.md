---
type: entity
summary: "Tag reading and writing (MP3, FLAC, M4A), scoped-storage consent, single and batch editors."
aliases: [":feature:tageditor"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase1-plan]]", "[[sources/custom-tags-phase2-plan]]", "[[sources/custom-tags-phase3-plan]]"]
updated: 2026-11-12
---
# :feature:tageditor

Tag reading and writing (MP3, FLAC, M4A), scoped-storage consent, single and batch editors.

ID3 / Vorbis / MP4 tag IO (jaudiotagger), scoped-storage write consent, the single-track and batch editors, Change and
Remove artwork, and Set/Remove artwork for a Tracks-tab selection ([[per-track-artwork]]).

- **Owners:** tag-editor-agent (IO, consent), ui-builder (screens) ([[agent-module-ownership]]).
- **Shared tag helpers** moved to [[module-core-tagio]] (2026-11-09).
- **Custom tags** (2026-11-10): both editors host the picker. The single editor writes `ROLLA_TAGS` only when the
  ticks changed, keeping unknown IDs read from the editable copy; the batch editor adds the chosen tags to every MP3 or
  FLAC and never removes; other formats show disabled rows ([[custom-tags]]).
- **Manage tags (custom tags phase 3):** package `managetags` (screen, ViewModel, SAF list-file IO). The editors link
  to it from the picker, prune ticks to the known tags on return, and the single editor saves only the user's own tick
  changes onto the file's current `ROLLA_TAGS` ([[custom-tags-phase3-plan]], [[custom-tags]]).

## Related
[[claude-md]], [[ownership-map]], [[per-track-artwork]], [[agent-module-ownership]]
