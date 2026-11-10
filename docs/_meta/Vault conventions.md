---
type: index
tags: [meta]
related: ["[[Home]]", "[[Record knowledge in the vault]]"]
---

# Vault conventions

## Folders and note types

| Folder | `type:` | One note is… |
|---|---|---|
| `rules/` | `rule` | a standing instruction from the user that every session obeys |
| `decisions/` | `decision` | one product or architecture decision, with its reason and what it rules out |
| `knowledge/` | `knowledge` | one trap, fact or technique learned the hard way |
| `devices/` | `device` | one test device and how to use it safely |
| `features/` | `feature` | one user-facing feature: what it does, its modules, specs, plans, decisions |
| `modules/` | `module` | one Gradle module: purpose, owner agent, key types |
| `agents/` | `agent` | one project subagent: role, ownership, hard rules, source file |
| `guides/` | `guide` | a coding or design guide: the must-obeys and a pointer to the source of truth |
| `history/` | `history`, `ledger` | releases, phases, archived execution rulings |
| `backlog/` | `index` | open work |
| `*/<Index>.md` | `index` | the table of contents for its folder |

## Frontmatter

```yaml
---
type: rule            # see the table above
status: active        # active | superseded | history | done | open
date: 2026-11-08      # when it was decided, learned or last confirmed
source: "where it came from (user message, ledger, CLAUDE.md, a test)"
tags: [ui, copy]
related: ["[[Other note]]"]
superseded_by: "[[Newer note]]"   # only when status: superseded
---
```

Body: one `#` title equal to the file name, then the content. Decisions use **Decision / Why / Rules out / Links**.
Rules use **Rule / Why / How to apply**. Knowledge uses **Symptom / Cause / Fix**.

## Linking

- Link every note to its folder index and to every entity it mentions: features ↔ modules ↔ agents ↔ decisions ↔ rules.
- Link by file name (`[[Song in code, Tracks on screen]]`). File names are unique across the vault.
- Every Markdown file Claude writes links other Markdown notes with `[[wikilinks]]`, specs and plans included
  (`[[2026-11-09-custom-tags-design]]`), so Obsidian's graph and backlinks connect them
  ([[Link Markdown notes with wikilinks]]). Source code is cited by repo path in backticks, not copied.
- `.claude/rules/*`, `.claude/agents/*` and `.claude/skills/*` stay where the harness loads them. Their notes here link
  to them and carry only the must-obeys; the files are the source of truth.

## Querying (for Claude)

- Everything about a subject: `grep -rli "<term>" docs --include=*.md`
- All notes of a type: `grep -rl "^type: rule" docs`
- Active decisions about one area: `grep -rl "^type: decision" docs | xargs grep -l "tags:.*playback"`
- Superseded notes: `grep -rl "^status: superseded" docs`
- In Obsidian: the `bases/` tables, the graph view, backlinks and tags.

## Writing (for Claude)

Follow [[Record knowledge in the vault]]: write the note in the same turn the decision or lesson happens, update an
existing note rather than adding a near-duplicate, mark replaced notes `status: superseded` with `superseded_by`, and
add the note to its folder index.
