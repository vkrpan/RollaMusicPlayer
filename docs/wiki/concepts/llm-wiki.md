---
type: concept
summary: "This wiki: an LLM-maintained layer of linked pages over the project's raw sources."
aliases: ["Knowledge wiki", "Karpathy LLM wiki"]
tags: [process, wiki]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-13
---
# LLM wiki

This wiki: an LLM-maintained layer of linked pages over the project's raw sources.

Karpathy's pattern ("Obsidian is the IDE; the LLM is the programmer; the wiki is the codebase"): **raw sources** are
only read, the **wiki** is written by the LLM, and the **schema** (the "Knowledge wiki" section of
[[claude-md]]) sets the rules. Three operations: ingest, query (good answers are filed back to `answers/`) and
lint.

- Lives in `docs/wiki/`: `sources/`, `entities/`, `concepts/`, `answers/`, `index.md`, `log.md`.
- Built by the Obsidian "Karpathy LLM Wiki" plugin (installed in the repository-root `.obsidian/`, with
  `wikiFolder: docs/wiki`) and the `/wiki-ingest`, `/wiki-query`, `/wiki-lint` commands.
- Page names are kebab-case and unique across the vault, because Obsidian resolves links by name; module pages are
  `module-<name>` so they don't clash with the vault's own module notes.
- Never hand-edited; `reviewed: true` protects a curated page.
- Code and `CLAUDE.md` win over the wiki; the authored record is the [[knowledge-vault]].

## Related
[[claude-md]], [[knowledge-vault]]

## Disagreements & open questions
- The schema in [[claude-md]] still scopes name uniqueness to "the whole `docs/` vault"; the vault is now the
  repository root, so the namespace includes `CLAUDE.md`, `README.md` and other repo Markdown outside the excluded
  folders. No clash today.
