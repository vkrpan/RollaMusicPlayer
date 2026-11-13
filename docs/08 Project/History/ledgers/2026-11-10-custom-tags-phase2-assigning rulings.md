---
type: ledger
status: history
date: 2026-11-10
plan: "docs/superpowers/plans/2026-11-10-custom-tags-phase2-assigning.md"
rulings: 12
deferred_minors: 7
tags: [ledger, rulings, custom-tags]
related: ["[[Custom tags]]", "[[Timeline]]", "[[Ledgers]]", "[[2026-11-10-custom-tags-phase2-assigning]]"]
---

# Ledger rulings — Custom tags phase 2 (assigning)

Every `Ruling:`, `minor (deferred)` and `parked` line from the execution ledger
`.superpowers/sdd/2026-11-10-custom-tags-phase2-assigning/progress.md` (git-ignored scratch, archived here verbatim on
2026-11-11 so it survives the workspace's deletion). Plan: [[2026-11-10-custom-tags-phase2-assigning]]; spec:
[[2026-11-09-custom-tags-design]]. Final review: ready with fixes; one fix wave, re-reviewed clean. Device: Room
121/121 on the Motorola; the user's walk passed every step (2026-11-11).

## Pre-flight

- Ruling 1: Task 3 also produces `@Immutable data class CustomTagNameDialogState(val kind: CustomTagKind, val
  initialName: String, val error: String? = null)` in `com.rolla.musicplayer.core.ui.customtags`, and
  `CustomTagNameDialog` may take it instead of the three loose parameters. — the T4/T5 UiState names it but no task
  defines it. — Cost if wrong: one small class in a different file.

## During execution

- Ruling 2: keep "bump on changed file or forced re-read" — a row already unread with an unchanged file has no newer
  content, so its in-flight read is current. — Cost if wrong: one line in toEntity.
- Task 1: minor (deferred): M1 a deleted row whose id returns restarts at generation 0 (theoretical); M2 Ruling 2 not
  pinned by a test (unread + unchanged + no reread keeps gen); M3 @Transaction atomicity of applyFileTags/
  markFileTagsRead now unpinned (comment it as load-bearing); M4 no pass-level test of a skipped markFileTagsRead;
  M5 KDoc slips (FileTagPassResult "later read"; CustomTagDao header still says v6).
- Task 2: minor (deferred): M1 NFD folding ≠ collator (Ø/Æ/Ł/Đ/ß, compatibility forms; noted in core-ui note); M2
  SUBTAG branch of `toggled` untested; M3 names re-folded per keystroke (precompute if lists grow).
- Ruling 3: I3 is Important — the plan's per-screen table is part of the plan's requirements ("disabled rows
  announce the note"). — Cost if wrong: one semantics line per row.
- Ruling 4: I2 fix = chip text wraps (no ellipsis): pill shape on one line, the kit's rounded-rectangle shape when it
  wraps; the test asserts no visual overflow per chip with a 40-character name inside a card inset by
  `settingsCardInset`. — Review Focus 3 is explicit. — Cost if wrong: taller chips for very long names.
- Ruling 5: ⚠️ "Enter a name" unreachable from the dialog (Create disabled while blank) — accepted: disabling is
  better feedback; the Blank mapping stays for any other caller. Pre-filled dialog not auto-focusing — accepted: kit
  ruling P37. — Cost if wrong: a copy string unused in the UI.
- Ruling 6: fold cheap Minors into fix round 1: M2 (real desync bug) with an M1 sync test, M4 kit test, M5 braces.
  M3 stays deferred. — Cost if wrong: a slightly larger fix diff.
- Task 3: minor (deferred): M3 Done padding borrows settingsSectionGap.
- Task 3: minor (deferred): brace-less `if` at CustomTagPickerSheet.kt:148; LabelChip not in KitPreviews; the
  pill→rounded-rect shape switch untested.
- Ruling 7: I1 — skip the ROLLA_TAGS write when the ticks equal the loaded known IDs (spec anti-goal "losing an
  existing tag of any kind on save"; matches Task 5's skip). — Cost if wrong: none observable; an unchanged field.
- Ruling 8: M1 — Review Focus 5 governs: a ROLLA_TAGS read failure at load disables the rows (note shown) and Save
  still writes the other fields; pinned by a test. M2 — `.catch` on the lists flow so a custom-tag table error
  degrades to empty lists instead of crashing the editor. M4 — assert the exact "Use 40 characters or fewer" and
  "Enter a name" mappings. M5 — one end-to-end test with the real TagWriter (title edit keeps an unknown ID). Folded
  into fix round 1. M3 noted. — Cost if wrong: a slightly larger fix round.
- Task 4: minor (deferred): M3 create's non-duplicate DB exceptions reach viewModelScope (consistent with other repo calls).
- Task 4: minor (deferred): an MP3/FLAC with an unreadable field shows the "MP3 and FLAC only" note (wrong reason —
  copy decision for the walk/final review); lists never recover after a failure for the screen's life; M3 now
  reachable from a usable screen; create can tick while a save runs (canEditCustomTags checked only at tap); create on
  an M4A has no own test.
- Ruling 9: the ~100 duplicated lines (picker observer, 8 handlers, create guard/mapping, copy constants, plusId)
  between the two editor VMs go to the final fix wave as one `internal` shared helper in `feature/tageditor/`, with
  the batch copy mappings tested through it. — a maintenance risk the final review will also see; one place for the
  verbatim copy. — Cost if wrong: one extra file.
- Ruling 10: undecodable ROLLA_TAGS overwritten by an edit that changes ticks / a batch add → backlog (needs phase 1's
  read to distinguish "absent" from "undecodable"; both editors). — the value holds no ID the app can identify; the
  user explicitly asked to add/change tags on that file. — Cost if wrong: another program's malformed value in our
  field is replaced.
- Task 5: minor (deferred): discardCopy refactor partial (onConsentDenied/onRecoveryGranted inline); rows look active
  during a save (taps ignored); PREVIEW_TALL_HEIGHT declared at file bottom.
- Final review: ready with fixes. Important: the Ruling 9 duplication. Minor (fix now): @Transaction load-bearing KDoc,
  KDoc slips (CustomTagDao header, FileTagPassResult, canWriteCustomTags "decision pending"), brace-less if. Can wait:
  create DB exceptions; picker rows ellipsize long names at 1.5× (walk); a new tag can take an ID an unread file
  carries during the first pass (reserveIds, phase 3); single editor reads ROLLA_TAGS for OGG/WAV. Vault items 11–16.
  Phase 3/4 hand-off: Manage-tags link param; stale editor copy after Manage (diff-based write
  `(inFile − (loaded − selected)) ∪ selected`, refresh copy, prune ticks); reserveIds with import; phase 4 chip variant.
- Ruling 11: fix wave = F1 shared helper (Ruling 9), F2 comments/style, F3 skip the ROLLA_TAGS read for formats the scan
  ignores; create's other exceptions stay deferred (no copy ruled). — Cost if wrong: one unhandled rare crash path.
- Ruling 12: accept "recompute only when ticks change" — "always" would change behaviour during load and after a
  declined save; the brief's aim was behaviour preservation. — Cost if wrong: none if the re-review confirms parity.
- Final fix wave re-review: all addressed (F1 parity confirmed incl. Ruling 12; F2 ×4; F3); no existing editor test
  changed. Out of scope: in phase 3 a `Saved` ID already ticked wouldn't recompute (unreachable now); stale comment
  naming canEditCustomTags in TagEditorViewModelTest:1022; vault gaps (controller wrote them).
