---
type: ledger
status: history
date: 2026-11-12
plan: "docs/superpowers/plans/2026-11-12-custom-tags-phase4-finding.md"
rulings: 9
deferred_minors: 9
tags: [ledger, rulings, custom-tags]
related: ["[[Custom tags]]", "[[Timeline]]", "[[Ledgers]]", "[[2026-11-12-custom-tags-phase4-finding]]"]
---

# Ledger rulings — Custom tags phase 4 (finding and showing)

Every `Ruling` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-11-12-custom-tags-phase4-finding/progress.md` (git-ignored scratch, archived here verbatim on
2026-11-12 so it survives the workspace's deletion). Plan: [[2026-11-12-custom-tags-phase4-finding]]; spec:
[[2026-11-09-custom-tags-design]]. Pre-flight scan clean. Final review: Ready, 4 Minor, no fix wave (Ruling 8).
Device: Room 125/125 on the Motorola; scroll A/B within 5 % of `7f26949` (Ruling 9); the user's walk passed every step
(2026-11-12). Open minors went to [[Backlog]].

## Rulings

- Ruling 1: fix round 1 = I1 + M1 + M2 + M3/M4/M5 wording; M6 deferred. — M1 cannot be judged by Task 7: the benchmark's
  seeded library has no ROLLA_TAGS, so it measures only untagged rows; tagged rows (the user's real library) must be
  cheap by construction. M2: drop the lone chip when the room is below 2 × tagChipHorizontalPadding + an ellipsis.
  — Cost if wrong: one layout rework in a kit file.
- Ruling 2: I2 fix = reviewer option (b): each chip slot carries a semantics modifier node that clears its subtree
  unless the chip is placed in the current pass (set from the measure pass, invalidateSemantics on change), so placed
  chips keep their text and hidden ones vanish from merged and unmerged trees; test it (hidden label absent from the
  merged row tree; shown labels present). Task 3's SongCustomTagChips still sets its own description per the plan. —
  (a) would break every text-based chip test and (c) leaves a TalkBack leak. — Cost if wrong: one modifier node.
- Ruling 3: accept that hidden chips stay in the test API's unmerged tree (Compose 1.7.3 getChildren includes replaced
  semantics on purpose; accessibility uses getReplacedChildren) — the user-facing trees (merged row, TalkBack) are
  correct and the unmerged check asserts unplaced-under-clearing instead. — Cost if wrong: a later test author meets the
  caveat (documented in the KDoc and spec §6).
- Ruling 4: the :core:data detekt failure is a Task 1 defect; the Task 1 implementer fixes it in a Task 1 fix round
  (repository convention), re-checked by :core:data:detekt. TalkBack reading every tag (concern 3) is accepted: the
  description is the full list, which is more useful than the visible subset. — Cost if wrong: a suppression line.
- Ruling 5: the queue sheet (its own QueueRow) shows no chips — spec §5.5 "track lists" means the library lists; the
  queue is a compact playback list. Search results show chips (call 6 keeps search behaviour unchanged, not its rows).
  TalkBack order → the walk. — Cost if wrong: chips added to QueueRow later.
- Ruling 6: I1 fix = the VM exposes `listsUnavailable: Boolean` and `activeCustomTagFilters:
  List<ActiveCustomTagFilter(kind, id, name: String?)>` covering every filter ID; Task 6 shows a chip when name != null,
  or labelled "Unavailable tag" / "Unavailable subtag" when listsUnavailable, ✕ → onRemoveCustomTag. Test
  listsFail_filterTagsStayRemovable. Task 6's dispatch carries the fields. — A filter the user can't see or remove is a
  silently wrong result. — Cost if wrong: two fields and one copy pair.
- Ruling 7: fix round 1 = I1 (always emit the strip item, empty when no chips; test typing the first filter keeps
  "Remove filter Title: …" displayed) + M4 (ImeAction.Next through the five fields, Done on Year). M1 → the user's walk
  decides; VM playlist members accepted (SongOptionsHost needs them; vault records them); controller adds Find tracks
  to the nav and style-guide screen lists. M2, M3, M5, M6 deferred. — Cost if wrong: one list item.
- Ruling 8: no final fix wave — every final finding is Minor and none breaches a constraint; they go to the Backlog;
  the walk checks M1 and RF3 end to end. — Cost if wrong: a polish round later.
- Ruling 9: benchmark passes — the plan's per-run bar (≤ 67.12) was missed by the first two HEAD runs, but the
  interleaved A/B shows the base itself ranges 63.9–66.1 and HEAD's median P90 is within 5% of the base median; the
  benchmark's library has no tags, so the measured path is the untagged row (one map lookup by construction, final
  review). No system trace taken. — Cost if wrong: a real ≤5% untagged-row regression ships; the Backlog's scroll item
  covers a trace.

## Deferred minors

- Task 1: minor (deferred): M1 links→CustomTagIds mapping duplicated (SongFinder.kt:96-102 vs CustomTagRepositoryImpl.kt:89-97);
  M2 EntryTieBreak repeats SongTieBreak; M3 SongFilter.isEmpty untested (carried into Task 5's dispatch); M4 no test
  for re-emit on song added/removed; M5 final check's tests FROM-CACHE (unreported).
- Task 2: minor (deferred): M6 RemovableChip footprint/Outline pixels and RTL untested.
- Task 2: minor (deferred): N1, N2.
- Task 3: minor (deferred): M1 move the lookup read into SongInfo; M2 merged row "Tags: …" description untested; M3
  fallback catch swallows without a log; M4 "has any tag" check in the caller (Task 4 repeats it).
- Task 4: minor (deferred): M1 stale comment NowPlayingScreenTest.kt:283-284 (640 dp slack); M2, M3 see review-task4-result.md.
- Task 5: minor (deferred): M1 two chained WhileSubscribed(5s) (~10 s linger); M2 lists failure terminal (no retry); M3 onRemoveCustomTag untested (covered by the fix round's test); M4 VM before tests / stale RED stack lines; M5 build-logic stabilityConfigurationFile deprecation warnings.
- Task 5: minor (deferred): fix-round M1 @VisibleForTesting on the keys.
- Task 6: minor (deferred): M2, M3, M5, M6 (review-task6-result.md); M1 pending the walk.
- Task 6: minor (deferred): N1 (walk), N2.
