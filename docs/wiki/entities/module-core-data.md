---
type: entity
summary: "Repositories, the MediaStore scanner and the per-track artwork cache."
aliases: [":core:data"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase1-plan]]", "[[sources/custom-tags-phase2-plan]]"]
updated: 2026-11-11
---
# :core:data

Repositories, the MediaStore scanner and the per-track artwork cache.

Repositories, the MediaStore scanner (`MediaScanner`, `LibraryIndexer`: incremental sync, mutex-serialised manual
rescan) and `SongArtworkCache`, the per-track artwork disk cache ([[per-track-artwork]]).

- **Custom tags:** `FileTagPass` reads `ROLLA_TAGS` and the genre from unread files in batches of 50, in the
  background (requested after the MediaStore diff, coalesced, never blocking or crashing), applying a read only if the
  row's generation still matches; `SongFileSource` reads a file in place or through a cache copy;
  `CustomTagRepository` creates, renames and observes the lists and a song's IDs ([[custom-tags]],
  [[module-core-tagio]]).

- **Owners:** data-layer-agent (repositories, cache), media-scanning-agent (`scanner/**`)
  ([[agent-module-ownership]]).
- **Cross-feature flows** go through here or through navigation, never feature to feature
  ([[multi-module-architecture]]).

## Related
[[claude-md]], [[ownership-map]], [[per-track-artwork]], [[agent-module-ownership]], [[multi-module-architecture]]
