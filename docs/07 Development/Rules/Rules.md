---
type: index
tags: [index, rules]
related: ["[[00 Home]]"]
---

# Rules

Standing instructions from the user. Every session obeys them; a conflict with a skill or a default goes to the rule.

## Working with the user
- [[Commits are user-triggered]] — report "ready to commit"; commit only when asked.
- [[Record knowledge in the vault]] — the vault is the first source of information; every code or doc change is
  recorded in every vault note it touches, in the same turn.
- [[Document the project top-down]] — overview first, one note per concept, frontmatter for queries, ADRs after the
  fact, link the code instead of copying it.
- [[Wiki pages are generated, never hand-edited]] — `wiki/` is LLM-written; only `reviewed: true` by hand; code and
  CLAUDE.md win.
- [[Save compaction summaries to the vault]] — after every compaction, distil the saved summary into the vault before
  continuing (hooks save it automatically).
- [[Subagents run on Opus]] — every dispatch passes `model: opus`; the orchestrator verifies results.
- [[Skill observation protocol]] — the task-observer protocol at session start and after each task.

## Product
- [[Offline and privacy first]] — no network, no analytics, storage permission only.
- [[No decorative toggles]] — every control must change real behaviour.
- [[Stock Android is the target platform]] — platform-UI features are judged on stock Android.

## Code and UI
- [[Song in code, Tracks on screen]] — the model is `Song`; UI copy says "Tracks".
- [[UI copy and spelling]] — "Rolla Music", "Equaliser", British spelling.
- [[Build UI from tokens and the kit]] — no hardcoded colour, type, dp or Material icons.
- [[Module dependency direction]] — feature → core → model; features never depend on each other.
- [[Spec wins over plan]] — specs bind plans, plans bind CLAUDE.md claims.
- [[Keep docs honest]] — sweep claims at every merge; grep before saying "gone everywhere".
- [[Link Markdown notes with wikilinks]] — every `.md` Claude writes links other notes with `[[wikilinks]]`.
- [[Judge motion on a release-like build]] — animation verdicts come from `assembleBenchmark`.

## Tools and devices
- [[Edit files with Write and Edit, not the shell]] — no `sed -i`; no source through heredocs.
- [[Device safety]] — what may and may not be done to the user's phones.
- [[Device tests run on the Motorola]] — serial `ZY22MBNVB4`; installing there is allowed.
- [[Commit staging excludes local artefacts]] — the exact staging command.
