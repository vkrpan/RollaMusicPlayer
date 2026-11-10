---
description: Health-check the LLM wiki and propose fixes
---

Lint the project wiki per CLAUDE.md → "Knowledge wiki". Report first, fix only after my OK.

Check:
1. Broken [[links]] (target page doesn't exist).
2. Orphans: pages with no inbound links (index.md doesn't count).
3. Pages missing from `docs/wiki/index.md`, or index lines pointing at missing pages.
4. Frontmatter problems: missing type/summary/sources/updated, unknown type.
5. Likely duplicates: pages covering the same thing under different names/aliases.
6. Contradictions between pages, and claims the code now contradicts (spot-check entity pages
   against the files they describe).
7. Stale pages: their raw sources changed since `updated:` (use `git log -1 --format=%cs -- <path>`).
8. Concepts mentioned on several pages that have no page of their own.
9. Raw sources never ingested (compare the schema's source list with `docs/wiki/sources/`).

Output a numbered list grouped by check, each with the proposed fix. After I approve items,
apply them (skip `reviewed: true` pages; code and CLAUDE.md win over the wiki), then append
`## [YYYY-MM-DD] lint | <n> issues, <m> fixed` with bullets to `docs/wiki/log.md`.
