---
type: index
status: open
date: 2026-11-08
tags: [backlog, index]
related: ["[[Timeline]]", "[[Custom tag system is cut]]", "[[Per-track artwork]]"]
source: "CLAUDE.md (moved into the vault 2026-11-08)"
---

# Backlog

Open work, newest first. Each item links to its source.

## In flight (2026-11-08)

- [x] **Per-track artwork**: committed `e129f3e` and pushed 2026-11-09 (hook script `4dd862a`). See [[Per-track artwork]].
- [ ] **Merge `feat/oneui-redesign` into `main`** (the One UI redesign and per-track artwork): the user's call.
  - Done 2026-11-09: `:core:database:connectedDebugAndroidTest` on the Motorola, 104 tests, 0 failures (Task 5's
    reshaped Artist, Folder and artist-search SQL).
  - Outside changes (final review M3/M4, ruling P28): after another app edits a track's tags, or a startup sync finds
    a newer file, the current track's Now Playing, mini-player, notification and widget keep the old artwork value until
    the track changes (lists update). Fix: after `LibraryIndexer.sync`, call `updateSongMetadata` for changed rows in the
    queue (crosses `:core:data` → `:core:media`).
  - Performance minors from the final review (M1 no-picture binds take the IO hop, M2 uncapped decodes, M5 one extra
    decode per save, M6 undecodable pictures re-read per bind): left by ruling P28; see the run's final review.
- [ ] **Merge `feat/oneui-redesign` into `main`** and tag the release (the user's call). See [[One UI redesign]].
- [ ] **Install the staged superpowers-extras update** (`~/.claude/task-observer-workspace/skill-updates/2026-11-08/`).
- [ ] **Custom tags and subtags** (asked 2026-11-09): replaces [[Custom tag system is cut]]. Spec approved
  ([[2026-11-09-custom-tags-design]]); four phases ([[2026-11-09-custom-tags-roadmap]]). Phase 1
  ([[2026-11-09-custom-tags-phase1-foundation]]) complete and device-verified, waiting for the user's commit. Progress
  and phase-2 notes in [[Custom tags]].
  - Batch the `FileTagPass` writes (one transaction per 50 files instead of per file) if the device probe shows the
    first pass costs CPU or scroll smoothness (final review I3, Ruling 11).
  - Before phase 2: decide M4A writes; fix the apply-after-reset race ([[Custom tags]] "Known limits").
  - R8 keep rule for `RollaTagsFileIo`'s static jaudiotagger logger field (`:core:tagio` consumer rules), so release
    keeps jaudiotagger at WARNING (final fix re-review).
  - Phase 3: make `insertNew` report "full" in its transaction (last-ID race, [[Custom tags]] "Known limits").
- [ ] **Bug (existing, found 2026-11-09): saving tags on an M4A erases other apps' custom fields.** jaudiotagger's
  commit drops every freeform atom its `Mp4FieldKey` enum does not list
  ([[jaudiotagger cannot write unknown M4A freeform atoms]]). Affects the [[Tag editor]] now and blocks writing
  `ROLLA_TAGS` to M4A ([[Custom tags]]). Decide with the M4A question before custom tags phase 2.
- [ ] **LLM wiki follow-ups** (first ingest 2026-11-09, [[LLM wiki beside the vault]]), all the user's call:
  - `docs/log.md` (a stray log scaffold at the vault root, not made by Claude) clashes by name with `docs/wiki/log.md`;
    delete it or rename it.
  - Commit or git-ignore the plugin code (`docs/.obsidian/plugins/karpathywiki`, 4.8 MB) and
    `docs/.obsidian/community-plugins.json`. Its `data.json` is already git-ignored.
  - The plugin's extraction granularity is `standard`; the user's notes suggest Coarse or Minimal to save API cost.
  - Uncommitted: the wiki scaffold, the CLAUDE.md schema section, the `/wiki-*` commands, the `.gitignore` lines.
- [ ] **Drift the first wiki ingest found** (fix in the raw sources, not the wiki):
  - [[Ownership map]] lists `:app/.../di/**` modules; `:app` has no `di/`. `DatabaseModule` is in
    `:core:database/di`, `PreferencesModule` in `:core:datastore/di`; `MediaModule` and `ImageModule` do not exist.
  - `CLAUDE.md` Notes say "Use Material Design 3 components throughout"; [[Build UI from tokens and the kit]] wins.
  - `CLAUDE.md` Current Status still calls `15821b4` the redesign's last commit; the branch is at `e129f3e`.
- [ ] **Hooks block**: the compaction hooks live in `.claude/settings.local.json`; move them into the committed
  `.claude/settings.json` if the user wants them shared.

## Moved from CLAUDE.md

### Post-1.0 cuts (deliberate, not defects)

One thing described in this repo's specs was consciously scoped out. If a future prompt flags it as "missing", it is
deferred, not broken; don't build it without an explicit ask:

- **Custom tag system:** deferred during Phase 5, formally cut at v1.0 — **superseded 2026-11-09**: the user asked for
  it and it is being built as [[Custom tags]].

The other three v1.0 cuts (the A–Z fast-scroll index, the centre-weighted tab bar and Folders browsing) were built by
the One UI redesign at the user's request (Phases 2, 4b and 5) and are shipped on `feat/oneui-redesign`.

### What remains (backlog)

- The custom tag system: now in progress as [[Custom tags]] (see In flight).
- **Scroll performance:** on the `pixel6Api31Frames` emulator the Tracks list's frame-duration P90 (with the baseline
  profile) is 96.3 ms against v1.0.0's 70.9 ms (+35.9 %; startup +13.4 %). The final review found no cause in the
  Phase 7 diff; the gap spans the whole redesign (pager, panel, rail, overlay pill). Next step: benchmark 8f4ce3a
  against HEAD and take a system trace of `ScrollBenchmark` (Phase 7 ledger, `benchmarks-v1-vs-phase7.md`).
