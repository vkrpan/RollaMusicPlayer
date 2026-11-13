---
type: hub
status: active
date: 2026-11-13
tags: [project, known-issues, tech-debt]
related: ["[[00 Home]]", "[[Backlog]]", "[[Roadmap]]", "[[Troubleshooting]]"]
---

# Known Issues

Open bugs and tech debt, each with the module it affects. Detail and history stay in [[Backlog]] (the working list);
this note is the index by kind. Close an item here and in the Backlog together.

## Bugs (user-visible)
| Issue | Module | Detail |
|---|---|---|
| Saving tags on an M4A erases other apps' custom freeform fields | [[feature-tageditor]], [[core-tagio]] | [[jaudiotagger cannot write unknown M4A freeform atoms]] |
| After another app edits the current track's tags, Now Playing, the mini-player, notification and widget keep the old artwork until the track changes | [[core-data]], [[core-media]] | Backlog "In flight", ruling P28 |
| "Unknown" artist sorts under `#` in the Artists tab (raw `<unknown>` key) | [[feature-library]] | [[Artist]] |
| An MP3/FLAC whose `ROLLA_TAGS` can't be read shows "Tags can be saved to MP3 and FLAC files only" (wrong reason) | [[feature-tageditor]] | [[ROLLA_TAGS field]] |
| A tick pruned by one import and made known by a later one is shown unticked | [[feature-tageditor]] | [[Custom tags]] |
| After a tag-list failure the editors still offer the picker and Create | [[feature-tageditor]] | [[Custom tags]] |
| Create's non-duplicate database errors are uncaught in both editors | [[feature-tageditor]] | [[Custom tags]] |
| A tag chip's fill matches the selected-row highlight, so the pill edge vanishes | [[core-ui]] | [[Custom tags]] |
| Manage tags delete asks consent for, and copies, M4A/WAV/OGG files it can never write | [[feature-tageditor]] | [[Custom tags]] |
| The System UI resume card is unverified on stock Android and absent on One UI | [[core-media]] | [[One UI has no media resume card]] |

## Performance
| Issue | Module | Detail |
|---|---|---|
| Tracks list scroll frame P90 +35.9 % and startup +13.4 % against v1.0.0 (emulator) | [[feature-library]], [[app]] | [[Roadmap]] |
| Per-track artwork minors: no-picture binds take the IO hop, uncapped decodes, an extra decode per save, undecodable pictures re-read | [[core-data]] | [[Per-track artwork]] |
| `FileTagPass` commits per file; batch per 50 if the device shows a cost | [[core-data]] | [[Custom tags]] |

## Tech debt
| Item | Module | Detail |
|---|---|---|
| The per-file write loop (consent, editable copy, API 29 recovery, finalizer) exists three times | [[feature-tageditor]] | [[Custom tags]] |
| Picker pager sync and empty state duplicated (~40 lines) | [[feature-tageditor]] → [[core-ui]] | [[Custom tags]] |
| Absent and undecodable `ROLLA_TAGS` look the same in `RollaTagsFileIo.read` | [[core-tagio]] | [[ROLLA_TAGS field]] |
| No R8 keep rule for `RollaTagsFileIo`'s jaudiotagger logger field | [[core-tagio]] | [[Custom tags]] |
| The single editor's save-time fresh copy can stay in the cache after a cancellation or IO failure | [[feature-tageditor]] | [[Custom tags]] |
| The release Baseline Profile still lists deleted album-cache classes | [[app]] | [[baselineprofile]] |
| Dispatcher qualifiers are per module, not in `:core:common` | [[core-common]] | — |
| Redesign test-hygiene minors (Phases 5–7 ledgers) | many | [[Backlog]] |

## Flaky tests
| Test | Module |
|---|---|
| `MusicWidgetContentTest.pausedStateShowsPlay` (`UncompletedCoroutinesError`, once) | [[feature-widget]] |
| `AddToPlaylistSheetSmokeTest.clickingNewPlaylistRow` on the Motorola (1 in 4) | [[core-ui]] |

## Docs drift
| Item | Detail |
|---|---|
| `README.md` still describes v1.0 (compileSdk 34, album artwork cache, the cut tab bar) | [[Keep docs honest]] |
| [[Ownership map]] lists `:app/.../di/**` modules that do not exist | [[Backlog]] |
| `CLAUDE.md` calls `15821b4` the redesign's last commit and says "Material Design 3 components throughout" | [[Backlog]] |
| `CLAUDE.md`'s wiki schema scopes page-name uniqueness to "the `docs/` vault"; the vault is now the repo root | found by the 2026-11-13 wiki re-ingest ([[LLM wiki beside the vault]]) |
