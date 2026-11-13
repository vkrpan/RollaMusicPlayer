---
type: rule
status: active
date: 2026-11-13
source: "user, 2026-11-13: \"RULES for documenting an existing project\" (with the numbered folder template)"
tags: [vault, docs, workflow]
related: ["[[Rules]]", "[[Vault conventions]]", "[[Numbered vault structure]]", "[[Record knowledge in the vault]]", "[[Keep docs honest]]"]
---

# Document the project top-down

**Rule:**
1. **Top-down.** Overview, Architecture and Local Setup first (most value for the least effort), then one module at a
   time.
2. **One note per concept, linked often.** Use `[[wikilinks]]` instead of repeating content; the graph and backlinks
   only help once notes are densely linked.
3. **Frontmatter for queries.** `tags`, `status`, `code:` (repo path) and the type's properties, so Bases tables
   (the vault's Dataview equivalent) can list notes.
4. **Write ADRs after the fact.** For past decisions, write down why things are the way they are; that knowledge
   disappears first.
5. **The vault lives in the repo.** Docs are versioned with the code; `.obsidian/` is committed except its
   per-machine `workspace*.json` and plugin `data.json`.
6. **Don't duplicate the code.** Document the why, the boundaries and the gotchas; link file paths for the how.

**Why:** the user's standing instructions for documenting this existing project (2026-11-13).

**How to apply:** place notes per [[Vault conventions]]; start new notes from `99 Meta/Templates/`; when a decision
has no note yet, add one with the next `adr:` number; cite code as `path` in backticks and never paste its body.
