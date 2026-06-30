---
name: bootstrap-project
description: "Phase 0 workflow for RollaMusicPlayer — scaffold the hybrid feature+core multi-module Gradle project, lock the :core:model vocabulary, then build the first vertical slice (permission → scan → list → play one song) end-to-end across the agent mesh, with a build/checkpoint after every step and a review gate before commit. Use this once, at project start, to validate that the agents and skills compose."
---

# Skill: Bootstrap Project (Phase 0)

## Overview
A one-time, ordered workflow to take RollaMusicPlayer from an empty repo to a working vertical slice. It scaffolds the multi-module Gradle project (build-tooling-agent + `setup-modularization`), wires the quality gates, locks the canonical domain models in `:core:model`, then drives one thin end-to-end feature — permission → media scan → song list → tap → play one song — across permissions-agent, data-layer-agent, media-scanning-agent, audio-engineer, ui-builder, and viewmodel-architect. The point is validation: if these agents compose cleanly on one real feature, the rest of the app is repetition of the same loop. Every step ends with a green build and a checkpoint; the slice ends with a parallel review gate.

## When to Use
- Exactly once, at the very start of development
- After the `.claude/` config (agents, skills, rules) is in place and consistent
- To prove the agent mesh before scaling to full features

## Prerequisites
- Android Studio + Android SDK (API 34), JDK 17
- Claude Code launched from the repo root
- `.claude/` fully populated; Track→Song rename applied per `model-vocabulary.md`
- `git init` done; an emulator/device available (AOSP/Google-APIs image)
- Read first: `CLAUDE.md`, `OWNERSHIP.md`, `.claude/rules/*`

## Operating Rules
- **One step at a time.** Do not batch — each step must reach its checkpoint (green build) before the next.
- **Commit at each marker** so there is always a green fallback.
- **Stop and correct** if any step proposes networking or the `Track` model name.

## Workflow Steps

### Step 0: Orient (no code)
**Goal**: Confirm shared understanding before writing anything
**Actions**: Have Claude read `CLAUDE.md`, `OWNERSHIP.md`, and `.claude/rules/`, then restate the Phase 0 plan and the agents/skills it will use per step. Verify it commits to offline-first and `Song` (never `Track`).
**Checkpoint**: Plan restated correctly; no network, no `Track`.

### Step 1: Scaffold the skeleton
**Goal**: An empty but buildable multi-module shell with quality gates
**Actions** (build-tooling-agent):
1. `setup-modularization` → `settings.gradle.kts` (all modules), root `build.gradle.kts`, `gradle/libs.versions.toml`, `build-logic/` convention plugins, thin per-module build files. minSdk 24, targetSdk 34. Build green.
2. `setup-static-analysis` → detekt + Spotless(ktlint), the `checkNoNetwork` gate, pre-commit hook, CI workflow. `./gradlew check` runs all gates.
3. App shell: `RollaApp` (@HiltAndroidApp), `MainActivity` (`implement-edge-to-edge-and-insets`), empty NavHost (navigation-agent), manifest with NO internet permission. Launches to a blank themed screen.
**Checkpoint**: `./gradlew assembleDebug` and `./gradlew check` green; app launches edge-to-edge.
**Commit**: `chore: multi-module scaffold + tooling + app shell`

### Step 2: Lock the domain model
**Goal**: The shared vocabulary every module imports
**Actions** (`:core:model`, governed by `model-vocabulary.md`): create `Song` (canonical), `Album`, `Artist`, `Playlist`, `EqualizerPreset`, and enums `RepeatMode`/`ShuffleMode` as pure-Kotlin types with exactly the specified fields. No Android deps. Unit-test any model logic.
**Checkpoint**: `:core:model` compiles as a JVM library; no `Track` type anywhere.
**Commit**: `feat(core-model): canonical domain models`

### Step 3: Vertical slice (permission → scan → list → play)
**Goal**: Prove the agent mesh end-to-end, offline
**Actions** (build + verify between each sub-step):
- **3a Permissions** (permissions-agent + `handle-runtime-permissions`): `:core:permissions` — version-aware media permission, Compose gate with rationale + Settings routing, manifest (no INTERNET/WRITE), `onGranted` callback.
- **3b Database** (data-layer-agent + `add-room-database`): `:core:database` — `SongEntity` (incl. media_store_id, date_modified, content_uri, artwork_uri), `SongDao` (observe Flow, upsert, deleteByMediaStoreIds, getAll), `MusicDatabase` v1, Hilt module, DAO test.
- **3c Scanner** (media-scanning-agent + `implement-media-scanning`): `:core:data` — `MediaScanner` (IS_MUSIC, content URIs, TRACK normalization), mapper, `LibraryIndexer.sync()` incremental diff.
- **3d Repository** (data-layer-agent + `implement-repository-pattern`): `:core:data` — `SongRepository.observeSongs(): Flow<List<Song>>` mapping entities to `:core:model`.
- **3e Playback** (audio-engineer + `media3-playback`): `:core:media` — ExoPlayer service, MediaSession, audio focus, foreground notification, `PlaybackController`; expose `audioSessionId`; play content URIs.
- **3f Library UI** (ui-builder + viewmodel-architect + `implement-state-management` + `implement-image-loading-coil`): `:feature:library` — `LibraryScreen` (LazyColumn, stable keys, Coil artwork), `LibraryViewModel` (scan on grant, `songs: StateFlow`), tap → `PlaybackController.play(song)`, wrapped in the permission gate.
- **3g Wire app** (navigation-agent): register the library nav entry as start destination in `:app`, inject playback, run **in airplane mode**.
- **3h Review gate**: run code-reviewer + compose-performance-auditor + test-writer in parallel; fix all CRITICAL/HIGH (network, leaks, missing keys, main-thread IO); add ViewModel + repository tests; `./gradlew check` green.
**Checkpoint**: grant permission → library populates from local files → tap a song → it plays with notification controls, **all in airplane mode**.
**Commit**: `feat: vertical slice — scan, list, play one song (offline, end-to-end)`

## Definition of Done
- Multi-module project builds (`assembleDebug` + `check` green)
- `:core:model` is the single source of model truth (Song, not Track)
- The permission→scan→list→play path works on-device in airplane mode
- No CRITICAL/HIGH review findings; ViewModel + repository tested
- Each step committed

## Related Files
- `PHASE0-BOOTSTRAP.md` — the exact copy-paste prompt sequence this skill formalizes
- `CLAUDE.md` — module graph + ownership quick map
- `OWNERSHIP.md` — who owns which path / cross-cutting files
- `.claude/rules/model-vocabulary.md` — canonical model names

## Notes
- This is a run-once skill. After it, proceed feature-by-feature (player, playlists, equalizer, tageditor, search, widget), each on its own branch, reusing the loop: skill → owning agent → build → review gate → commit.
- The airplane-mode end-to-end check in 3g is the real success signal — it proves the offline guarantee, not just compilation.
- If the slice exposes friction at an agent boundary, fix `OWNERSHIP.md` / the agent before scaling.

## Common Pitfalls
- ❌ Skipping checkpoints / batching steps — a broken build compounds across modules.
- ❌ Writing feature code before `:core:model` is locked — reintroduces model-name drift.
- ❌ Verifying on a normal network instead of airplane mode — hides accidental network use.
- ❌ Committing the slice before the review gate — lets CRITICAL findings into history.
