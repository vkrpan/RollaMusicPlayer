---
type: decision
adr: "ADR-026"
status: active
date: 2026-11-09
source: "user, 2026-11-09: \"scaffold it now: the CLAUDE.md section, the three /wiki-* commands, wiki/index.md and log.md, the .gitignore lines, and a first ingest of CLAUDE.md and OWNERSHIP.md\""
tags: [wiki, process, memory]
related: ["[[Decisions]]", "[[Wiki pages are generated, never hand-edited]]", "[[Record knowledge in the vault]]"]
---

# LLM wiki beside the vault

**Decision:** the project gets a Karpathy-style LLM wiki in `docs/wiki/`, inside this vault (the plugin's `wikiFolder` is `wiki`
relative to the vault root).
Raw sources (`CLAUDE.md`, the [[Ownership map]], the v1 phase docs, specs, plans, `.claude/rules/`) are only read; the
wiki is generated from them by the Obsidian "Karpathy LLM Wiki" plugin (v1.30.0, Apache-2.0) and the `/wiki-*`
commands; the schema is the "Knowledge wiki" section of `CLAUDE.md`, not `CLAUDE.md` as a whole.

**Layout:** `docs/wiki/sources/`, `entities/`, `concepts/`, `answers/`, `index.md`, `log.md`. Wiki file names must be
unique across the vault, so module pages are `module-<name>` (the vault's module notes own the bare names).
Frontmatter: `type`, `summary`, `tags`, `aliases`, `sources`, `updated`, optional `reviewed`.

**Why:** the wiki gives agents and the user a pre-digested, linked overview instead of re-deriving the project from
raw files on every question (the gist's argument against plain RAG). The vault stays the authored record of rules,
decisions and history; the wiki is derived and may lag.

**Plugin notes:**
- It needs its own provider API key; it cannot use the Claude Code subscription, and Anthropic API use is billed
  separately. A local model (Ollama, LM Studio) is possible for these small docs.
- Keep "Auto-Maintenance" off and use a Coarse or Minimal extraction level to control cost.
- No backend or telemetry; the optional MinerU PDF backend uploads to mineru.net and is not needed here.
- Plugin settings (`.obsidian/plugins/*/data.json`) can hold the key and are git-ignored.

**First ingest (2026-11-09):** `CLAUDE.md` and the ownership map; 37 pages. It found the ownership map's `:app` DI
rows stale and a "Material 3 throughout" line in `CLAUDE.md` that the kit-only rule overrides.

**Schema:** the user's own "Knowledge wiki (Karpathy LLM-wiki pattern)" section of `CLAUDE.md` (page format, rules,
raw-source list), corrected on 2026-11-09 to the plugin's real location (`docs/wiki/`), the moved raw sources and the
`module-` names, and extended with the never-hand-edit rule, the log format and the keep-current cadence.

**Plugin settings seen 2026-11-09:** provider Anthropic, model `claude-opus-5-5`, extraction granularity `standard`
(the guidance above suggests Coarse or Minimal to save cost), auto smart-fix off, auto-watch off, periodic lint off.
