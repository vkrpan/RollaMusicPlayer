---
type: index
tags: [index, home]
---

# Rolla Music — knowledge vault

This vault is the project's memory: what was decided, what must always be obeyed, what was learned the hard way, and
what happened when. Claude reads it before working and writes to it as work happens; the user browses it in Obsidian.
How notes are shaped and how to query them: [[Vault conventions]].

## Start here

| Need | Go to |
|---|---|
| Rules the user set that every session must obey | [[Rules]] |
| Why the app is the way it is | [[Decisions]] |
| Traps in the build, tests, devices and platform | [[Knowledge]] |
| What each feature is and where its code lives | [[Features]] |
| The Gradle modules and who owns them | [[Modules]], [[Agents]], [[Ownership map]] |
| Coding and design guides (and where their source lives) | [[Guides]] |
| What happened when | [[Timeline]] |
| What is open | [[Backlog]] |
| Rulings made while building each phase | [[Ledgers]] |
| Compaction summaries saved after each context compaction | [[Sessions]] |
| The LLM-generated wiki (`docs/wiki/`, derived, never hand-edited; start at `wiki/index.md`) | [[LLM wiki beside the vault]] |

## Current state (2026-11-09)

- v1.0.0 shipped on `main` (2026-07-23): [[v1.0 release]].
- The One UI redesign (Phases 1–7) is complete on `feat/oneui-redesign`, pushed, waiting for the user's merge:
  [[One UI redesign]].
- [[Per-track artwork]] — committed (`e129f3e`) and pushed on `feat/oneui-redesign` (2026-11-09).

## Other folders

- `superpowers/specs/` and `superpowers/plans/` — design specs and implementation plans (binding; a spec wins over its
  plan). Listed per feature in [[Features]].
- `design/` — One UI measurements (`design/oneui-measurements.md`) and calibration screenshots.
- `history/v1-phases/` — the v1.0 phase prompt docs: [[v1 phase docs]].
- `daily/` — daily notes (template `templates/daily-note-template.md`).
- `bases/` — Obsidian Bases tables over the notes' properties.