- **"Unknown" sorts under `#`** in the Artists tab (it is the raw `<unknown>` key that sorts; Phase 7 ruling P9 kept
  it). Sorting it by its display name is a small, separate change.
- [x] Review follow-ups #1–#3 in [[v1.0 release]]: all closed (#2 in redesign Phase 4a; #1 and #3 by [[Per-track artwork]]
  Task 6, 2026-11-08).
- Flaky test: `MusicWidgetContentTest.pausedStateShowsPlay` failed once (`UncompletedCoroutinesError` after 2 s) in a
  full run on 2026-11-09 and passed on re-runs (per-track artwork Task 8 ledger).
- Regenerate the release Baseline Profile (`app/src/release/generated/baselineProfiles/`) before the next release: it
  still lists the deleted album-cache classes (harmless).
- Optional consolidation: move the per-module dispatcher qualifiers into `:core:common` (it holds the library order).
- **Redesign test-hygiene minors** (Phase 7 ruling 7): pure test or doc polish from the phase ledgers, none visible to a
  user. Each entry is in the named ledger under `.superpowers/sdd/<plan>/progress.md` (git-ignored; the summaries below
  are the record once the workspace is gone):

  | Ledger | Items |
  |---|---|
  | Phase 5 (`2026-10-17-oneui-phase5-fast-scroll`) | T1 M1, M2; T2 M3; T3 M1, M2, M4; T4 M1, M6; T5 M3, M6–M9 |
  | 6a (`2026-10-18-oneui-phase6a-details-sheets`) | T1 M1–M6; T2 M2–M6; T3 M1–M5; T4 M1–M3; T5 M2, M4–M7; T6 M1–M5; T7 M1, M2 |
  | 6b (`2026-10-21-oneui-phase6b-search-editors`) | T1 M5; T2 M3–M6; T3 M3–M5; T5 M3; the WhileSubscribed re-record |
  | 6c (`2026-10-22-oneui-phase6c-widget`) | ±15 not pinned as stroked; the preview's `VectorRaster` lint; the ink-mask colour; widget layout values unpinned (M-4); the bar clearance's zero margin |
  | 7, Task 11 and final review | `AddToPlaylistSheetSmokeTest.clickingNewPlaylistRow` is flaky on the Motorola (1 failure in 4 runs, "No compose hierarchies found"); `buildFastScrollSections` throws on a list not in library order instead of degrading (ruling P36); the tag editor and the permission prompt borrow `settingsSectionGap` (now 13 dp) for their own spacing; `checkNoMaterialIcons` has no Gradle-level test of its wiring and also matches comments |
  | 7 (`2026-10-27-oneui-phase7-calibration-release`) | the `minor (deferred)` lines for Tasks 3b, 5 and 6 (EQ card height unpinned, divider tests' coverage, the transport's even-spread fallback below ≈334 dp, the shared `miniPlayerArtGlyph` name, the widget picker preview's old corners, a possible first-frame scrim, the search field's async query round-trip, the search-field overload duplication, `searchFlow` containment details, the trailing-control width edge, rail test polish) |

## Next Steps

1. Commit Phase 7 and merge `feat/oneui-redesign` into `main` (the user's call), then tag the release.
2. Verify the System UI resume card on a stock-Android device (designed for, not yet seen).
3. Triage the backlog above; the custom tag system is a new feature, so treat it as a fresh ask.
