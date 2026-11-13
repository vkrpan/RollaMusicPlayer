---
type: index
tags: [meta]
related: ["[[00 Home]]", "[[Record knowledge in the vault]]", "[[Document the project top-down]]", "[[Numbered vault structure]]"]
---

# Vault conventions

## Where the vault is
Obsidian opens the **repository root** (`.obsidian/` there); the notes live in `docs/`. Code folders, build output and
local reference files are excluded from search and graph (`.obsidian/app.json`). `CLAUDE.md` and `README.md` are in the
vault, so notes can link [[CLAUDE]] and [[README]]. Why: [[Numbered vault structure]].

## Folders and note types

| Folder | `type:` | One note is… |
|---|---|---|
| `00 Home.md` | `index` | the entry point: links only |
| `01 Overview/` | `hub` | Project Overview, Tech Stack, Glossary |
| `02 Architecture/` | `hub` | Architecture Overview, Data Flow |
| `02 Architecture/Decisions/` | `decision` | one product or architecture decision (an ADR, numbered by `adr:`), with its reason and what it rules out |
| `03 Modules/` | `module` | one Gradle module: purpose, owner agent, key types, `code:` path |
| `03 Modules/Features/` | `feature` | one user-facing feature: what it does, its modules, specs, plans, decisions |
| `03 Modules/Agents/` | `agent` | one project subagent: role, ownership, hard rules, source file |
| `04 Interfaces/` | `hub`, `interface` | one contract other software relies on (routes, widget actions, file formats). The app has no HTTP API |
| `05 Data/` | `hub` | Database Schema |
| `05 Data/Entities/` | `entity` | one model or table: fields, invariants, who reads and writes it |
| `06 Operations/` | `hub` | Local Setup, Configuration, Deployment, Troubleshooting |
| `06 Operations/Knowledge/` | `knowledge` | one trap, fact or technique learned the hard way |
| `06 Operations/Devices/` | `device` | one test device and how to use it safely |
| `07 Development/` | `hub` | Conventions, Testing |
| `07 Development/Rules/` | `rule` | a standing instruction from the user that every session obeys |
| `07 Development/Guides/` | `guide` | a coding or design guide: the must-obeys and a pointer to the source of truth |
| `08 Project/` | `hub`, `index` | Changelog, Roadmap, Known Issues, Backlog (the working list of open work) |
| `08 Project/History/` | `history`, `ledger`, `session` | releases, phases, the Timeline, archived execution rulings, compaction summaries |
| `99 Meta/` | — | these conventions, `Templates/`, `Bases/`, `Attachments/` (pasted screenshots) |
| `design/`, `superpowers/`, `wiki/` | — | measurements and calibration captures; specs and plans; the generated LLM wiki. Paths kept because tools write there |
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

Extra properties by type: modules carry `gradle:`, `code:` (repo path) and `owners:`; decisions carry `adr:`
(`ADR-001` …, in date order; a new decision takes the next number); interfaces carry `kind:` and `code:`; entities carry
`table:`, `model:` and `code:`. The `99 Meta/Bases/` tables read these properties.

Body: one `#` title equal to the file name, then the content. Decisions use **Decision / Why / Rules out** (optionally
**Context** first). Rules use **Rule / Why / How to apply**. Knowledge uses **Symptom / Cause / Fix**, and every
knowledge note with a symptom gets a line in [[Troubleshooting]]. New notes start from `99 Meta/Templates/` (the
Templates core plugin points there).

## Linking

- Link every note to its folder index and to every entity it mentions: features ↔ modules ↔ agents ↔ decisions ↔ rules
  ↔ interfaces ↔ entities.
- Link by file name (`[[Song in code, Tracks on screen]]`). File names are unique across the vault, `docs/wiki/`
  included.
- Every Markdown file Claude writes links other Markdown notes with `[[wikilinks]]`, specs and plans included
  (`[[2026-11-09-custom-tags-design]]`), so Obsidian's graph and backlinks connect them
  ([[Link Markdown notes with wikilinks]]). Source code is cited by repo path in backticks, not copied.
- `.claude/rules/*`, `.claude/agents/*` and `.claude/skills/*` stay where the harness loads them (dot-folders are
  outside Obsidian's index). Their notes here link to them and carry only the must-obeys; the files are the source of
  truth.

## Querying (for Claude)

Folder names contain spaces: quote paths.
- Everything about a subject: `grep -rli "<term>" docs --include=*.md`
- All notes of a type: `grep -rl "^type: rule" docs`
- Active decisions about one area: `grep -rl "^type: decision" docs | xargs grep -l "tags:.*playback"`
- Superseded notes: `grep -rl "^status: superseded" docs`
- The next ADR number: `grep -rh '^adr:' "docs/02 Architecture/Decisions" | sort | tail -1`
- In Obsidian: the `99 Meta/Bases/` tables (embedded on [[00 Home]]), the graph view, backlinks and tags.

## Writing (for Claude)

Follow [[Record knowledge in the vault]] and [[Document the project top-down]]: write the note in the same turn the
decision or lesson happens, update an existing note rather than adding a near-duplicate, mark replaced notes
`status: superseded` with `superseded_by`, and add the note to its folder index and, where it has one, its hub
(Troubleshooting, Known Issues, Roadmap, Changelog).
