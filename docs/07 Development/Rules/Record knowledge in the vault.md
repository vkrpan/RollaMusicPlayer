---
type: rule
status: active
date: 2026-11-08
source: "user, 2026-11-08: \"From now on I want you to use Obsidian vault for manipulating with memory and knowledge in this project.\" and, the same day: \"From now on I want you to always first reference to the vault for informations and when you create, modify or alter any part of the code (or md files) I want you to record those changes to every entity in the vault that need it and is involved.\""
tags: [vault, memory, workflow]
related: ["[[Rules]]", "[[Vault conventions]]", "[[Keep docs honest]]"]
---

# Record knowledge in the vault

**Rule:** `docs/` is the project's memory and the **first** place to look for information about the project.
Decisions, the user's standing instructions, lessons, project history — and every change to the code or the docs — are
recorded here, in every note the change touches. Claude's private auto-memory only points at this vault.

**How to apply:**
- **Vault first.** Before answering a question about the project or starting a task, grep the vault for the area
  (`grep -rli "<term>" docs --include=*.md`), read the matching notes and obey every matching rule. Only then read
  code, git history or other docs; when they disagree with the vault, fix the vault ([[Keep docs honest]]).
- **Every change reaches every involved entity.** When code or an `.md` file is created, modified or removed, update
  in the same turn (or at the latest when the task's work is done, before reporting it) every vault note that
  describes what changed:
  - the [[Features]] note of each feature whose behaviour changed;
  - the [[Modules]] note of each module touched (purpose, key types);
  - the [[Agents]] note when an agent's ownership or hard rules change;
  - [[Decisions]] when a decision is made or superseded, [[Rules]] when a rule changes;
  - [[Knowledge]] when something is learned the hard way;
  - [[Backlog]] when work opens or closes, [[Timeline]] when a phase or release ends;
  - the guide note when a `.claude/rules` file changes.
  A change with no vault update is unfinished work.
- **Preferences, decisions, lessons.** A preference the user states ("from now on…", "always…", "never…") →
  `07 Development/Rules/`; a design or product choice → `02 Architecture/Decisions/` (next `adr:` number); a lesson →
  `06 Operations/Knowledge/` plus its line in [[Troubleshooting]]. Where each kind of note goes: [[Vault conventions]];
  how to write it: [[Document the project top-down]].
- **One meaning, one note.** Update the existing note instead of adding a near-duplicate; mark a replaced note
  `status: superseded` with `superseded_by`.
- **Link it.** Add the note to its folder index and link the features, modules, agents and decisions it touches
  ([[Vault conventions]]).
- **Point, don't copy.** `.claude/rules`, `.claude/agents` and `.claude/skills` stay the source of truth for what they
  hold; specs and plans stay in `docs/superpowers/`.
- **Subagents** that change code report which entities their change touches; the controller writes the vault updates.
