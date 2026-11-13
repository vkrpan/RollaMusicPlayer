---
type: index
summary: "Entry point of the LLM wiki: every page, one line each."
updated: 2026-11-13
---

# Rolla Music wiki — index

Generated and maintained by the LLM (the Karpathy LLM Wiki plugin and the `/wiki-*` commands); never hand-edited.
Schema: `CLAUDE.md` → "Knowledge wiki". Code and `CLAUDE.md` win over anything here. History: [[log]].

## Sources
- [[claude-md]] — The project memory file every agent loads: overview, architecture, rules, status, the vault layout and the wiki schema.
- [[custom-tags-phase1-plan]] — Custom tags phase 1: the ROLLA_TAGS field IO, Room v6, the scan's file pass and the tag repository.
- [[custom-tags-phase2-plan]] — Custom tags phase 2: the Room v7 read guard, the picker sheet, tags in both tag editors.
- [[custom-tags-phase3-plan]] — Custom tags phase 3: Manage tags, rename, delete with file clean-up, export/import.
- [[custom-tags-phase4-plan]] — Custom tags phase 4: chips in track lists and on Now Playing, Find tracks.
- [[custom-tags-roadmap]] — The four-phase roadmap for custom tags.
- [[custom-tags-spec]] — The approved design for user-defined tags and subtags stored in each file's ROLLA_TAGS field.
- [[ownership-map]] — CODEOWNERS-style map: one primary owner agent per path, required reviewers for cross-cutting files.

## Entities
- [[module-app]] — App shell: Application, MainActivity, the NavHost, the DI root and the Home screen.
- [[module-baselineprofile]] — Macrobenchmark and Baseline Profile generator (a test module).
- [[module-build-logic]] — Convention plugins (an included build) plus the version catalog and root Gradle files.
- [[module-core-common]] — Shared non-model utilities; today only the Unicode-aware library order.
- [[module-core-data]] — Repositories, the MediaStore scanner and the per-track artwork cache.
- [[module-core-database]] — Room: entities, DAOs, migrations and MusicDatabase (schema v7).
- [[module-core-datastore]] — DataStore: app settings, equaliser active-state and recent searches.
- [[module-core-designsystem]] — One UI theme tokens (colour, type, shape, dimensions) and the model-agnostic component kit.
- [[module-core-media]] — Media3 playback: PlaybackService, MediaSession, the queue, queue restore and the equaliser effect.
- [[module-core-model]] — Pure-Kotlin domain models and enums: the shared contract every module imports.
- [[module-core-permissions]] — The runtime media permission gate and flow (storage only).
- [[module-core-tagio]] — jaudiotagger IO shared below the feature layer: tag helpers and the ROLLA_TAGS field.
- [[module-core-testing]] — Shared test fakes, fixtures and rules.
- [[module-core-ui]] — Model-aware shared composables: song rows, album cards, list rows, the song options sheet.
- [[module-feature-equalizer]] — The 8-band equaliser screen: sliders, presets and the response curve.
- [[module-feature-library]] — Home tabs Tracks, Albums, Artists and Folders, and the Album, Artist and Folder detail screens.
- [[module-feature-player]] — Now Playing, the floating mini-player and the queue sheet.
- [[module-feature-playlists]] — Favourites and Playlists tabs, playlist detail, smart playlists and reordering.
- [[module-feature-search]] — Local search across songs, albums and artists (substring match over Room).
- [[module-feature-settings]] — App settings, manual rescan, Privacy, About and Licenses.
- [[module-feature-tageditor]] — Tag reading and writing (MP3, FLAC, M4A), scoped-storage consent, single and batch editors.
- [[module-feature-widget]] — The Glance home-screen widget: artwork, progress, 15-second skip and playback controls.

## Concepts
- [[agent-module-ownership]] — Every path has one primary owner agent; cross-cutting files name required reviewers.
- [[custom-tags]] — User-defined tags and subtags: IDs in each file's ROLLA_TAGS field, indexed in Room.
- [[core-model-governance]] — `:core:model` has no owner; changes follow model-vocabulary.md and need code-reviewer plus consumer review.
- [[development-history]] — v1.0 built in Phases 0–8 (Jun–Jul 2026); One UI redesign Phases 1–7 (Oct–Nov 2026); per-track artwork; custom tags in four phases (Nov 2026).
- [[knowledge-vault]] — The authored Obsidian vault in docs/, in a numbered layout (00 Home … 99 Meta); the repo root is the vault.
- [[llm-wiki]] — This wiki: an LLM-maintained layer of linked pages over the project's raw sources.
- [[multi-module-architecture]] — Hybrid feature + core modules; features never depend on each other.
- [[offline-privacy-architecture]] — No network layer, local data only, no analytics, storage-only permissions.
- [[one-ui-design-system]] — OLED-black dark theme, one blue accent, rounded surfaces; tokens and kit in :core:designsystem.
- [[per-track-artwork]] — Every track shows its own embedded picture; albums, artists and folders show the placeholder.
- [[playback-queue-restore]] — Queue, position, shuffle and repeat are saved in Room and restored paused on the next start.
- [[song-not-track]] — The audio item is `Song` in code; UI copy says Tracks.
- [[tech-stack]] — Kotlin 2.2, Compose + M3, Media3 1.11.1, Room, Hilt 2.57.2, AGP 8.13.2, compileSdk 36.

## Answers

_None filed yet._
