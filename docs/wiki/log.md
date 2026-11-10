---
type: log
summary: "Append-only record of every ingest, query and lint."
updated: 2026-11-09
---

# Wiki log

## [2026-11-09] ingest | CLAUDE.md
- Created: [[claude-md]]; entities [[module-app]], [[module-baselineprofile]], [[module-build-logic]], [[module-core-common]], [[module-core-data]], [[module-core-database]], [[module-core-datastore]], [[module-core-designsystem]], [[module-core-media]], [[module-core-model]], [[module-core-permissions]], [[module-core-testing]], [[module-core-ui]], [[module-feature-equalizer]], [[module-feature-library]], [[module-feature-player]], [[module-feature-playlists]], [[module-feature-search]], [[module-feature-settings]], [[module-feature-tageditor]], [[module-feature-widget]]; concepts
  [[agent-module-ownership]], [[core-model-governance]], [[development-history]], [[knowledge-vault]], [[llm-wiki]], [[multi-module-architecture]], [[offline-privacy-architecture]], [[one-ui-design-system]], [[per-track-artwork]], [[playback-queue-restore]], [[song-not-track]], [[tech-stack]].
- Contradictions: CLAUDE.md "Use Material Design 3 components throughout" vs the kit-only rule
  ([[one-ui-design-system]]); Current Status's "last commit 15821b4" ([[development-history]]). Checked and not
  found: `:core:common` as "Dispatchers, Result types" ([[module-core-common]]).

## [2026-11-09] ingest | docs/agents/Ownership map.md (formerly OWNERSHIP.md)
- Created: [[ownership-map]], [[core-model-governance]]; updated [[agent-module-ownership]] and every module
  entity's `sources:`.
- Contradiction: the map's `:app/.../di/**` rows vs the code (no `di/` in `:app`; DI modules live in
  [[module-core-database]] and [[module-core-datastore]]) — see [[module-app]].
