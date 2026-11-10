---
type: concept
summary: "The authored Obsidian vault in docs/: rules, decisions, knowledge, features, history and backlog."
aliases: ["docs vault", "Project memory"]
tags: [process, memory]
sources: ["[[sources/claude-md]]"]
updated: 2026-11-09
---
# Knowledge vault (docs/)

The authored Obsidian vault in docs/: rules, decisions, knowledge, features, history and backlog.

`docs/` is the authored project memory ([[claude-md]]): the user's rules, decisions, lessons, feature, module
and agent notes, history and backlog. Agents read it before every task and update it with every change; compaction
summaries are saved to `docs/history/sessions/` and distilled.

**Relation to this wiki:** the wiki is derived by the LLM from raw sources (including `CLAUDE.md` and the ownership
map); the vault is written as work happens. Code and `CLAUDE.md` win over the wiki ([[llm-wiki]]).

## Related
[[claude-md]], [[llm-wiki]]
