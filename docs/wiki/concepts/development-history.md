---
type: concept
summary: "v1.0 built in Phases 0–8 (Jun–Jul 2026); One UI redesign Phases 1–7 (Oct–Nov 2026); per-track artwork; custom tags in four phases (Nov 2026), all on feat/oneui-redesign."
aliases: ["Timeline"]
tags: [history]
sources: ["[[sources/claude-md]]", "[[sources/custom-tags-roadmap]]"]
updated: 2026-11-13
---
# Development history

v1.0 built in Phases 0–8 (Jun–Jul 2026); One UI redesign Phases 1–7 (Oct–Nov 2026); per-track artwork; custom tags in four phases (Nov 2026), all on feat/oneui-redesign.

- **v1.0.0** shipped on `main` 2026-07-23 (Phases 0–8, 2026-06-29 to 2026-07-23; prompt docs in
  `docs/08 Project/History/v1-phases/`).
- **One UI redesign** Phases 1–7, 2026-10-06 to 2026-11-04 ([[one-ui-design-system]]).
- **Per-track artwork** committed `e129f3e` and pushed 2026-11-09, device-verified ([[per-track-artwork]]).
- **Custom tags** (cut from v1.0, asked for again 2026-11-09), built in four phases ([[custom-tags]],
  [[custom-tags-roadmap]]): foundation `dce76dd` (2026-11-10), assigning `eb9ebcb` (2026-11-11), managing `7f26949`
  and finding `1cb705b` (2026-11-12), each device-walked; all four pushed 2026-11-12.
- Everything after v1.0 sits on `feat/oneui-redesign`, waiting for the user's merge into `main`.
- Full dated history: `docs/08 Project/History/Timeline.md`; changes per version: `docs/08 Project/Changelog.md`
  (both in the authored [[knowledge-vault]]).

## Related
[[claude-md]], [[one-ui-design-system]], [[per-track-artwork]], [[custom-tags]], [[custom-tags-roadmap]],
[[knowledge-vault]]

## Disagreements & open questions
- [[claude-md]]'s Current Status still calls `15821b4` the redesign's "last commit"; the branch has since moved on
  (`e129f3e` per-track artwork, then the custom-tags commits up to `1cb705b`). True for the redesign itself, but easy
  to misread.
