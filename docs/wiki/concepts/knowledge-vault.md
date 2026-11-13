---
type: concept
summary: "The authored Obsidian vault in docs/, in a numbered layout (00 Home … 99 Meta): rules, decisions, modules, interfaces, data, operations, history and backlog."
aliases: ["docs vault", "Project memory", "Knowledge vault"]
tags: [process, memory]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-13
---
# Knowledge vault (docs/)

The authored Obsidian vault in docs/, in a numbered layout (00 Home … 99 Meta): rules, decisions, modules, interfaces, data, operations, history and backlog.

`docs/` is the authored project memory ([[claude-md]]). Agents read it before every task ("vault first": grep it,
read the matching notes, obey every rule note) and update every note a change touches before reporting work done.

**Layout (since 2026-11-13):**
- `00 Home.md`: the entry point, links only.
- `01 Overview/`: project overview, tech stack, glossary.
- `02 Architecture/`: architecture overview, data flow, and `Decisions/` (numbered ADRs).
- `03 Modules/`: one note per Gradle module, plus `Features/` and `Agents/`.
- `04 Interfaces/`: the app's contracts (routes, widget actions, file formats). There is no HTTP API
  ([[offline-privacy-architecture]]).
- `05 Data/`: database schema and `Entities/`.
- `06 Operations/`: local setup, configuration, deployment, troubleshooting, plus `Knowledge/` and `Devices/`.
- `07 Development/`: conventions, testing, plus `Rules/` and `Guides/`.
- `08 Project/`: changelog, roadmap, known issues, `Backlog.md` and `History/`.
- `99 Meta/`: vault conventions, templates, Bases tables, attachments.
- `design/`, `superpowers/` (specs and plans) and `wiki/` (this wiki, [[llm-wiki]]) keep their paths.

Obsidian opens the **repository root** as the vault (`.obsidian/` at the root, code folders excluded), so `CLAUDE.md`
and `README.md` are linkable notes too. Folder names contain spaces, so shell commands quote the paths.

**Where a change is recorded:** a user preference → `07 Development/Rules/`; a product or design choice →
`02 Architecture/Decisions/`; a lesson → `06 Operations/Knowledge/` plus a Troubleshooting line when it has a
symptom; a finished phase → `08 Project/History/`, the Timeline and the Changelog; opened or closed work → the
Backlog, plus Roadmap and Known Issues when it changes them. Compaction summaries are saved to
`docs/08 Project/History/sessions/` by a hook and then distilled into the entity notes.

**Point, don't copy:** `.claude/rules/`, `.claude/agents/` and `.claude/skills/` stay where the harness loads them;
vault notes link to them.

**Relation to this wiki:** the wiki is derived by the LLM from raw sources (including `CLAUDE.md` and the
[[ownership-map]]); the vault is written as work happens. Code and `CLAUDE.md` win over the wiki ([[llm-wiki]]).

## Related
[[claude-md]], [[llm-wiki]], [[ownership-map]], [[development-history]]
