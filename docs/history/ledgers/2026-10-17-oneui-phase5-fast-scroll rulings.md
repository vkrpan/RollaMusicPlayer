---
type: ledger
status: history
date: 2026-10-17
plan: "docs/superpowers/plans/2026-10-17-oneui-phase5-fast-scroll.md"
rulings: 20
deferred_minors: 20
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — One UI Phase 5 — A–Z fast scroll

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-17-oneui-phase5-fast-scroll/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-17-oneui-phase5-fast-scroll.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Ruling P1: Task 3's named mutation "ceil → floor" survives the slots=26 assertion but fails at slots=13 inside the same test — the test still fails, so no rewrite; the implementer reports which assertion goes RED — cost if wrong: none (the test is not blind).
- Ruling P2: `flowOn(sortDispatcher)` has no test (no observable output); the plan states it as untested by design, covered by review + Task 7 gfxinfo — cost if wrong: a main-thread sort ships unnoticed until the device jank check.
- Ruling: fix I1/I2 as test-only changes (control 'O' for "Ölaf"; mutations named in device-test comments, one applied on device in Task 7) — the constraints' per-test control rule binds over the plan's text — cost if wrong: two extra lines of test.
- Task 1: minor (deferred): M1 collation uses untrimmed name ("  Zed" before "Zap" inside Z).
- Task 1: minor (deferred): M2 leading Cf (BOM) / fullwidth first char lands in '#' (NFKD + skip Cf would fix).
- Task 1: minor (deferred): M4 build noise "Type-safe project accessors is an incubating feature" (project-wide, pre-existing).
- Task 2: minor (deferred): M1 Kotlin 2.2 annotation-target warning on 5 new qualifier params (project-wide pattern; build-logic -Xannotation-default-target or @param: everywhere — build-tooling follow-up).
- Task 2: minor (deferred): M2 plan named :app:kspDebugKotlin as the Hilt check; the graph validates in :app:hiltJavaCompileDebug (closed by controller run).
- Task 2: minor (deferred): M3 Album and Playlist id tie-breaks untested.
- Ruling: authorize one control assertion (27 sections for ["Abba"]) in noKeysMeansNoSections — the per-test control rule binds over the plan's test text — cost if wrong: one line.
- Task 3: minor (deferred): M1 step-test comment predicts RED at 26 slots; real RED is at 13 (plan-authored comment).
- Task 3: minor (deferred): M2 fastScrollLetterSlot KDoc says "drops every k-th label"; spec: shows every k-th (brief-verbatim wording).
- Task 3: minor (deferred): M3 mutation logs lack the cmp revert line (blob hashes prove the revert).
- Task 3: minor (deferred): M4 mutation #7 RED is a bare assertTrue AssertionError (no message).
- Ruling P3: absence-only tests in Tasks 4/5 get an in-test control by state flip (ruling-P3-controls.md), carried into both dispatches — the same gap was found by the Task 1 and Task 3 reviews (observation 0016), and pre-empting it saves two fix rounds — cost if wrong: a few extra test lines.
- Ruling: authorize Text wrapContentHeight(unbounded = true) + 200 dp preview + pixel render test at fontScale 2 — plan ruling 10 handled label overlap but not clipping within a slot; spec §10 / Review Focus 4 need readable letters on short rails — cost if wrong: first/last label may poke past the pill ends (Phase 7 calibration decides any inset).
- Ruling: raise review Minor 2 (no test for container isTraversalGroup) and Minor 3 (no test for per-gesture reset) to Important — they guard spec §10 traversal order and the no-dead-controls constraint — cost if wrong: two small tests.
- Task 4: minor (deferred): M1 traversalIndex RED via config[] IllegalStateException (right reason; getOrNull would quote values).
- Task 4: minor (deferred): M5/C3 mutation backups taken before root spotlessApply (LF); reverts byte-exact against those bytes.
- Task 4: minor (deferred): M6 drag-start reset (FastScrollRail.kt:99) untested — deleting it stays GREEN.
- Ruling: the Playlists rail starts below the card row — FastScrollContainer gains railTopPadding (default 0 dp); PlaylistsList passes the measured card-item height (fixed inset) — spec §10 "user rows only" and the reference's condensed Playlists rail both mean a rail beside the rows; options (a) accept overlap and (b) hide while cards visible rejected (a dead strip / a rail that pops in) — cost if wrong: a gap above the rail once the cards scroll away; Phase 7 calibrates.
- Ruling: raise review Minor 1 (Review Focus 5 unpinned outside selection mode) and Minor 2 (rescan arrival unchecked) to Important — Review Focus lines must be pinned by a test that can fail — cost if wrong: two small tests.
- Task 5: minor (deferred): M3 M-2 RED surfaces as a node lookup failure (viewport-dependent).
- Task 5: minor (deferred): M4 buildFastScrollSections runs on main during composition per emission (auditor to judge in Task 6).
- Task 5: minor (deferred): M5 HotSpot "Sharing is only supported for boot loader classes" warning in test logs (environment).
- Ruling: Playlists landscape regression (fixed 186 dp inset > ~180 dp viewport → 0 dp rail, breaks Review Focus 4) — FastScrollContainer caps railTopPadding to keep RollaDimens.fastScrollMinRailHeight = 96 dp (new token, six slots) — a guard in the container serves every caller; (c) amending Review Focus 4 rejected (would accept a missing control) — cost if wrong: in landscape the rail's top letters overlap the card row's end again.
- Ruling: raise re-review Minors (inset not saveable → rail jumps 186 dp mid-scroll after restore; one-sided inset test) to Important for this round — a visible jump and a test that cannot see an over-inset — cost if wrong: two small changes.
- Task 5: minor (deferred): M6 "no user playlists means no rail" relies on Robolectric keeping a 0-dp node (fragile).
- Task 5: minor (deferred): M7 railStartsBelowTheTopPadding's 143 dp equals the fixture header height.
- Task 5: minor (deferred): M8 coerceAtLeast(0.dp) floor in FastScrollContainer untested (landscape-with-pill branch; mutation coerceAtLeast(20.dp)).
- Task 5: minor (deferred): M9 fastScrollMinRailHeight KDoc "six letter slots" true only at fontScale 1.
- Ruling: F1 goes into the single final fix wave — buildFastScrollSections binary-searches each label's first index over the section-sorted list (27 × log N indexLetter calls) + an exactly-equivalent ASCII fast path in indexLetter — the library order (§9.0) already guarantees sorted sections — cost if wrong: an unsorted caller gets wrong sections (precondition documented and tested).
- Ruling: F2–F4 deferred to Phase 7 (baseline-profile regeneration is a Phase 7 item per spec §15; the layout{} refactor is optional).
- Ruling: one final fix wave (final-fix-brief.md) = perf F1 (binary-search sections + ASCII fast path in indexLetter, shared public librarySectionRank in :core:common) + I1 + I2 + code Minors M1 (Dimens KDoc), M5 (FastScrollContainer previews), M6 (labels test mutation comment) — the reviewer's fix-before-merge list plus the auditor's only blocker — cost if wrong: a binary-search bug would mis-target letters (existing section tests + a 5 000-key test guard it).
- Ruling: doc Minors (spec §10 "never covers the cards" contradiction, CLAUDE.md "while the list scrolls" + stale A–Z-as-cut lines, roadmap row) fixed by the controller in Task 6 docs; review Minor 9 (device parity fixture with whitespace/punctuation) deferred — expectations need a device run; Minor 7 superseded by F1; Minor 8 accepted (one-shot callers sort off main).
- Task 6: parked — FastScrollIndexTest.kt:21-22 mutation (b) comment still names the removed put/putIfAbsent — Ruling: comment-only, no second fix wave per process; fix when the file is next touched (Phase 6/7), the lower bound is pinned by aLargeListFindsEachSectionsFirstRow — cost if wrong: a reader cannot apply that named mutation.
- Task 6: parked — FastScrollRailTest.kt:156 I2 comment calls "the swipe passes the slop" a control though only the in-slot half is asserted — Ruling: distance is built as slop + 4 dp, so the claim holds by construction; reword later — cost if wrong: none at run time.
- Task 6: parked — report cites FW-2 control at line 52 (is :51) — Ruling: report typo, no code impact.
- Ruling: rail jump cost (~one dropped frame per jump, UI-thread composition of a fresh viewport; fling unaffected at 1.18%) accepted for Phase 5 — the user judged the drag "okay" on the device, and it is not a regression of an existing interaction — Phase 7 carry-over: Perfetto trace of a rail jump + regenerate the baseline profile with a rail journey (spec §15) — cost if wrong: visible stutter on fast rail drags until Phase 7.
- Ruling: keep this workspace after acceptance (not deleted) — the roadmap points at this ledger for the Phase 7 carry-overs, as with the 4a/4b ledgers — cost if wrong: a few MB of git-ignored scratch.
