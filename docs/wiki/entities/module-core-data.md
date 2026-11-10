---
type: entity
summary: "Repositories, the MediaStore scanner and the per-track artwork cache."
aliases: [":core:data"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:data

Repositories, the MediaStore scanner and the per-track artwork cache.

Repositories, the MediaStore scanner (`MediaScanner`, `LibraryIndexer`: incremental sync, mutex-serialised manual
rescan) and `SongArtworkCache`, the per-track artwork disk cache ([[per-track-artwork]]).

- **Owners:** data-layer-agent (repositories, cache), media-scanning-agent (`scanner/**`)
  ([[agent-module-ownership]]).
- **Cross-feature flows** go through here or through navigation, never feature to feature
  ([[multi-module-architecture]]).

## Related
[[claude-md]], [[ownership-map]], [[per-track-artwork]], [[agent-module-ownership]], [[multi-module-architecture]]
