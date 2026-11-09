---
type: ledger
status: history
date: 2026-10-21
plan: "docs/superpowers/plans/2026-10-21-oneui-phase6b-search-editors.md"
rulings: 6
deferred_minors: 0
tags: [ledger, rulings]
related: ["[[One UI redesign]]", "[[Timeline]]"]
---

# Ledger rulings — One UI Phase 6b — Search, tag editors

Every `Ruling:` and `minor (deferred)` line from the execution ledger
`.superpowers/sdd/2026-10-21-oneui-phase6b-search-editors/progress.md` (git-ignored scratch, archived here verbatim on 2026-11-08 so it survives the
workspace's deletion). Plan: `docs/superpowers/plans/2026-10-21-oneui-phase6b-search-editors.md`. Each ruling reads
"what was decided — why — what it costs if wrong".

- Ruling P1: option (a) — drop the test's two Expand lines and update its comment; the test still pins statusBarsPadding (its mutation stays RED, "expected:<80.0> but was:<0.0>") — the "unedited" clause guarded the inset, not the step that the user's full-height decision (2026-10-21) removed — cost if wrong: the test no longer exercises the half→full transition, which the queue no longer has.
- Task 2: ⚠️ prompt layout (290 dp, non-scrolling, ignores pill/nav insets) in landscape / fontScale 1.5 — Ruling P2: cannot be device-checked without revoking the permission (user-owned setting), so the final fix wave makes the prompt content verticalScroll and pads it by homeListBottomPadding() — cheap insurance — cost if wrong: a few lines.
- Ruling P3: accept the comment/message rewording in DetailRoutesInsetTest (assertions and logic unchanged) and the four feature-screen KDoc lines — the "unedited" clause guards behaviour; KDocs must not name a deleted symbol — cost if wrong: two comment hunks.
- Ruling P4: SongListItem's selection Checkbox stays — the plan's Global Constraint listed Checkbox to stop the batch editor's apply checkboxes (spec §8.7: OneUiSwitch), but the row's selection checkbox is the Phase 2 selection UI the spec keeps ("the selected-state highlight … keep working"), in a file 6b does not touch; the docs state the rule as "apply toggles use OneUiSwitch" — cost if wrong: a kit OneUiCheckbox wrapper in Phase 7.
- Ruling P5: ONE final fix wave = I1, I2, perf blockers 1-2, perf minors 3-6, and the triaged fix-now minors (T1 M2, T1 M4, T2 M1, T2 M2, T3 M1/M2, T4 M1/M2/M5/M6, T5 M2, T6 M1) in final-fix-wave-brief.md; the docs items (spec §8.7 line 577, §7.3, CLAUDE.md lines 323/360, ui-style-guide barrier wording) are the controller's — cost if wrong: one larger fix wave.
- Ruling P6: Phase 7 list = final review Minor 1 (search field label), Minor 2 (refocus on re-entry), Minor 7 (VM exception containment), perf minor 7, and the ledger's Phase 7 minors; final review Minor 3 (options sheet under the keyboard) goes to the Task 8 device check.
