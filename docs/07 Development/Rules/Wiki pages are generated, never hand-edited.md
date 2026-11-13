---
type: rule
status: active
date: 2026-11-09
source: "user, 2026-11-09: the LLM wiki schema (\"Never hand-edit wiki pages, except to set reviewed: true on a curated one\"; ingest each phase doc; lint before each phase)"
tags: [wiki, process, memory]
related: ["[[Rules]]", "[[LLM wiki beside the vault]]", "[[Record knowledge in the vault]]"]
---

# Wiki pages are generated, never hand-edited

**Rule:** `docs/wiki/` (inside this vault) is written only by the LLM (the Obsidian "Karpathy LLM Wiki" plugin and the
`/wiki-ingest`, `/wiki-query`, `/wiki-lint` commands). Nobody edits a wiki page by hand, except to add
`reviewed: true` to a curated page, which protects it from regeneration.

**How to apply:**
- Agents read `docs/wiki/index.md` for project context before opening raw sources.
- Code and `CLAUDE.md` win when they disagree with the wiki; a contradiction is a lint finding.
- Use the canonical terms from `.claude/rules/model-vocabulary.md` (`Song`, never `Track`, in code terms).
- Log entries use `## [YYYY-MM-DD] ingest | Title` (or `query` / `lint`).
- After each phase or major commit, ingest that phase's doc; file good query answers back as pages.
- Run lint before starting each new phase and review its counts before applying fixes ("Smart Fix All" in the plugin).

The schema itself is the "Knowledge wiki" section of `CLAUDE.md`.
