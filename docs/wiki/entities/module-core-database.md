---
type: entity
summary: "Room: entities, DAOs, migrations and MusicDatabase (schema v7)."
aliases: [":core:database"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase1-plan]]", "[[sources/custom-tags-phase2-plan]]"]
updated: 2026-11-11
---
# :core:database

Room: entities, DAOs, migrations and MusicDatabase (schema v7).

Room database: entities, DAOs, migrations, `MusicDatabase`. v5 added `playback_queue` / `playback_state`
([[playback-queue-restore]]); v6 the custom tag lists, per-kind ID counters, song links, `songs.genre` and
`songs.file_tags_read`; v7 `songs.file_tags_generation`, so `CustomTagDao.applyFileTags` writes a background read only
if the row was not reset since it was queried ([[custom-tags]]). Its Hilt `DatabaseModule` lives here.

- **Owner:** data-layer-agent ([[agent-module-ownership]]).
- **Artwork:** the `artwork_uri` column is still written but ignored on read; the per-track value is derived in
  `SongEntity.toDomain()` ([[per-track-artwork]]).

## Related
[[claude-md]], [[ownership-map]], [[playback-queue-restore]], [[agent-module-ownership]], [[per-track-artwork]]
