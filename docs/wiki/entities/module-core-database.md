---
type: entity
summary: "Room: entities, DAOs, migrations and MusicDatabase (schema v5)."
aliases: [":core:database"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]"]
updated: 2026-11-09
---
# :core:database

Room: entities, DAOs, migrations and MusicDatabase (schema v5).

Room database: entities, DAOs, migrations, `MusicDatabase`. Schema v5 adds `playback_queue` / `playback_state`
([[playback-queue-restore]]). Its Hilt `DatabaseModule` lives here.

- **Owner:** data-layer-agent ([[agent-module-ownership]]).
- **Artwork:** the `artwork_uri` column is still written but ignored on read; the per-track value is derived in
  `SongEntity.toDomain()` ([[per-track-artwork]]).

## Related
[[claude-md]], [[ownership-map]], [[playback-queue-restore]], [[agent-module-ownership]], [[per-track-artwork]]
