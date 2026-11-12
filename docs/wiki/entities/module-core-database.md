---
type: entity
summary: "Room: entities, DAOs, migrations and MusicDatabase (schema v7)."
aliases: [":core:database"]
tags: [module]
sources: ["[[sources/claude-md]]", "[[sources/ownership-map]]", "[[sources/custom-tags-phase1-plan]]", "[[sources/custom-tags-phase2-plan]]", "[[sources/custom-tags-phase3-plan]]", "[[sources/custom-tags-phase4-plan]]"]
updated: 2026-11-12
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
- **Custom tags phase 3:** `CustomTagDao` gains entry delete, list replacement (links kept, counters only rise), link
  counts, and `insertNew` returning null when full; no schema change, still v7 ([[custom-tags-phase3-plan]]).
- **Custom tags phase 4:** a flow of every song–tag link, unknown IDs included; no schema change ([[custom-tags-phase4-plan]]).

## Related
[[claude-md]], [[ownership-map]], [[playback-queue-restore]], [[agent-module-ownership]], [[per-track-artwork]]
