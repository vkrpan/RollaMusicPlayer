---
type: ledger
status: history
date: 2026-11-11
plan: "docs/superpowers/plans/2026-11-11-custom-tags-phase3-managing.md"
rulings: 4
deferred_minors: 7
tags: [ledger, rulings, custom-tags]
related: ["[[Custom tags]]", "[[Timeline]]", "[[Ledgers]]", "[[2026-11-11-custom-tags-phase3-managing]]"]
---

# Ledger rulings — Custom tags phase 3 (managing)

Every `Ruling` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-11-11-custom-tags-phase3-managing/progress.md` (git-ignored scratch, archived here verbatim on
2026-11-12 so it survives the workspace's deletion). Plan: [[2026-11-11-custom-tags-phase3-managing]]; spec:
[[2026-11-09-custom-tags-design]]. Pre-flight scan clean. Final review: ready with fixes (an import dropping a ticked
ID was stripped from the open editor's file; `trackCount` in code); one fix wave, re-reviewed clean. Device: Room
124/124 on the Motorola; the user's walk passed every step (2026-11-12). Open minors went to [[Backlog]].

## Rulings

- Ruling 1: fix round 1 = I1 (CUSTOM_TAG_NAME_MAX_LENGTH moves to :core:model next to MAX_CUSTOM_TAG_ID; vocabulary row
  by the controller) + M1 (move CustomTagListFile to :core:model; the codec stays in :core:tagio) + M2 (applyImport
  validates names and uniqueness with the repository's own rules) + M4 (boundary tests) + a :feature:tageditor detekt
  run. M3, M5 deferred. — Task 2 builds on this API, so settle the module boundary now. — Cost if wrong: a few moved
  imports.
- Ruling 2: I1 — literal "Updated X of N tracks." (constraint verbatim; the batch editor's "Edited X of N tracks."
  precedent the spec cites), pinned by an N = 1 test. Fold M2 (one bulk song lookup), M4 (tests), M5 (comment), and move
  `minusId` beside `plusId` in CustomTagEditing.kt. M1 (extract the shared write loop) → backlog; M3 deferred (siblings
  share it). — Cost if wrong: "of 1 tracks" for a single track.
- Ruling 3: fix I1 (no pruning on the fallback emission; test: lists throw after load, title-only save leaves the field
  byte-identical; correct the comment). Keep the brief's prune rule `selected ∩ known` (fails visibly; the prev/now
  rule hides a deleted ID under conflation). M2 → backlog; M3 → Task 6 walk; M4 controller. — The reviewer showed the
  race unreachable while the picker is open. — Cost if wrong: a just-created tick vanishes and the user re-ticks it.
- Ruling 4: single editor writes the user's own changes onto the field read from a FRESH copy of the original at save:
  `(current − userUnticked) ∪ userTicked`, pruned IDs count as untouched; write into the copy whenever the computed set
  differs from the copy's field (the persist copies the load-time copy back). Also closes Task 5 M2. I2 rename to
  songCount. M1, M2 → backlog. — Re-reading the original is the only source that knows both a Manage delete and an
  import. — Cost if wrong: one extra file copy per save.

## Deferred minors

- Task 1: minor (deferred): M3 non-transactional preview/export reads; M5 multimap DAO return types.
- Task 1: minor (deferred): applyImport validation test covers blank + duplicate name only (ID range, duplicate ID,
  41 code points, SUBTAG list untested; codec and DB constraints back it).
- Task 2: minor (deferred): M1 shared per-file write loop extraction (backlog); M3 uncaught repository failures in the
  delete flow leave isBusy stuck (siblings share the gap).
- Task 3: minor (deferred): M1 ~40 lines duplicated from the picker (pager sync + empty state) → backlog (extract into
  :core:ui); M2 row end padding borrows topBarEdge; M3 82 dp gutter (walk); M4 rows look enabled while busy (taps
  ignored); M5 CustomTagNameDialog comment says five parameters (six); M6 busy test doesn't check the bar's ⋮.
- Task 4: minor (deferred): M1 import read/preview holds the busy lock with no progress shown (a stalled provider
  looks frozen; Back swallowed); M2 ListFileMenu KDoc says "while a delete runs"; M3 apply failure says "Couldn't read
  that file."; M4 retryPersist could use `succeeds`; M5 clash lines don't name their list; M6 BOM strip and 4 MiB cap
  untested (Android class).
- Task 5: minor (deferred): M1; M2 (backlog); M3 (walk).
- Task 5: minor (deferred): M1 picker RED by mutation only; M2 (backlog); M3 route lambdas (walk); fix-round minors above.
