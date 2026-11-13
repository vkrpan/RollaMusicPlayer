---
type: source
summary: "The project memory file every agent loads: overview, architecture, rules, status, the vault layout and the wiki schema."
aliases: ["CLAUDE.md"]
tags: [source]
sources: []
updated: 2026-11-13
---
# Source: CLAUDE.md

The project memory file every agent loads: overview, architecture, rules, status, the vault layout and the wiki schema.

**What it covers:** the instruction file every Claude Code session in this repo loads. It sets the start-up protocol
(task-observer, skill selection), describes the authored [[knowledge-vault]] and where each kind of note goes, defines
this wiki's schema ([[llm-wiki]]), and describes the product, the architecture, the stack, the features, the module
tree, the agent-to-module quick map, key design decisions and the current status.

**Key decisions it records:**
- Fully offline and privacy-first, with no network layer ([[offline-privacy-architecture]]).
- Multi-module, feature + core, with a strict dependency direction ([[multi-module-architecture]]).
- Media3 over MediaPlayer, Compose, MVVM, Room, Hilt, a foreground service ([[tech-stack]]).
- The One UI–inspired dark design system built from tokens and a kit ([[one-ui-design-system]]).
- `Song` in code, "Tracks" on screen ([[song-not-track]]).
- The persistent playback queue ([[playback-queue-restore]]), per-track artwork ([[per-track-artwork]]) and custom
  tags stored in each file's `ROLLA_TAGS` field ([[custom-tags]]).
- Since 2026-11-13 the authored vault uses a numbered layout (`00 Home.md` … `99 Meta/`) and Obsidian opens the whole
  repository as the vault; `design/`, `superpowers/` and `wiki/` keep their paths ([[knowledge-vault]]).
- Every change reaches every vault note it touches, routed by kind: rules, decisions (ADRs), knowledge plus a
  Troubleshooting line, history plus Timeline and Changelog, Backlog plus Roadmap and Known Issues
  ([[knowledge-vault]]).

## Mentions
[[module-app]], [[module-core-model]], [[module-core-common]], [[module-core-database]], [[module-core-datastore]],
[[module-core-data]], [[module-core-media]], [[module-core-designsystem]], [[module-core-ui]],
[[module-core-permissions]], [[module-core-tagio]], [[module-core-testing]], [[module-feature-library]],
[[module-feature-search]], [[module-feature-player]], [[module-feature-equalizer]], [[module-feature-playlists]],
[[module-feature-tageditor]], [[module-feature-settings]], [[module-feature-widget]], [[module-baselineprofile]],
[[module-build-logic]], [[offline-privacy-architecture]], [[multi-module-architecture]], [[agent-module-ownership]],
[[song-not-track]], [[per-track-artwork]], [[playback-queue-restore]], [[custom-tags]], [[one-ui-design-system]],
[[tech-stack]], [[development-history]], [[knowledge-vault]], [[llm-wiki]], [[ownership-map]]

## Related
[[knowledge-vault]], [[llm-wiki]], [[offline-privacy-architecture]], [[multi-module-architecture]], [[tech-stack]],
[[one-ui-design-system]], [[song-not-track]], [[playback-queue-restore]], [[per-track-artwork]], [[custom-tags]],
[[agent-module-ownership]], [[ownership-map]], [[development-history]]

## Disagreements & open questions
- The Notes line "Use Material Design 3 components throughout" conflicts with the kit-only rule
  ([[one-ui-design-system]]).
- Current Status names `15821b4` as the redesign's last commit while the branch has moved on (`1cb705b`, custom
  tags, 2026-11-12) ([[development-history]]).
- The wiki schema says page names must be unique "across the whole `docs/` vault", but since 2026-11-13 the vault is
  the repository root, so `CLAUDE.md` and `README.md` (and any other repo Markdown outside the excluded folders) share
  the namespace. No clash exists today ([[llm-wiki]]).
- Expected drift checked and **not** found: `:core:common` described as "Dispatchers, Result types". The current text
  and the code agree that it holds the library order ([[module-core-common]]).
