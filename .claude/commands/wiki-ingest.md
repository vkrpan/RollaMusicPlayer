---
description: Ingest one raw source into the LLM wiki (docs/wiki/)
argument-hint: <path to raw source, e.g. docs/history/v1-phases/PHASE7-WIDGET.md>
---

Ingest `$ARGUMENTS` into the project wiki, following CLAUDE.md → "Knowledge wiki".

1. Read `$ARGUMENTS` fully. If it isn't one of the raw sources listed in the schema, stop and ask.
2. Read `docs/wiki/index.md` and grep `docs/wiki/` for the main names the source mentions, so you extend
   existing pages instead of creating duplicates.
3. Before writing anything, give me 3–7 bullet takeaways and the list of pages you plan to
   create/update (entities vs concepts). Wait for my OK or corrections.
4. Write `docs/wiki/sources/<slug>.md` (type: source): what the doc covers, key decisions, and a
   `## Mentions` list linking every entity/concept it touches. Frontmatter on every page: `type`, `summary` (one
   line), `tags`, `aliases`, `sources`, `updated` (the plugin's `tags`/`type`/`aliases` plus ours).
5. Create/update each entity and concept page (`docs/wiki/entities/` for modules, agents and components;
   `docs/wiki/concepts/` for ideas and rules). Never touch a page with `reviewed: true`; note it in the report instead. Rewrite pages so they stay coherent, link
   generously with [[slug]], add the source to each page's `sources:` frontmatter, and set
   `updated:` to today. Record conflicts under `## Disagreements & open questions`, and check the
   code when it can settle them.
6. Update `docs/wiki/index.md` (one line per new page, in the right section, alphabetical).
7. Append to `docs/wiki/log.md`:
   `## [YYYY-MM-DD] ingest | <source path>` followed by bullets of pages created/updated.
8. Report: pages created, pages updated, contradictions found. Don't commit. Use the canonical terms from
   `.claude/rules/model-vocabulary.md` (`Song`, never `Track`) throughout.
