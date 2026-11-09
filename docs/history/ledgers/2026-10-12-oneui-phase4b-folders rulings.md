---
type: ledger
status: history
date: 2026-10-12
plan: "docs/superpowers/plans/2026-10-12-oneui-phase4b-folders.md"
rulings: 2
deferred_minors: 1
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — One UI Phase 4b — Folders

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-12-oneui-phase4b-folders/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-12-oneui-phase4b-folders.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Ruling P1: FolderDetailViewModel duplicates FavouritesTabViewModel's play + playlist actions (third copy after TracksViewModel) — plan ruling 8 mandates it; features cannot share code except through :core:*, and extracting a shared helper would touch two other features' ViewModels and tests mid-phase — cost if wrong: one small Phase 6 extraction (carried over), and reviewers may flag the duplication again.
- Task 1: minor (deferred): M-2 no index on folder_path. Ruling: no index in v4 — a few-thousand-row scan per emission is sub-millisecond and spec §9.4 specifies only the ALTER; revisit with Phase 7's NOCASE sort-perf item (L11) — cost if wrong: a v5 migration to add the index later.
- Task 2: minor (deferred): unused Song.toEntity(mediaStoreId, dateModified) in SongEntity.kt would write NULL folder_path; no callers; self-heals on next sync — delete in a later data-layer cleanup.
