---
type: decision
adr: "ADR-027"
status: active
date: 2026-11-13
source: "user, 2026-11-13: restructure the docs vault by a numbered folder template, move .obsidian to the repo root; picked option B (move existing folders) over hubs-only and full renaming"
tags: [vault, docs, workflow]
related: ["[[Decisions]]", "[[Vault conventions]]", "[[Document the project top-down]]", "[[Record knowledge in the vault]]", "[[LLM wiki beside the vault]]"]
---

# Numbered vault structure

**Context:** the vault (created 2026-11-08) was sorted by note type (`rules/`, `decisions/`, `knowledge/`, …) with
no overview, architecture, data or operations notes, and Obsidian opened `docs/` alone. The user asked for a
numbered, top-down documentation structure that later development can build on.

**Decision:**
- `docs/` follows the numbered layout: `00 Home.md` (links only), `01 Overview`, `02 Architecture` (+ `Decisions/`),
  `03 Modules` (+ `Features/`, `Agents/`), `04 Interfaces`, `05 Data` (+ `Entities/`), `06 Operations`
  (+ `Knowledge/`, `Devices/`), `07 Development` (+ `Rules/`, `Guides/`), `08 Project` (+ `History/`), `99 Meta`
  (`Templates/`, `Bases/`, `Attachments/`). Details: [[Vault conventions]].
- The existing type folders moved under the numbered parents with their file names kept, so every `[[link]]` still
  resolves. Decisions gained an `adr:` number, modules a `code:` path.
- `04 API` became **04 Interfaces**: the app is offline and has no HTTP API; its contracts are routes, widget actions
  and file formats.
- `.obsidian/` moved to the repository root: the whole repo is the vault (code excluded), so `CLAUDE.md` and
  `README.md` are linkable. The Karpathy wiki plugin points at `docs/wiki`.
- `design/`, `superpowers/` and `wiki/` keep their paths: the superpowers skills, the calibration scripts, the code's
  KDoc and the wiki plugin write or cite them.
- Every live path reference moved with the files: `CLAUDE.md`, the PostCompact hook, `.claude` docs, rule notes and
  three KDoc comments. Archived records (ledgers, compaction summaries, v1 phase docs, specs, plans, wiki pages) keep
  the paths that were true when they were written.

**Why:** one structure, one place per note, overview → detail reading order; the hub notes link the type folders
instead of repeating them.

**Rules out:**
- Hubs only, nothing moved: two parallel structures.
- Renaming to the template's names (`ADR-001 …`, one Troubleshooting note instead of the knowledge notes): breaks
  one-note-per-concept and the existing links.
